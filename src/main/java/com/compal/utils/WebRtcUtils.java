package com.compal.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Size;

import androidx.annotation.Nullable;

import com.appwebrtc.AppRTCAudioManager;
import com.appwebrtc.AppRTCClient;
import com.appwebrtc.DirectRTCClient;
import com.appwebrtc.PeerConnectionClient;
import com.appwebrtc.WebSocketRTCClient;
import com.appwebrtc.util.AppRTCUtils;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsResult;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoSink;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WebRtcUtils implements AppRTCClient.SignalingEvents,
        PeerConnectionClient.PeerConnectionEvents {

    private static final String TAG = "WebRtcUtils";

    public static String SignalingWsUrl = "ws://192.168.10.252";
    public static String ip = "";
    public static String roomId = "";

    public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
    public static final String EXTRA_URLPARAMETERS = "org.appspot.apprtc.URLPARAMETERS";
    public static final String EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK";
    public static final String EXTRA_VIDEO_CALL = "org.appspot.apprtc.VIDEO_CALL";
    public static final String EXTRA_SCREENCAPTURE = "org.appspot.apprtc.SCREENCAPTURE";
    public static final String EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2";
    public static final String EXTRA_VIDEO_WIDTH = "org.appspot.apprtc.VIDEO_WIDTH";
    public static final String EXTRA_VIDEO_HEIGHT = "org.appspot.apprtc.VIDEO_HEIGHT";
    public static final String EXTRA_VIDEO_FPS = "org.appspot.apprtc.VIDEO_FPS";
    public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED = "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
    public static final String EXTRA_VIDEO_BITRATE = "org.appspot.apprtc.VIDEO_BITRATE";
    public static final String EXTRA_VIDEOCODEC = "org.appspot.apprtc.VIDEOCODEC";
    public static final String EXTRA_HWCODEC_ENABLED = "org.appspot.apprtc.HWCODEC";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE";
    public static final String EXTRA_FLEXFEC_ENABLED = "org.appspot.apprtc.FLEXFEC";
    public static final String EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC";
    public static final String EXTRA_NOAUDIOPROCESSING_ENABLED = "org.appspot.apprtc.NOAUDIOPROCESSING";
    public static final String EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP";
    public static final String EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED = "org.appspot.apprtc.SAVE_INPUT_AUDIO_TO_FILE";
    public static final String EXTRA_OPENSLES_ENABLED = "org.appspot.apprtc.OPENSLES";
    public static final String EXTRA_DISABLE_BUILT_IN_AEC = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC";
    public static final String EXTRA_DISABLE_BUILT_IN_AGC = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC";
    public static final String EXTRA_DISABLE_BUILT_IN_NS = "org.appspot.apprtc.DISABLE_BUILT_IN_NS";
    public static final String EXTRA_DISABLE_WEBRTC_AGC_AND_HPF = "org.appspot.apprtc.DISABLE_WEBRTC_GAIN_CONTROL";
    public static final String EXTRA_DISPLAY_HUD = "org.appspot.apprtc.DISPLAY_HUD";
    public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
    public static final String EXTRA_CMDLINE = "org.appspot.apprtc.CMDLINE";
    public static final String EXTRA_RUNTIME = "org.appspot.apprtc.RUNTIME";
    public static final String EXTRA_VIDEO_FILE_AS_CAMERA = "org.appspot.apprtc.VIDEO_FILE_AS_CAMERA";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE = "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH = "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT = "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT";
    public static final String EXTRA_USE_VALUES_FROM_INTENT = "org.appspot.apprtc.USE_VALUES_FROM_INTENT";
    public static final String EXTRA_DATA_CHANNEL_ENABLED = "org.appspot.apprtc.DATA_CHANNEL_ENABLED";
    public static final String EXTRA_ORDERED = "org.appspot.apprtc.ORDERED";
    public static final String EXTRA_MAX_RETRANSMITS_MS = "org.appspot.apprtc.MAX_RETRANSMITS_MS";
    public static final String EXTRA_MAX_RETRANSMITS = "org.appspot.apprtc.MAX_RETRANSMITS";
    public static final String EXTRA_PROTOCOL = "org.appspot.apprtc.PROTOCOL";
    public static final String EXTRA_NEGOTIATED = "org.appspot.apprtc.NEGOTIATED";
    public static final String EXTRA_ID = "org.appspot.apprtc.ID";
    public static final String EXTRA_ENABLE_RTCEVENTLOG = "org.appspot.apprtc.ENABLE_RTCEVENTLOG";

    private static final int LABEL_LEFT_HAND = 0;
    private static final int LABEL_RIGHT_HAND = 1;

    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;
    private static final int STAT_CALLBACK_PERIOD = 1000;

    @Nullable
    public PeerConnectionClient peerConnectionClient;
    @Nullable
    private AppRTCClient appRtcClient;
    @Nullable
    private AppRTCClient.SignalingParameters signalingParameters;
    private final PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    @Nullable
    AppRTCAudioManager audioManager;
    private final List<VideoSink> remoteSinks = new ArrayList<>();
    private final VideoSink localProxyVideoSink = videoFrame -> {
    };

    private long callStartedTimeMs;
    private final boolean loopback;

    public static WebRtcUtils sInstance;

    public static void createInstance(Size previewSize) {
        if (null == sInstance) {
            sInstance = new WebRtcUtils(previewSize);
        }
    }

    private WebRtcUtils(Size previewSize) {
        signalingParameters = null;
        VideoSink remoteProxyRenderer = videoFrame -> LogUtils.v(TAG, "remoteProxyRenderer onFrame");
        remoteSinks.add(remoteProxyRenderer);

        Intent intent = new Intent();

        //String roomId = Integer.toString(handle);

        loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false);
        boolean tracing = intent.getBooleanExtra(EXTRA_TRACING, false);

        PeerConnectionClient.DataChannelParameters dataChannelParameters = null;
        if (intent.getBooleanExtra(EXTRA_DATA_CHANNEL_ENABLED, false)) {
            dataChannelParameters = new PeerConnectionClient.DataChannelParameters(
                    intent.getBooleanExtra(EXTRA_ORDERED, true),
                    intent.getIntExtra(EXTRA_MAX_RETRANSMITS_MS, -1),
                    intent.getIntExtra(EXTRA_MAX_RETRANSMITS, -1), intent.getStringExtra(EXTRA_PROTOCOL),
                    intent.getBooleanExtra(EXTRA_NEGOTIATED, false), intent.getIntExtra(EXTRA_ID, -1));
        }
        peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(
                        true,
                        loopback, tracing, previewSize.getWidth(), previewSize.getHeight(),
                        intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
                        intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0),
                        PeerConnectionClient.VIDEO_CODEC_VP8,
                        intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
                        intent.getBooleanExtra(EXTRA_FLEXFEC_ENABLED, false),
                        intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0),
                        intent.getStringExtra(EXTRA_AUDIOCODEC),
                        intent.getBooleanExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_AECDUMP_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
                        intent.getBooleanExtra(EXTRA_ENABLE_RTCEVENTLOG, false),
                        dataChannelParameters);
    }

    public void startCall(Context context, boolean transportGesture) {
        LogUtils.v(TAG, "start Call");
        // Create connection client. Use DirectRTCClient if room name is an IP otherwise use the standard WebSocketRTCClient.
        if (loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
            LogUtils.i(TAG, "Using WebSocketRTCClient");
            appRtcClient = new WebSocketRTCClient(this);
        } else {
            LogUtils.i(TAG, "Using DirectRTCClient because room name looks like an IP.");
            appRtcClient = new DirectRTCClient(this);
        }
        // Create connection parameters.
        String urlParameters = "";
        AppRTCClient.RoomConnectionParameters roomConnectionParameters = new AppRTCClient.RoomConnectionParameters(SignalingWsUrl, roomId, loopback, urlParameters);
        final EglBase eglBase = EglBase.create();
        peerConnectionClient = new PeerConnectionClient(
                context, eglBase, peerConnectionParameters, WebRtcUtils.this);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        if (loopback) {
            options.networkIgnoreMask = 0;
        }
        peerConnectionClient.createPeerConnectionFactory(options);

        callStartedTimeMs = System.currentTimeMillis();
        // Start room connection.
        LogUtils.d(TAG, "connect to roomUrl:" + roomConnectionParameters.roomUrl);
        appRtcClient.connectToRoom(roomConnectionParameters);
        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(context);
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        // This method will be called each time the number of available audio devices has changed.
        audioManager.start((device, availableDevices) -> {
            //onAudioManagerDevicesChanged(device, availableDevices);
            LogUtils.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", " + "selected: " + device);
            // TODO: add callback handler.
        });
        if (transportGesture) {
            HandResultUtils.getInstance().registerHandListener(context, this::handleLandmarkForWebRTC);
        }
    }

    public void stopCall() {
        LogUtils.d(TAG, "WebRTC stop");
        HandResultUtils.getInstance().unRegisterHandListener();
        if (peerConnectionClient != null) {
            disconnect();
        } else {
            LogUtils.e(TAG, "peerConnectionClient == null");
        }
    }

    @Override
    public void onConnectedToRoom(AppRTCClient.SignalingParameters params) {
        onConnectedToRoomInternal(params);
    }

    private void onConnectedToRoomInternal(final AppRTCClient.SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        signalingParameters = params;
        LogUtils.d(TAG, "Creating peer connection, delay=" + delta + "ms");
//        VideoCapturer videoCapturer = null;
//        if (peerConnectionParameters.videoCallEnabled) {
//            videoCapturer = createVideoCapturer();
//        }
        AppRTCUtils.assertIsTrue(peerConnectionClient != null);
        peerConnectionClient.createPeerConnection(
                localProxyVideoSink, remoteSinks, null, signalingParameters);
        AppRTCUtils.assertIsTrue(signalingParameters != null);
        if (signalingParameters.initiator) {
            LogUtils.d(TAG, "Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createOffer();
        } else {
            if (params.offerSdp != null) {
                peerConnectionClient.setRemoteDescription(params.offerSdp);
                LogUtils.d(TAG, "Creating ANSWER...");
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient.createAnswer();
            }
            if (params.iceCandidates != null) {
                // Add remote ICE candidates from room.
                for (IceCandidate iceCandidate : params.iceCandidates) {
                    peerConnectionClient.addRemoteIceCandidate(iceCandidate);
                }
            }
        }
    }

    @Override
    public void onRemoteDescription(SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        if (peerConnectionClient == null) {
            LogUtils.e(TAG, "Received remote SDP for non-initilized peer connection.");
            return;
        }
        LogUtils.d(TAG, "Received remote " + sdp.type + ", delay=" + delta + "ms");
        peerConnectionClient.setRemoteDescription(sdp);
        if (null != signalingParameters && !signalingParameters.initiator) {
            LogUtils.d(TAG, "Creating ANSWER...");
            // Create answer. Answer SDP will be sent to offering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createAnswer();
        }
    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {
        if (peerConnectionClient == null) {
            LogUtils.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
            return;
        }
        peerConnectionClient.addRemoteIceCandidate(candidate);
    }

    @Override
    public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {
        if (peerConnectionClient == null) {
            LogUtils.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
            return;
        }
        peerConnectionClient.removeRemoteIceCandidates(candidates);
    }

    @Override
    public void onChannelClose() {
        LogUtils.d(TAG, "Remote end hung up; dropping PeerConnection");
        disconnect();
    }

    // Should be called from UI thread
    private void callConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        LogUtils.i(TAG, "Call connected: delay=" + delta + "ms");
        if (peerConnectionClient == null) {
            LogUtils.w(TAG, "Call is connected in closed or error state");
            return;
        }
        // Enable statistics callback.
        peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
        //setSwappedFeeds(false /* isSwappedFeeds */);
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    private void disconnect() {
        if (appRtcClient != null) {
            appRtcClient.disconnectFromRoom();
            appRtcClient = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
    }

    @Override
    public void onChannelError(String description) {
        LogUtils.e(TAG, "onChannelError: " + description);
    }
    ///End AppRTCClient.SignalingEvents
    ///////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //Start PeerConnectionClient.PeerConnectionEvents
    @Override
    public void onLocalDescription(SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        if (appRtcClient != null) {
            LogUtils.d(TAG, "Sending " + sdp.type + ", delay=" + delta + "ms");
            if (signalingParameters != null && signalingParameters.initiator) {
                appRtcClient.sendOfferSdp(sdp);
            } else {
                appRtcClient.sendAnswerSdp(sdp);
            }
            if (null != peerConnectionClient && peerConnectionParameters.videoMaxBitrate > 0) {
                LogUtils.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
                peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
            }
        }
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        if (appRtcClient != null) {
            appRtcClient.sendLocalIceCandidate(candidate);
        }
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        if (appRtcClient != null) {
            appRtcClient.sendLocalIceCandidateRemovals(candidates);
        }
    }

    @Override
    public void onIceConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        LogUtils.d(TAG, "ICE connected, delay=" + delta + "ms");
    }

    @Override
    public void onIceDisconnected() {
        LogUtils.d(TAG, "ICE disconnected");
    }

    @Override
    public void onConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        LogUtils.d(TAG, "DTLS connected, delay=" + delta + "ms");
        callConnected();
    }

    @Override
    public void onDisconnected() {
        LogUtils.d(TAG, "DTLS disconnected");
        disconnect();
    }

    @Override
    public void onPeerConnectionClosed() {
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {

    }

    @Override
    public void onPeerConnectionError(String description) {
        LogUtils.e(TAG, "onPeerConnectionError: " + description);
    }

    private void handleLandmarkForWebRTC(HandsResult result) {
        if (result.multiHandWorldLandmarks().isEmpty() || result.multiHandedness().isEmpty()) {
            return;
        }

        int index = 0;
        for (LandmarkProto.NormalizedLandmarkList landmarkList : result.multiHandLandmarks()) {
            String label = result.multiHandedness().get(index).getLabel();
            LogUtils.d(TAG, label + " hand.");
            LandmarkProto.NormalizedLandmark wrist = landmarkList.getLandmarkList().get(HandLandmark.WRIST);
            //            LogUtils.d(TAG, String.format("wrist coordinates: x=%f m, y=%f m, z=%f m", wrist.getX(), wrist.getY(), wrist.getZ()));
            LandmarkProto.NormalizedLandmark tCmc = landmarkList.getLandmarkList().get(HandLandmark.THUMB_CMC);
            //            LogUtils.d(TAG, String.format("tCmc coordinates: x=%f m, y=%f m, z=%f m", tCmc.getX(), tCmc.getY(), tCmc.getZ()));
            LandmarkProto.NormalizedLandmark tMcp = landmarkList.getLandmarkList().get(HandLandmark.THUMB_MCP);
            //            LogUtils.d(TAG, String.format("tMcp coordinates: x=%f m, y=%f m, z=%f m", tMcp.getX(), tMcp.getY(), tMcp.getZ()));
            LandmarkProto.NormalizedLandmark tIp = landmarkList.getLandmarkList().get(HandLandmark.THUMB_IP);
            //            LogUtils.d(TAG, String.format("tIp coordinates: x=%f m, y=%f m, z=%f m", tIp.getX(), tIp.getY(), tIp.getZ()));
            LandmarkProto.NormalizedLandmark tTip = landmarkList.getLandmarkList().get(HandLandmark.THUMB_TIP);
            //            LogUtils.d(TAG, String.format("tTip coordinates: x=%f m, y=%f m, z=%f m", tTip.getX(), tTip.getY(), tTip.getZ()));
            LandmarkProto.NormalizedLandmark iMcp = landmarkList.getLandmarkList().get(HandLandmark.INDEX_FINGER_MCP);
            //            LogUtils.d(TAG, String.format("iMcp coordinates: x=%f m, y=%f m, z=%f m", iMcp.getX(), iMcp.getY(), iMcp.getZ()));
            LandmarkProto.NormalizedLandmark iPip = landmarkList.getLandmarkList().get(HandLandmark.INDEX_FINGER_PIP);
            //            LogUtils.d(TAG, String.format("iPip coordinates: x=%f m, y=%f m, z=%f m", iPip.getX(), iPip.getY(), iPip.getZ()));
            LandmarkProto.NormalizedLandmark iDip = landmarkList.getLandmarkList().get(HandLandmark.INDEX_FINGER_DIP);
            //            LogUtils.d(TAG, String.format("iDip coordinates: x=%f m, y=%f m, z=%f m", iDip.getX(), iDip.getY(), iDip.getZ()));
            LandmarkProto.NormalizedLandmark iTip = landmarkList.getLandmarkList().get(HandLandmark.INDEX_FINGER_TIP);
            //            LogUtils.d(TAG, String.format("iTip coordinates: x=%f m, y=%f m, z=%f m", iTip.getX(), iTip.getY(), iTip.getZ()));
            LandmarkProto.NormalizedLandmark mMcp = landmarkList.getLandmarkList().get(HandLandmark.MIDDLE_FINGER_MCP);
            //            LogUtils.d(TAG, String.format("mMcp coordinates: x=%f m, y=%f m, z=%f m", mMcp.getX(), mMcp.getY(), mMcp.getZ()));
            LandmarkProto.NormalizedLandmark mPip = landmarkList.getLandmarkList().get(HandLandmark.MIDDLE_FINGER_PIP);
            //            LogUtils.d(TAG, String.format("mPip coordinates: x=%f m, y=%f m, z=%f m", mPip.getX(), mPip.getY(), mPip.getZ()));
            LandmarkProto.NormalizedLandmark mDip = landmarkList.getLandmarkList().get(HandLandmark.MIDDLE_FINGER_DIP);
            //            LogUtils.d(TAG, String.format("mDip coordinates: x=%f m, y=%f m, z=%f m", mDip.getX(), mDip.getY(), mDip.getZ()));
            LandmarkProto.NormalizedLandmark mTip = landmarkList.getLandmarkList().get(HandLandmark.MIDDLE_FINGER_TIP);
            //            LogUtils.d(TAG, String.format("mTip coordinates: x=%f m, y=%f m, z=%f m", mTip.getX(), mTip.getY(), mTip.getZ()));
            LandmarkProto.NormalizedLandmark rMcp = landmarkList.getLandmarkList().get(HandLandmark.RING_FINGER_MCP);
            //            LogUtils.d(TAG, String.format("rMcp coordinates: x=%f m, y=%f m, z=%f m", rMcp.getX(), rMcp.getY(), rMcp.getZ()));
            LandmarkProto.NormalizedLandmark rPip = landmarkList.getLandmarkList().get(HandLandmark.RING_FINGER_PIP);
            //            LogUtils.d(TAG, String.format("rPip coordinates: x=%f m, y=%f m, z=%f m", rPip.getX(), rPip.getY(), rPip.getZ()));
            LandmarkProto.NormalizedLandmark rDip = landmarkList.getLandmarkList().get(HandLandmark.RING_FINGER_DIP);
            //            LogUtils.d(TAG, String.format("rDip coordinates: x=%f m, y=%f m, z=%f m", rDip.getX(), rDip.getY(), rDip.getZ()));
            LandmarkProto.NormalizedLandmark rTip = landmarkList.getLandmarkList().get(HandLandmark.RING_FINGER_TIP);
            //            LogUtils.d(TAG, String.format("rTip coordinates: x=%f m, y=%f m, z=%f m", rTip.getX(), rTip.getY(), rTip.getZ()));
            LandmarkProto.NormalizedLandmark pMcp = landmarkList.getLandmarkList().get(HandLandmark.PINKY_MCP);
            //            LogUtils.d(TAG, String.format("pMcp coordinates: x=%f m, y=%f m, z=%f m", pMcp.getX(), pMcp.getY(), pMcp.getZ()));
            LandmarkProto.NormalizedLandmark pPip = landmarkList.getLandmarkList().get(HandLandmark.PINKY_PIP);
            //            LogUtils.d(TAG, String.format("pPip coordinates: x=%f m, y=%f m, z=%f m", pPip.getX(), pPip.getY(), pPip.getZ()));
            LandmarkProto.NormalizedLandmark pDip = landmarkList.getLandmarkList().get(HandLandmark.PINKY_DIP);
            //            LogUtils.d(TAG, String.format("pDip coordinates: x=%f m, y=%f m, z=%f m", pDip.getX(), pDip.getY(), pDip.getZ()));
            LandmarkProto.NormalizedLandmark pTip = landmarkList.getLandmarkList().get(HandLandmark.PINKY_TIP);
            //            LogUtils.d(TAG, String.format("pTip coordinates: x=%f m, y=%f m, z=%f m\n\n", pTip.getX(), pTip.getY(), pTip.getZ()));

            index++;

            byte[] temp = new byte[]{
                    (byte) ((label.equals("Left") ? LABEL_LEFT_HAND : LABEL_RIGHT_HAND)), (byte) (wrist.getX() * 100), (byte) (tCmc.getX() * 100),
                    (byte) (tMcp.getX() * 100), (byte) (tIp.getX() * 100), (byte) (tTip.getX() * 100), (byte) (iMcp.getX() * 100), (byte) (iPip.getX() * 100),
                    (byte) (iDip.getX() * 100), (byte) (iTip.getX() * 100), (byte) (mMcp.getX() * 100), (byte) (mPip.getX() * 100), (byte) (mDip.getX() * 100),
                    (byte) (mTip.getX() * 100), (byte) (rMcp.getX() * 100), (byte) (rPip.getX() * 100), (byte) (rDip.getX() * 100), (byte) (rTip.getX() * 100),
                    (byte) (pMcp.getX() * 100), (byte) (pPip.getX() * 100), (byte) (pDip.getX() * 100), (byte) (pTip.getX() * 100), (byte) (wrist.getY() * 100),
                    (byte) (tCmc.getY() * 100), (byte) (tMcp.getY() * 100), (byte) (tIp.getY() * 100), (byte) (tTip.getY() * 100), (byte) (iMcp.getY() * 100),
                    (byte) (iPip.getY() * 100), (byte) (iDip.getY() * 100), (byte) (iTip.getY() * 100), (byte) (mMcp.getY() * 100), (byte) (mPip.getY() * 100),
                    (byte) (mDip.getY() * 100), (byte) (mTip.getY() * 100), (byte) (rMcp.getY() * 100), (byte) (rPip.getY() * 100), (byte) (rDip.getY() * 100),
                    (byte) (rTip.getY() * 100), (byte) (pMcp.getY() * 100), (byte) (pPip.getY() * 100), (byte) (pDip.getY() * 100), (byte) (pTip.getY() * 100),
                    (byte) (wrist.getZ() * 100), (byte) (tCmc.getZ() * 100), (byte) (tMcp.getZ() * 100), (byte) (tIp.getZ() * 100), (byte) (tTip.getZ() * 100),
                    (byte) (iMcp.getZ() * 100), (byte) (iPip.getZ() * 100), (byte) (iDip.getZ() * 100), (byte) (iTip.getZ() * 100), (byte) (mMcp.getZ() * 100),
                    (byte) (mPip.getZ() * 100), (byte) (mDip.getZ() * 100), (byte) (mTip.getZ() * 100), (byte) (rMcp.getZ() * 100), (byte) (rPip.getZ() * 100),
                    (byte) (rDip.getZ() * 100), (byte) (rTip.getZ() * 100), (byte) (pMcp.getZ() * 100), (byte) (pPip.getZ() * 100), (byte) (pDip.getZ() * 100),
                    (byte) (pTip.getZ() * 100)
            };

            if (null != peerConnectionClient) {
                peerConnectionClient.sendData(ByteBuffer.wrap(temp));
            }
        }
    }
}
