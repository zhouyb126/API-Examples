package io.agora.api.example.examples.basic;

import static io.agora.api.example.common.model.Examples.BASIC;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import java.util.LinkedHashMap;
import java.util.Map;

import io.agora.api.example.MainApplication;
import io.agora.api.example.R;
import io.agora.api.example.annotation.Example;
import io.agora.api.example.common.BaseFragment;
import io.agora.api.example.common.widget.AudioSeatManager;
import io.agora.api.example.utils.CommonUtil;
import io.agora.api.example.utils.TokenUtils;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;

/**This demo demonstrates how to make a one-to-one voice call
 * @author cjw*/
@Example(
        index = 1,
        group = BASIC,
        name = R.string.item_joinaudio,
        actionId = R.id.action_mainFragment_to_joinChannelAudio,
        tipsId = R.string.joinchannelaudio
)
public class JoinChannelAudio extends BaseFragment implements View.OnClickListener
{
    private static final String TAG = JoinChannelAudio.class.getSimpleName();
    private Spinner audioProfileInput;
    private Spinner audioScenarioInput;
    private EditText et_channel;
    private Button mute, join, speaker;
    private SeekBar record, playout, inear;
    private Switch inEarSwitch;
    private RtcEngine engine;
    private int myUid;
    private boolean joined = false;
    private AudioSeatManager audioSeatManager;

    private SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if(seekBar.getId() == record.getId()){
                engine.adjustRecordingSignalVolume(progress);
            }
            else if(seekBar.getId() == playout.getId()){
                engine.adjustPlaybackSignalVolume(progress);
            }
            else if(seekBar.getId() == inear.getId()){
                if(progress == 0){
                    engine.enableInEarMonitoring(false);
                }
                else {
                    engine.enableInEarMonitoring(true);
                    engine.setInEarMonitoringVolume(progress);
                }
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        handler = new Handler();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_joinchannel_audio, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        join = view.findViewById(R.id.btn_join);
        et_channel = view.findViewById(R.id.et_channel);
        audioProfileInput = view.findViewById(R.id.audio_profile_spinner);
        audioScenarioInput = view.findViewById(R.id.audio_scenario_spinner);
        audioScenarioInput.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(joined){
                    int scenario = Constants.AudioScenario.valueOf(audioScenarioInput.getSelectedItem().toString()).ordinal();
                    engine.setAudioScenario(scenario);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        view.findViewById(R.id.btn_join).setOnClickListener(this);
        mute = view.findViewById(R.id.microphone);
        mute.setOnClickListener(this);
        speaker = view.findViewById(R.id.btn_speaker);
        speaker.setOnClickListener(this);
        speaker.setActivated(true);
        record = view.findViewById(R.id.recordingVol);
        playout = view.findViewById(R.id.playoutVol);
        inear = view.findViewById(R.id.inEarMonitorVol);
        record.setOnSeekBarChangeListener(seekBarChangeListener);
        playout.setOnSeekBarChangeListener(seekBarChangeListener);
        inear.setOnSeekBarChangeListener(seekBarChangeListener);
        inEarSwitch = view.findViewById(R.id.inEarMonitorSwitch);
        inEarSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            engine.enableInEarMonitoring(isChecked);
            inear.setEnabled(isChecked);
        });
        record.setEnabled(false);
        playout.setEnabled(false);
        inear.setEnabled(false);
        inEarSwitch.setEnabled(false);

        audioSeatManager = new AudioSeatManager(
                view.findViewById(R.id.audio_place_01),
                view.findViewById(R.id.audio_place_02),
                view.findViewById(R.id.audio_place_03),
                view.findViewById(R.id.audio_place_04),
                view.findViewById(R.id.audio_place_05),
                view.findViewById(R.id.audio_place_06)
        );
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        // Check if the context is valid
        Context context = getContext();
        if (context == null)
        {
            return;
        }
        try
        {
            RtcEngineConfig config = new RtcEngineConfig();
            /**
             * The context of Android Activity
             */
            config.mContext = context.getApplicationContext();
            /**
             * The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id"> How to get the App ID</a>
             */
            config.mAppId = getString(R.string.agora_app_id);
            /** Sets the channel profile of the Agora RtcEngine.
             CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
             Use this profile in one-on-one calls or group calls, where all users can talk freely.
             CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
             channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
             an audience can only receive streams.*/
            config.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
            /**
             * IRtcEngineEventHandler is an abstract class providing default implementation.
             * The SDK uses this class to report to the app on SDK runtime events.
             */
            config.mEventHandler = iRtcEngineEventHandler;
            config.mAudioScenario = Constants.AudioScenario.valueOf(audioScenarioInput.getSelectedItem().toString()).ordinal();
            config.mAreaCode = ((MainApplication)getActivity().getApplication()).getGlobalSettings().getAreaCode();
            engine = RtcEngine.create(config);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            getActivity().onBackPressed();
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        /**leaveChannel and Destroy the RtcEngine instance*/
        if(engine != null)
        {
            engine.leaveChannel();
        }
        handler.post(RtcEngine::destroy);
        engine = null;
    }

    @Override
    public void onClick(View v)
    {
        if (v.getId() == R.id.btn_join)
        {
            if (!joined)
            {
                CommonUtil.hideInputBoard(getActivity(), et_channel);
                // call when join button hit
                String channelId = et_channel.getText().toString();
                // Check permission
                if (AndPermission.hasPermissions(this, Permission.Group.STORAGE, Permission.Group.MICROPHONE, Permission.Group.CAMERA))
                {
                    joinChannel(channelId);
                    audioProfileInput.setEnabled(false);
                    return;
                }
                // Request permission
                AndPermission.with(this).runtime().permission(
                        Permission.Group.STORAGE,
                        Permission.Group.MICROPHONE
                ).onGranted(permissions ->
                {
                    // Permissions Granted
                    joinChannel(channelId);
                    audioProfileInput.setEnabled(false);
                }).start();
            }
            else
            {
                joined = false;
                /**After joining a channel, the user must call the leaveChannel method to end the
                 * call before joining another channel. This method returns 0 if the user leaves the
                 * channel and releases all resources related to the call. This method call is
                 * asynchronous, and the user has not exited the channel when the method call returns.
                 * Once the user leaves the channel, the SDK triggers the onLeaveChannel callback.
                 * A successful leaveChannel method call triggers the following callbacks:
                 *      1:The local client: onLeaveChannel.
                 *      2:The remote client: onUserOffline, if the user leaving the channel is in the
                 *          Communication channel, or is a BROADCASTER in the Live Broadcast profile.
                 * @returns 0: Success.
                 *          < 0: Failure.
                 * PS:
                 *      1:If you call the destroy method immediately after calling the leaveChannel
                 *          method, the leaveChannel process interrupts, and the SDK does not trigger
                 *          the onLeaveChannel callback.
                 *      2:If you call the leaveChannel method during CDN live streaming, the SDK
                 *          triggers the removeInjectStreamUrl method.*/
                engine.leaveChannel();
                join.setText(getString(R.string.join));
                speaker.setText(getString(R.string.speaker));
                speaker.setEnabled(false);
                mute.setText(getString(R.string.closemicrophone));
                mute.setEnabled(false);
                audioProfileInput.setEnabled(true);
                record.setEnabled(false);
                playout.setEnabled(false);
                inear.setEnabled(false);
                inEarSwitch.setEnabled(false);
                inEarSwitch.setChecked(false);
                audioSeatManager.downAllSeats();
            }
        }
        else if (v.getId() == R.id.microphone)
        {
            mute.setActivated(!mute.isActivated());
            mute.setText(getString(mute.isActivated() ? R.string.openmicrophone : R.string.closemicrophone));
            /**Turn off / on the microphone, stop / start local audio collection and push streaming.*/
            engine.muteLocalAudioStream(mute.isActivated());
        }
        else if (v.getId() == R.id.btn_speaker)
        {
            speaker.setActivated(!speaker.isActivated());
            speaker.setText(getString(speaker.isActivated() ? R.string.speaker : R.string.earpiece));
            /**Turn off / on the speaker and change the audio playback route.*/
            engine.setDefaultAudioRoutetoSpeakerphone(speaker.isActivated());
        }
    }

    /**
     * @param channelId Specify the channel name that you want to join.
     *                  Users that input the same channel name join the same channel.*/
    private void joinChannel(String channelId)
    {
        /**In the demo, the default is to enter as the anchor.*/
        engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);

        int profile = Constants.AudioProfile.valueOf(audioProfileInput.getSelectedItem().toString()).ordinal();
        engine.setAudioProfile(profile);

        int scenario = Constants.AudioScenario.valueOf(audioScenarioInput.getSelectedItem().toString()).ordinal();
        engine.setAudioScenario(scenario);

        ChannelMediaOptions option = new ChannelMediaOptions();
        option.autoSubscribeAudio = true;
        option.autoSubscribeVideo = true;

        /**Please configure accessToken in the string_config file.
         * A temporary token generated in Console. A temporary token is valid for 24 hours. For details, see
         *      https://docs.agora.io/en/Agora%20Platform/token?platform=All%20Platforms#get-a-temporary-token
         * A token generated at the server. This applies to scenarios with high-security requirements. For details, see
         *      https://docs.agora.io/en/cloud-recording/token_server_java?platform=Java*/
        TokenUtils.gen(requireContext(), channelId, 0, ret -> {

            /** Allows a user to join a channel.
             if you do not specify the uid, we will generate the uid for you*/
            int res = engine.joinChannel(ret, channelId, 0, option);
            if (res != 0)
            {
                // Usually happens with invalid parameters
                // Error code description can be found at:
                // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
                // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
                showAlert(RtcEngine.getErrorDescription(Math.abs(res)));
                Log.e(TAG, RtcEngine.getErrorDescription(Math.abs(res)));
                return;
            }
            // Prevent repeated entry
            join.setEnabled(false);
        });


    }

    /**IRtcEngineEventHandler is an abstract class providing default implementation.
     * The SDK uses this class to report to the app on SDK runtime events.*/
    private final IRtcEngineEventHandler iRtcEngineEventHandler = new IRtcEngineEventHandler()
    {
        /**Reports a warning during SDK runtime.
         * Warning code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_warn_code.html*/
        @Override
        public void onWarning(int warn)
        {
            Log.w(TAG, String.format("onWarning code %d message %s", warn, RtcEngine.getErrorDescription(warn)));
        }

        /**Occurs when a user leaves the channel.
         * @param stats With this callback, the application retrieves the channel information,
         *              such as the call duration and statistics.*/
        @Override
        public void onLeaveChannel(RtcStats stats)
        {
            super.onLeaveChannel(stats);
            Log.i(TAG, String.format("local user %d leaveChannel!", myUid));
            showLongToast(String.format("local user %d leaveChannel!", myUid));
        }

        /**Occurs when the local user joins a specified channel.
         * The channel name assignment is based on channelName specified in the joinChannel method.
         * If the uid is not specified when joinChannel is called, the server automatically assigns a uid.
         * @param channel Channel name
         * @param uid User ID
         * @param elapsed Time elapsed (ms) from the user calling joinChannel until this callback is triggered*/
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed)
        {
            Log.i(TAG, String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            showLongToast(String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            myUid = uid;
            joined = true;
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    speaker.setEnabled(true);
                    mute.setEnabled(true);
                    join.setEnabled(true);
                    join.setText(getString(R.string.leave));
                    record.setEnabled(true);
                    playout.setEnabled(true);
                    inear.setEnabled(inEarSwitch.isChecked());
                    inEarSwitch.setEnabled(true);
                    audioSeatManager.upLocalSeat(uid);
                }
            });
        }

        /**Since v2.9.0.
         * This callback indicates the state change of the remote audio stream.
         * PS: This callback does not work properly when the number of users (in the Communication profile) or
         *     broadcasters (in the Live-broadcast profile) in the channel exceeds 17.
         * @param uid ID of the user whose audio state changes.
         * @param state State of the remote audio
         *   REMOTE_AUDIO_STATE_STOPPED(0): The remote audio is in the default state, probably due
         *              to REMOTE_AUDIO_REASON_LOCAL_MUTED(3), REMOTE_AUDIO_REASON_REMOTE_MUTED(5),
         *              or REMOTE_AUDIO_REASON_REMOTE_OFFLINE(7).
         *   REMOTE_AUDIO_STATE_STARTING(1): The first remote audio packet is received.
         *   REMOTE_AUDIO_STATE_DECODING(2): The remote audio stream is decoded and plays normally,
         *              probably due to REMOTE_AUDIO_REASON_NETWORK_RECOVERY(2),
         *              REMOTE_AUDIO_REASON_LOCAL_UNMUTED(4) or REMOTE_AUDIO_REASON_REMOTE_UNMUTED(6).
         *   REMOTE_AUDIO_STATE_FROZEN(3): The remote audio is frozen, probably due to
         *              REMOTE_AUDIO_REASON_NETWORK_CONGESTION(1).
         *   REMOTE_AUDIO_STATE_FAILED(4): The remote audio fails to start, probably due to
         *              REMOTE_AUDIO_REASON_INTERNAL(0).
         * @param reason The reason of the remote audio state change.
         *   REMOTE_AUDIO_REASON_INTERNAL(0): Internal reasons.
         *   REMOTE_AUDIO_REASON_NETWORK_CONGESTION(1): Network congestion.
         *   REMOTE_AUDIO_REASON_NETWORK_RECOVERY(2): Network recovery.
         *   REMOTE_AUDIO_REASON_LOCAL_MUTED(3): The local user stops receiving the remote audio
         *               stream or disables the audio module.
         *   REMOTE_AUDIO_REASON_LOCAL_UNMUTED(4): The local user resumes receiving the remote audio
         *              stream or enables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_MUTED(5): The remote user stops sending the audio stream or
         *               disables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_UNMUTED(6): The remote user resumes sending the audio stream
         *              or enables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_OFFLINE(7): The remote user leaves the channel.
         *   @param elapsed Time elapsed (ms) from the local user calling the joinChannel method
         *                  until the SDK triggers this callback.*/
        @Override
        public void onRemoteAudioStateChanged(int uid, int state, int reason, int elapsed) {
            super.onRemoteAudioStateChanged(uid, state, reason, elapsed);
            Log.i(TAG, "onRemoteAudioStateChanged->" + uid + ", state->" + state + ", reason->" + reason);
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
         * @param uid ID of the user whose audio state changes.
         * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
         *                until this callback is triggered.*/
        @Override
        public void onUserJoined(int uid, int elapsed)
        {
            super.onUserJoined(uid, elapsed);
            Log.i(TAG, "onUserJoined->" + uid);
            showLongToast(String.format("user %d joined!", uid));
            runOnUIThread(() -> {
                audioSeatManager.upRemoteSeat(uid);
            });
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) leaves the channel.
         * @param uid ID of the user whose audio state changes.
         * @param reason Reason why the user goes offline:
         *   USER_OFFLINE_QUIT(0): The user left the current channel.
         *   USER_OFFLINE_DROPPED(1): The SDK timed out and the user dropped offline because no data
         *              packet was received within a certain period of time. If a user quits the
         *               call and the message is not passed to the SDK (due to an unreliable channel),
         *               the SDK assumes the user dropped offline.
         *   USER_OFFLINE_BECOME_AUDIENCE(2): (Live broadcast only.) The client role switched from
         *               the host to the audience.*/
        @Override
        public void onUserOffline(int uid, int reason)
        {
            Log.i(TAG, String.format("user %d offline! reason:%d", uid, reason));
            showLongToast(String.format("user %d offline! reason:%d", uid, reason));
            runOnUIThread(() -> {
                audioSeatManager.downSeat(uid);
            });
        }

        @Override
        public void onLocalAudioStats(LocalAudioStats stats) {
            super.onLocalAudioStats(stats);
            runOnUIThread(() -> {
                Map<String, String> _stats = new LinkedHashMap<>();
                _stats.put("sentSampleRate", stats.sentSampleRate + "");
                _stats.put("sentBitrate", stats.sentBitrate + " kbps");
                _stats.put("internalCodec", stats.internalCodec + "");
                _stats.put("audioDeviceDelay", stats.audioDeviceDelay + " ms");
                audioSeatManager.getLocalSeat().updateStats(_stats);
            });
        }

        @Override
        public void onRemoteAudioStats(RemoteAudioStats stats) {
            super.onRemoteAudioStats(stats);
            runOnUIThread(() -> {
                Map<String, String> _stats = new LinkedHashMap<>();
                _stats.put("numChannels", stats.numChannels + "");
                _stats.put("receivedBitrate", stats.receivedBitrate + " kbps");
                _stats.put("audioLossRate", stats.audioLossRate + "");
                _stats.put("jitterBufferDelay", stats.jitterBufferDelay + " ms");
                audioSeatManager.getRemoteSeat(stats.uid).updateStats(_stats);
            });
        }
    };
}
