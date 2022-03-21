package io.agora.api.example.examples.advanced;

import static io.agora.api.example.common.model.Examples.ADVANCED;

import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.agora.api.example.R;
import io.agora.api.example.annotation.Example;
import io.agora.api.example.common.BaseFragment;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.audio.SpatialAudioParams;

@Example(
        index = 30,
        group = ADVANCED,
        name = R.string.item_spatial_sound,
        actionId = R.id.action_mainFragment_to_spatial_sound,
        tipsId = R.string.setvideoprofile
)
public class SpatialSound extends BaseFragment {
    private static final String TAG = SpatialSound.class.getSimpleName();
    private static final int ECHO_INTERVAL_IN_SECONDS = 10;

    private ImageView listenerIv;
    private ImageView speakerIv;
    private TextView startTv;
    private View rootView;

    private RtcEngine engine;
    private int speakerUid;

    private final ListenerOnTouchListener listenerOnTouchListener = new ListenerOnTouchListener();
    private final InnerRtcEngineEventHandler iRtcEngineEventHandler = new InnerRtcEngineEventHandler();

    private CountDownTimer countDownTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spatial_sound, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view.findViewById(R.id.root_view);
        listenerIv = view.findViewById(R.id.iv_listener);
        speakerIv = view.findViewById(R.id.iv_speaker);
        startTv = view.findViewById(R.id.tv_start);
        speakerIv.setOnTouchListener(listenerOnTouchListener);
        startTv.setOnClickListener(v -> startEcho());
    }


    private void startEcho() {
        if (countDownTimer != null) {
            return;
        }
        engine.startEchoTest(ECHO_INTERVAL_IN_SECONDS);
        startTv.setEnabled(false);
        startTv.setText(getString(R.string.recording_start) + " " + ECHO_INTERVAL_IN_SECONDS);
        countDownTimer = new CountDownTimer(ECHO_INTERVAL_IN_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                handler.post(() -> startTv.setText(getString(R.string.recording_start) + " " + (millisUntilFinished / 1000)));
            }

            @Override
            public void onFinish() {
                handler.post(() -> {
                    startTv.setVisibility(View.GONE);
                    startTv.setText(R.string.click_start);
                    countDownTimer = null;
                    handler.postDelayed(() -> {
                        engine.stopEchoTest();
                        startTv.setVisibility(View.VISIBLE);
                        startTv.setEnabled(true);
                        speakerUid = 0;
                    }, (ECHO_INTERVAL_IN_SECONDS + 2)* 1000);
                });
            }
        };
        countDownTimer.start();
    }


    private void updateSpatialSoundParam() {
        float transX = speakerIv.getTranslationX();
        float transY = speakerIv.getTranslationY();
        double viewDistance = Math.sqrt(Math.pow(transX, 2) + Math.pow(transY, 2));
        double viewMaxDistance = Math.sqrt(Math.pow((rootView.getWidth() - speakerIv.getWidth()) / 2.0f, 2) + Math.pow((rootView.getHeight() - speakerIv.getHeight()) / 2.0f, 2));
        double spkMaxDistance = 50;
        double spkMinDistance = 1;

        double spkDistance = spkMaxDistance * (viewDistance / viewMaxDistance);
        if (spkDistance < spkMinDistance) {
            spkDistance = spkMinDistance;
        }
        if (spkDistance > spkMaxDistance) {
            spkDistance = spkMaxDistance;
        }
        double degree = getDegree(0, 0, 0, -10, (int) transX, (int) transY);
        if (transX > 0) {
            degree = 360 - degree;
        }

        SpatialAudioParams params = new SpatialAudioParams();
        params.spk_distance = spkDistance;
        params.spk_azimuth = degree;
        params.spk_elevation = 0.0;
        params.spk_orientation = 0;
        params.enable_blur = true;
        params.enable_air_absorb = true;

        Log.d(TAG, "updateSpatialSoundParam spk_uid=" + speakerUid + ",spk_distance=" + params.spk_distance + ", spk_azimuth=" + params.spk_azimuth);
        if (speakerUid != 0) {
            engine.setRemoteUserSpatialAudioParams(speakerUid, params);
        }
    }

    private int getDegree(int vertexPointX, int vertexPointY, int point0X, int point0Y, int point1X, int point1Y) {
        //向量的点乘
        int vector = (point0X - vertexPointX) * (point1X - vertexPointX) + (point0Y - vertexPointY) * (point1Y - vertexPointY);
        //向量的模乘
        double sqrt = Math.sqrt(
                (Math.abs((point0X - vertexPointX) * (point0X - vertexPointX)) + Math.abs((point0Y - vertexPointY) * (point0Y - vertexPointY)))
                        * (Math.abs((point1X - vertexPointX) * (point1X - vertexPointX)) + Math.abs((point1Y - vertexPointY) * (point1Y - vertexPointY)))
        );
        //反余弦计算弧度
        double radian = Math.acos(vector / sqrt);
        //弧度转角度制
        return (int) (180 * radian / Math.PI);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            engine.stopEchoTest();
            countDownTimer.cancel();
            countDownTimer = null;
        }
        handler.removeCallbacksAndMessages(null);
        handler.post(RtcEngine::destroy);
        engine = null;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Check if the context is valid
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            /**Creates an RtcEngine instance.
             * @param context The context of Android Activity
             * @param appId The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id">
             *              How to get the App ID</a>
             * @param handler IRtcEngineEventHandler is an abstract class providing default implementation.
             *                The SDK uses this class to report to the app on SDK runtime events.*/
            String appId = getString(R.string.agora_app_id);
            engine = RtcEngine.create(getContext().getApplicationContext(), appId, iRtcEngineEventHandler);
            engine.enableSpatialAudio(true);
        } catch (Exception e) {
            e.printStackTrace();
            getActivity().onBackPressed();
        }
    }


    private class ListenerOnTouchListener implements View.OnTouchListener {
        private float startX, startY, tranX, tranY, curX, curY, maxX, maxY, minX, minY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    tranX = v.getTranslationX();
                    tranY = v.getTranslationY();
                    if (v.getParent() instanceof ViewGroup) {
                        maxX = (((ViewGroup) v.getParent()).getWidth() - v.getWidth() + 1) / 2;
                        maxY = (((ViewGroup) v.getParent()).getHeight() - v.getHeight() + 1) / 2;
                        minX = -maxX;
                        minY = -maxY;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    curX = event.getRawX();
                    curY = event.getRawY();
                    float newTranX = tranX + curX - startX;
                    if (minX != 0 && newTranX < minX) {
                        newTranX = minX;
                    }
                    if (maxX != 0 && newTranX > maxX) {
                        newTranX = maxX;
                    }
                    v.setTranslationX(newTranX);
                    float newTranY = tranY + curY - startY;
                    if (minY != 0 && newTranY < minY) {
                        newTranY = minY;
                    }
                    if (maxY != 0 && newTranY > maxY) {
                        newTranY = maxY;
                    }
                    v.setTranslationY(newTranY);
                    updateSpatialSoundParam();
                    break;
                case MotionEvent.ACTION_UP:
                    break;
            }
            return true;
        }
    }

    /**
     * IRtcEngineEventHandler is an abstract class providing default implementation.
     * The SDK uses this class to report to the app on SDK runtime events.
     */
    private class InnerRtcEngineEventHandler extends IRtcEngineEventHandler {
        /**
         * Reports a warning during SDK runtime.
         * Warning code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_warn_code.html
         */
        @Override
        public void onWarning(int warn) {
            Log.w(TAG, String.format("onWarning code %d message %s", warn, RtcEngine.getErrorDescription(warn)));
        }

        /**
         * Reports an error during SDK runtime.
         * Error code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
         */
        @Override
        public void onError(int err) {
            Log.e(TAG, String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
            showAlert(String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
        }


        /**
         * Since v2.9.0.
         * This callback indicates the state change of the remote audio stream.
         * PS: This callback does not work properly when the number of users (in the Communication profile) or
         * broadcasters (in the Live-broadcast profile) in the channel exceeds 17.
         *
         * @param uid     ID of the user whose audio state changes.
         * @param state   State of the remote audio
         *                REMOTE_AUDIO_STATE_STOPPED(0): The remote audio is in the default state, probably due
         *                to REMOTE_AUDIO_REASON_LOCAL_MUTED(3), REMOTE_AUDIO_REASON_REMOTE_MUTED(5),
         *                or REMOTE_AUDIO_REASON_REMOTE_OFFLINE(7).
         *                REMOTE_AUDIO_STATE_STARTING(1): The first remote audio packet is received.
         *                REMOTE_AUDIO_STATE_DECODING(2): The remote audio stream is decoded and plays normally,
         *                probably due to REMOTE_AUDIO_REASON_NETWORK_RECOVERY(2),
         *                REMOTE_AUDIO_REASON_LOCAL_UNMUTED(4) or REMOTE_AUDIO_REASON_REMOTE_UNMUTED(6).
         *                REMOTE_AUDIO_STATE_FROZEN(3): The remote audio is frozen, probably due to
         *                REMOTE_AUDIO_REASON_NETWORK_CONGESTION(1).
         *                REMOTE_AUDIO_STATE_FAILED(4): The remote audio fails to start, probably due to
         *                REMOTE_AUDIO_REASON_INTERNAL(0).
         * @param reason  The reason of the remote audio state change.
         *                REMOTE_AUDIO_REASON_INTERNAL(0): Internal reasons.
         *                REMOTE_AUDIO_REASON_NETWORK_CONGESTION(1): Network congestion.
         *                REMOTE_AUDIO_REASON_NETWORK_RECOVERY(2): Network recovery.
         *                REMOTE_AUDIO_REASON_LOCAL_MUTED(3): The local user stops receiving the remote audio
         *                stream or disables the audio module.
         *                REMOTE_AUDIO_REASON_LOCAL_UNMUTED(4): The local user resumes receiving the remote audio
         *                stream or enables the audio module.
         *                REMOTE_AUDIO_REASON_REMOTE_MUTED(5): The remote user stops sending the audio stream or
         *                disables the audio module.
         *                REMOTE_AUDIO_REASON_REMOTE_UNMUTED(6): The remote user resumes sending the audio stream
         *                or enables the audio module.
         *                REMOTE_AUDIO_REASON_REMOTE_OFFLINE(7): The remote user leaves the channel.
         * @param elapsed Time elapsed (ms) from the local user calling the joinChannel method
         *                until the SDK triggers this callback.
         */
        @Override
        public void onRemoteAudioStateChanged(int uid, int state, int reason, int elapsed) {
            super.onRemoteAudioStateChanged(uid, state, reason, elapsed);
            Log.i(TAG, "onRemoteAudioStateChanged->" + uid + ", state->" + state + ", reason->" + reason);
        }

        /**
         * Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
         *
         * @param uid     ID of the user whose audio state changes.
         * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
         *                until this callback is triggered.
         */
        @Override
        public void onUserJoined(int uid, int elapsed) {
            super.onUserJoined(uid, elapsed);
            Log.i(TAG, "onUserJoined->" + uid);
            showLongToast(String.format("user %d joined!", uid));
            speakerUid = uid;
            handler.post(SpatialSound.this::updateSpatialSoundParam);
        }

        /**
         * Occurs when a remote user (Communication)/host (Live Broadcast) leaves the channel.
         *
         * @param uid    ID of the user whose audio state changes.
         * @param reason Reason why the user goes offline:
         *               USER_OFFLINE_QUIT(0): The user left the current channel.
         *               USER_OFFLINE_DROPPED(1): The SDK timed out and the user dropped offline because no data
         *               packet was received within a certain period of time. If a user quits the
         *               call and the message is not passed to the SDK (due to an unreliable channel),
         *               the SDK assumes the user dropped offline.
         *               USER_OFFLINE_BECOME_AUDIENCE(2): (Live broadcast only.) The client role switched from
         *               the host to the audience.
         */
        @Override
        public void onUserOffline(int uid, int reason) {
            Log.i(TAG, String.format("user %d offline! reason:%d", uid, reason));
            showLongToast(String.format("user %d offline! reason:%d", uid, reason));
            speakerUid = 0;
        }

    }
}