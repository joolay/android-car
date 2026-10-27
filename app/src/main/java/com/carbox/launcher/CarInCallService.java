package com.carbox.launcher;

import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

/** Receives calls from Android's calling system when Car Launcher is chosen as the box's Phone app. */
public class CarInCallService extends InCallService {
    private final Call.Callback cb = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (call == CallState.call) CallState.updateTelecom();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        U.init((android.content.Context) this);
        Store.init(this);
        Apps.init(this);
    }

    @Override
    public void onCallAdded(Call call) {
        call.registerCallback(cb);
        CallState.fromTelecom(this, call);
        startActivity(CallState.screenIntent(this));
    }

    @Override
    public void onCallRemoved(Call call) {
        call.unregisterCallback(cb);
        if (call == CallState.call) CallState.clear();
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        CallState.muted = state.isMuted();
        CallState.changed();
    }
}
