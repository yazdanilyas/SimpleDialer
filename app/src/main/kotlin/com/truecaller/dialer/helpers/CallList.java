package com.truecaller.dialer.helpers;

import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;

import com.google.android.gms.common.internal.Preconditions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
//import com.google.common.base.Preconditions;
//import com.google.common.collect.Maps;


public class CallList {

    private static final int DISCONNECTED_CALL_SHORT_TIMEOUT_MS = 200;
    private static final int DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS = 2000;
    private static final int DISCONNECTED_CALL_LONG_TIMEOUT_MS = 5000;

    private static final int EVENT_DISCONNECTED_TIMEOUT = 1;

    private static CallList sInstance = new CallList();

    private final HashMap<String, CallHelper> mCallById = new HashMap<>();
    private final HashMap<android.telecom.Call, CallHelper> mCallByTelecommCall = new HashMap<>();
    private final HashMap<String, List<String>> mCallTextReponsesMap = new HashMap<>();//Maps.newHashMap();
    /**
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Listener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));
    private final HashMap<String, List<CallUpdateListener>> mCallUpdateListenerMap = new HashMap<>();//Maps.newHashMap();
    private final Set<CallHelper> mPendingDisconnectCalls = Collections.newSetFromMap(
            new ConcurrentHashMap<CallHelper, Boolean>(8, 0.9f, 1));
    /**
     * Handles the timeout for destroying disconnected calls.
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_DISCONNECTED_TIMEOUT:
//                    Log.d(this, "EVENT_DISCONNECTED_TIMEOUT ", msg.obj);
                    finishDisconnectedCall((CallHelper) msg.obj);
                    break;
                default:
//                    Log.wtf(this, "Message not expected: " + msg.what);
                    break;
            }
        }
    };

    /**
     * USED ONLY FOR TESTING
     * Testing-only constructor.  Instance should only be acquired through getInstance().
     */
    CallList() {
    }

    /**
     * Static singleton accessor method.
     */
    public static CallList getInstance() {
        return sInstance;
    }

    public void onCallAdded(android.telecom.Call telecommCall) {
        Trace.beginSection("onCallAdded");
        CallHelper call = new CallHelper(telecommCall);
//        Log.d(this, "onCallAdded: callState=" + call.getState());
        if (call.getState() == CallHelper.State.INCOMING ||
                call.getState() == CallHelper.State.CALL_WAITING) {
            onIncoming(call, call.getCannedSmsResponses());
        } else {
            onUpdate(call);
        }
        Trace.endSection();
    }

    public void onCallRemoved(android.telecom.Call telecommCall) {
        if (mCallByTelecommCall.containsKey(telecommCall)) {
            CallHelper call = mCallByTelecommCall.get(telecommCall);
            if (updateCallInMap(call)) {
//                Log.w(this, "Removing call not previously disconnected " + call.getId());
            }
            updateCallTextMap(call, null);
        }
    }

    /**
     * Called when a single call disconnects.
     */
    public void onDisconnect(CallHelper call) {
        if (updateCallInMap(call)) {
//            Log.i(this, "onDisconnect: " + call);
            // notify those listening for changes on this specific change
            notifyCallUpdateListeners(call);
            // notify those listening for all disconnects
            notifyListenersOfDisconnect(call);
        }
    }

    /**
     * Called when a single call has changed.
     */
    public void onIncoming(CallHelper call, List<String> textMessages) {
        if (updateCallInMap(call)) {
//            Log.i(this, "onIncoming - " + call);
        }
        updateCallTextMap(call, textMessages);

        for (Listener listener : mListeners) {
            listener.onIncomingCall(call);
        }
    }

    public void onUpgradeToVideo(CallHelper call) {
//        Log.d(this, "onUpgradeToVideo call=" + call);
        for (Listener listener : mListeners) {
            listener.onUpgradeToVideo(call);
        }
    }

    /**
     * Called when a single call has changed.
     */
    public void onUpdate(CallHelper call) {
        Trace.beginSection("onUpdate");
        onUpdateCall(call);
        notifyGenericListeners();
        Trace.endSection();
    }

    /**
     * Called when a single call has changed session modification state.
     *
     * @param call                     The call.
     * @param sessionModificationState The new session modification state.
     */
    public void onSessionModificationStateChange(CallHelper call, int sessionModificationState) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onSessionModificationStateChange(sessionModificationState);
            }
        }
    }

    /**
     * Called when the last forwarded number changes for a call.  With IMS, the last forwarded
     * number changes due to a supplemental service notification, so it is not pressent at the
     * start of the call.
     *
     * @param call The call.
     */
    public void onLastForwardedNumberChange(CallHelper call) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onLastForwardedNumberChange();
            }
        }
    }

    /**
     * Called when the child number changes for a call.  The child number can be received after a
     * call is initially set up, so we need to be able to inform listeners of the change.
     *
     * @param call The call.
     */
    public void onChildNumberChange(CallHelper call) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onChildNumberChange();
            }
        }
    }

    public void notifyCallUpdateListeners(CallHelper call) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onCallChanged(call);
            }
        }
    }

    /**
     * Add a call update listener for a call id.
     *
     * @param callId   The call id to get updates for.
     * @param listener The listener to add.
     */
    public void addCallUpdateListener(String callId, CallUpdateListener listener) {
        List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(callId);
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<CallUpdateListener>();
            mCallUpdateListenerMap.put(callId, listeners);
        }
        listeners.add(listener);
    }

    /**
     * Remove a call update listener for a call id.
     *
     * @param callId   The call id to remove the listener for.
     * @param listener The listener to remove.
     */
    public void removeCallUpdateListener(String callId, CallUpdateListener listener) {
        List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(callId);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);

        mListeners.add(listener);

        // Let the listener know about the active calls immediately.
        listener.onCallListChange(this);
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * TODO: Change so that this function is not needed. Instead of assuming there is an active
     * call, the code should rely on the status of a specific CallHelper and allow the presenters to
     * update the CallHelper object when the active call changes.
     */
    public CallHelper getIncomingOrActive() {
        CallHelper retval = getIncomingCall();
        if (retval == null) {
            retval = getActiveCall();
        }
        return retval;
    }

    public CallHelper getOutgoingOrActive() {
        CallHelper retval = getOutgoingCall();
        if (retval == null) {
            retval = getActiveCall();
        }
        return retval;
    }

    /**
     * A call that is waiting for {@link PhoneAccount} selection
     */
    public CallHelper getWaitingForAccountCall() {
        return getFirstCallWithState(CallHelper.State.SELECT_PHONE_ACCOUNT);
    }

    public CallHelper getPendingOutgoingCall() {
        return getFirstCallWithState(CallHelper.State.CONNECTING);
    }

    public CallHelper getOutgoingCall() {
        CallHelper call = getFirstCallWithState(CallHelper.State.DIALING);
        if (call == null) {
            call = getFirstCallWithState(CallHelper.State.REDIALING);
        }
        return call;
    }

    public CallHelper getActiveCall() {
        return getFirstCallWithState(CallHelper.State.ACTIVE);
    }

    public CallHelper getBackgroundCall() {
        return getFirstCallWithState(CallHelper.State.ONHOLD);
    }

    public CallHelper getDisconnectedCall() {
        return getFirstCallWithState(CallHelper.State.DISCONNECTED);
    }

    public CallHelper getDisconnectingCall() {
        return getFirstCallWithState(CallHelper.State.DISCONNECTING);
    }

    public CallHelper getSecondBackgroundCall() {
        return getCallWithState(CallHelper.State.ONHOLD, 1);
    }

    public CallHelper getActiveOrBackgroundCall() {
        CallHelper call = getActiveCall();
        if (call == null) {
            call = getBackgroundCall();
        }
        return call;
    }

    public CallHelper getIncomingCall() {
        CallHelper call = getFirstCallWithState(CallHelper.State.INCOMING);
        if (call == null) {
            call = getFirstCallWithState(CallHelper.State.CALL_WAITING);
        }

        return call;
    }

    public CallHelper getFirstCall() {
        CallHelper result = getIncomingCall();
        if (result == null) {
            result = getPendingOutgoingCall();
        }
        if (result == null) {
            result = getOutgoingCall();
        }
        if (result == null) {
            result = getFirstCallWithState(CallHelper.State.ACTIVE);
        }
        if (result == null) {
            result = getDisconnectingCall();
        }
        if (result == null) {
            result = getDisconnectedCall();
        }
        return result;
    }

    public boolean hasLiveCall() {
        CallHelper call = getFirstCall();
        if (call == null) {
            return false;
        }
        return call != getDisconnectingCall() && call != getDisconnectedCall();
    }

    public CallHelper getVideoUpgradeRequestCall() {
        for (CallHelper call : mCallById.values()) {
            if (call.getSessionModificationState() ==
                    CallHelper.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
                return call;
            }
        }
        return null;
    }

    public CallHelper getCallById(String callId) {
        return mCallById.get(callId);
    }

    public CallHelper getCallByTelecommCall(android.telecom.Call telecommCall) {
        return mCallByTelecommCall.get(telecommCall);
    }

    public List<String> getTextResponses(String callId) {
        return mCallTextReponsesMap.get(callId);
    }

    /**
     * Returns first call found in the call map with the specified state.
     */
    public CallHelper getFirstCallWithState(int state) {
        return getCallWithState(state, 0);
    }

    /**
     * Returns the [position]th call found in the call map with the specified state.
     * TODO: Improve this logic to sort by call time.
     */
    public CallHelper getCallWithState(int state, int positionToFind) {
        CallHelper retval = null;
        int position = 0;
        for (CallHelper call : mCallById.values()) {
            if (call.getState() == state) {
                if (position >= positionToFind) {
                    retval = call;
                    break;
                } else {
                    position++;
                }
            }
        }

        return retval;
    }

    /**
     * This is called when the service disconnects, either expectedly or unexpectedly.
     * For the expected case, it's because we have no calls left.  For the unexpected case,
     * it is likely a crash of phone and we need to clean up our calls manually.  Without phone,
     * there can be no active calls, so this is relatively safe thing to do.
     */
    public void clearOnDisconnect() {
        for (CallHelper call : mCallById.values()) {
            final int state = call.getState();
            if (state != CallHelper.State.IDLE &&
                    state != CallHelper.State.INVALID &&
                    state != CallHelper.State.DISCONNECTED) {

                call.setState(CallHelper.State.DISCONNECTED);
                call.setDisconnectCause(new DisconnectCause(DisconnectCause.UNKNOWN));
                updateCallInMap(call);
            }
        }
        notifyGenericListeners();
    }

    /**
     * Called when the user has dismissed an error dialog. This indicates acknowledgement of
     * the disconnect cause, and that any pending disconnects should immediately occur.
     */
    public void onErrorDialogDismissed() {
        final Iterator<CallHelper> iterator = mPendingDisconnectCalls.iterator();
        while (iterator.hasNext()) {
            CallHelper call = iterator.next();
            iterator.remove();
            finishDisconnectedCall(call);
        }
    }

    /**
     * Processes an update for a single call.
     *
     * @param call The call to update.
     */
    private void onUpdateCall(CallHelper call) {
//        Log.d(this, "\t" + call);
        if (updateCallInMap(call)) {
//            Log.i(this, "onUpdate - " + call);
        }
        updateCallTextMap(call, call.getCannedSmsResponses());
        notifyCallUpdateListeners(call);
    }

    /**
     * Sends a generic notification to all listeners that something has changed.
     * It is up to the listeners to call back to determine what changed.
     */
    private void notifyGenericListeners() {
        for (Listener listener : mListeners) {
            listener.onCallListChange(this);
        }
    }

    private void notifyListenersOfDisconnect(CallHelper call) {
        for (Listener listener : mListeners) {
            listener.onDisconnect(call);
        }
    }

    /**
     * Updates the call entry in the local map.
     *
     * @return false if no call previously existed and no call was added, otherwise true.
     */
    private boolean updateCallInMap(CallHelper call) {
        Preconditions.checkNotNull(call);

        boolean updated = false;

        if (call.getState() == CallHelper.State.DISCONNECTED) {
            // update existing (but do not add!!) disconnected calls
            if (mCallById.containsKey(call.getId())) {
                // For disconnected calls, we want to keep them alive for a few seconds so that the
                // UI has a chance to display anything it needs when a call is disconnected.

                // Set up a timer to destroy the call after X seconds.
                final Message msg = mHandler.obtainMessage(EVENT_DISCONNECTED_TIMEOUT, call);
                mHandler.sendMessageDelayed(msg, getDelayForDisconnect(call));
                mPendingDisconnectCalls.add(call);

                mCallById.put(call.getId(), call);
                mCallByTelecommCall.put(call.getTelecommCall(), call);
                updated = true;
            }
        } else if (!isCallDead(call)) {
            mCallById.put(call.getId(), call);
            mCallByTelecommCall.put(call.getTelecommCall(), call);
            updated = true;
        } else if (mCallById.containsKey(call.getId())) {
            mCallById.remove(call.getId());
            mCallByTelecommCall.remove(call.getTelecommCall());
            updated = true;
        }

        return updated;
    }

    private int getDelayForDisconnect(CallHelper call) {
        Preconditions.checkState(call.getState() == CallHelper.State.DISCONNECTED);


        final int cause = call.getDisconnectCause().getCode();
        final int delay;
        switch (cause) {
            case DisconnectCause.LOCAL:
                delay = DISCONNECTED_CALL_SHORT_TIMEOUT_MS;
                break;
            case DisconnectCause.REMOTE:
            case DisconnectCause.ERROR:
                delay = DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS;
                break;
            case DisconnectCause.REJECTED:
            case DisconnectCause.MISSED:
            case DisconnectCause.CANCELED:
                // no delay for missed/rejected incoming calls and canceled outgoing calls.
                delay = 0;
                break;
            default:
                delay = DISCONNECTED_CALL_LONG_TIMEOUT_MS;
                break;
        }

        return delay;
    }

    private void updateCallTextMap(CallHelper call, List<String> textResponses) {
        Preconditions.checkNotNull(call);

        if (!isCallDead(call)) {
            if (textResponses != null) {
                mCallTextReponsesMap.put(call.getId(), textResponses);
            }
        } else if (mCallById.containsKey(call.getId())) {
            mCallTextReponsesMap.remove(call.getId());
        }
    }

    private boolean isCallDead(CallHelper call) {
        final int state = call.getState();
        return CallHelper.State.IDLE == state || CallHelper.State.INVALID == state;
    }

    /**
     * Notifies all video calls of a change in device orientation.
     *
     * @param rotation The new rotation angle (in degrees).
     */
//    public void notifyCallsOfDeviceRotation(int rotation) {
//        for (CallHelper call : mCallById.values()) {
//            // First, ensure a VideoCall is set on the call so that the change can be sent to the
//            // provider (a VideoCall can be present for a call that does not currently have video,
//            // but can be upgraded to video).
//            // Second, ensure that the call videoState has video enabled (there is no need to set
//            // device orientation on a voice call which has not yet been upgraded to video).
//            if (call.getVideoCall() != null && CallUtils.isVideoCall(call)) {
//                call.getVideoCall().setDeviceOrientation(rotation);
//            }
//        }
//    }

    /**
     * Sets up a call for deletion and notifies listeners of change.
     */
    private void finishDisconnectedCall(CallHelper call) {
        if (mPendingDisconnectCalls.contains(call)) {
            mPendingDisconnectCalls.remove(call);
        }
        call.setState(CallHelper.State.IDLE);
        updateCallInMap(call);
        notifyGenericListeners();
    }

    /**
     * Listener interface for any class that wants to be notified of changes
     * to the call list.
     */
    public interface Listener {
        /**
         * Called when a new incoming call comes in.
         * This is the only method that gets called for incoming calls. Listeners
         * that want to perform an action on incoming call should respond in this method
         * because {@link #onCallListChange} does not automatically get called for
         * incoming calls.
         */
        public void onIncomingCall(CallHelper call);

        /**
         * Called when a new modify call request comes in
         * This is the only method that gets called for modify requests.
         */
        public void onUpgradeToVideo(CallHelper call);

        /**
         * Called anytime there are changes to the call list.  The change can be switching call
         * states, updating information, etc. This method will NOT be called for new incoming
         * calls and for calls that switch to disconnected state. Listeners must add actions
         * to those method implementations if they want to deal with those actions.
         */
        public void onCallListChange(CallList callList);

        /**
         * Called when a call switches to the disconnected state.  This is the only method
         * that will get called upon disconnection.
         */
        public void onDisconnect(CallHelper call);


    }

    public interface CallUpdateListener {
        // TODO: refactor and limit arg to be call state.  Caller info is not needed.
        public void onCallChanged(CallHelper call);

        /**
         * Notifies of a change to the session modification state for a call.
         *
         * @param sessionModificationState The new session modification state.
         */
        public void onSessionModificationStateChange(int sessionModificationState);

        /**
         * Notifies of a change to the last forwarded number for a call.
         */
        public void onLastForwardedNumberChange();

        /**
         * Notifies of a change to the child number for a call.
         */
        public void onChildNumberChange();
    }
}
