package com.appwebrtc;

import static org.webrtc.SessionDescription.Type.ANSWER;
import static org.webrtc.SessionDescription.Type.OFFER;
import static java.lang.Thread.sleep;

import android.util.Log;


import com.appwebrtc.util.AsyncHttpURLConnection;
import com.compal.utils.WebRtcUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

public class RoomParametersFetcher {
    private static final String TAG = "RoomRTCClient";
    private static final int TURN_HTTP_TIMEOUT_MS = 5000;
    private final RoomParametersFetcherEvents events;
    private final String roomUrl;
    private final String roomMessage;
    private WebSocketObserver wsObserver;
    private WebSocketConnection ws;
    public String roomID = "";

    // Room parameters fetcher callbacks.
    public interface RoomParametersFetcherEvents {
        /**
         * Callback fired once the room's signaling parameters
         * SignalingParameters are extracted.
         */
        void onSignalingParametersReady(final AppRTCClient.SignalingParameters params);
        /**
         * Callback for room parameters extraction error.
         */
        void onSignalingParametersError(final String description);
    }


    public RoomParametersFetcher(String roomUrl, String roomMessage,String roomID,
                                 final RoomParametersFetcherEvents events) {
        this.roomUrl = roomUrl;
        this.roomMessage = roomMessage;
        this.events = events;
        this.roomID = roomID;
    }


    public void makeRequest() {
        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("GET", "http://" + WebRtcUtils.ip + ":80", roomMessage,
                        new AsyncHttpURLConnection.AsyncHttpEvents() {
                            @Override
                            public void onHttpError(String errorMessage) {
                                Log.e(TAG, "Room connection error: " + errorMessage);
                                events.onSignalingParametersError(errorMessage);
                            }
                            @Override
                            public void onHttpComplete(String response) {
                                roomHttpResponseParse(response);
                            }
                        });
        httpConnection.send();
    }


    private void roomHttpResponseParse(String response)
    {
        Log.d(TAG, "Room response: " + response);
        try{
            List<IceCandidate> iceCandidates = null;
            SessionDescription offerSdp = null;
//            JSONObject roomJson = new JSONObject(response);
//
//            String result = roomJson.getString("result");
//            if (!result.equals("SUCCESS")) {
//                events.onSignalingParametersError("Room response error: " + result);
//                return;
//            }
//            response = roomJson.getString("params");
//            roomJson = new JSONObject(response);
//            String roomId = roomJson.getString("room_id");
//            String clientId = roomJson.getString("client_id");
//            String wssUrl = roomJson.getString("wss_url");
//            String wssPostUrl = roomJson.getString("wss_post_url");
//            boolean initiator = (roomJson.getBoolean("is_initiator"));
//            if (!initiator) {
//                iceCandidates = new ArrayList<>();
//                String messagesString = roomJson.getString("messages");
//                JSONArray messages = new JSONArray(messagesString);
//                for (int i = 0; i < messages.length(); ++i) {
//                    String messageString = messages.getString(i);
//                    JSONObject message = new JSONObject(messageString);
//                    String messageType = message.getString("type");
//                    Log.d(TAG, "GAE->C #" + i + " : " + messageString);
//                    if (messageType.equals("offer")) {
//                        offerSdp = new SessionDescription(
//                                SessionDescription.Type.fromCanonicalForm(messageType), message.getString("sdp"));
//                    } else if (messageType.equals("candidate")) {
//                        IceCandidate candidate = new IceCandidate(
//                                message.getString("id"),
//                                message.getInt("label"),
//                                message.getString("candidate"));
//                        iceCandidates.add(candidate);
//                    } else {
//                        Log.e(TAG, "Unknown message: " + messageString);
//                    }
//                }
//            }
//            Log.d(TAG, "RoomId: " + roomId + ". ClientId: " + clientId);
//            Log.d(TAG, "Initiator: " + initiator);
//            Log.d(TAG, "WSS url: " + wssUrl);
//            Log.d(TAG, "WSS POST url: " + wssPostUrl);

            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            String URL = "stun:stun.l.google.com:19302";
            iceServers.add(new PeerConnection.IceServer(URL));
            boolean isTurnPresent = false;
            for (PeerConnection.IceServer server : iceServers) {
                Log.d(TAG, "IceServer: " + server);
                for (String uri : server.urls) {
                    if (uri.startsWith("turn:")) {
                        isTurnPresent = true;
                        break;
                    }
                }
            }
            // Request TURN servers.
//            if (!isTurnPresent && !roomJson.optString("ice_server_url").isEmpty()) {
//                List<PeerConnection.IceServer> turnServers =
//                        requestTurnServers(roomJson.getString("ice_server_url"));
//                for (PeerConnection.IceServer turnServer : turnServers) {
//                    Log.d(TAG, "TurnServer: " + turnServer);
//                    iceServers.add(turnServer);
//                }
//            }
            AppRTCClient.SignalingParameters params = new AppRTCClient.SignalingParameters(
                    iceServers, true, roomID, roomUrl, "", null, iceCandidates);
            events.onSignalingParametersReady(params);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    // Requests & returns a TURN ICE Server based on a request URL.  Must be run off the main thread!
    private List<PeerConnection.IceServer> requestTurnServers(String url) throws IOException, JSONException
    {
        List<PeerConnection.IceServer> turnServers = new ArrayList<>();
        Log.d(TAG, "Request TURN from: " + url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("REFERER", "https://appr.tc");
        connection.setConnectTimeout(TURN_HTTP_TIMEOUT_MS);
        connection.setReadTimeout(TURN_HTTP_TIMEOUT_MS);
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Non-200 response when requesting TURN server from " + url + " : "
                    + connection.getHeaderField(null));
        }
        InputStream responseStream = connection.getInputStream();
        String response = drainStream(responseStream);
        connection.disconnect();
        Log.d(TAG, "TURN response: " + response);
        JSONObject responseJSON = new JSONObject(response);
        JSONArray iceServers = responseJSON.getJSONArray("iceServers");
        for (int i = 0; i < iceServers.length(); ++i) {
            JSONObject server = iceServers.getJSONObject(i);
            JSONArray turnUrls = server.getJSONArray("urls");
            String username = server.has("username") ? server.getString("username") : "";
            String credential = server.has("credential") ? server.getString("credential") : "";
            for (int j = 0; j < turnUrls.length(); j++) {
                String turnUrl = turnUrls.getString(j);
                PeerConnection.IceServer turnServer = PeerConnection.IceServer.builder(turnUrl)
                                .setUsername(username).setPassword(credential).createIceServer();
                turnServers.add(turnServer);
            }
        }
        return turnServers;
    }
    // Return the contents of an InputStream as a String.
    private static String drainStream(InputStream in) {
        Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection configuration string.
    private List<PeerConnection.IceServer> iceServersFromPCConfigJSON(String pcConfig) throws JSONException
    {
        JSONObject json = new JSONObject(pcConfig);
        JSONArray servers = json.getJSONArray("iceServers");
        List<PeerConnection.IceServer> ret = new ArrayList<>();
        for (int i = 0; i < servers.length(); ++i) {
            JSONObject server = servers.getJSONObject(i);
            String url = server.getString("urls");
            String credential = server.has("credential") ? server.getString("credential") : "";
            PeerConnection.IceServer turnServer =
                    PeerConnection.IceServer.builder(url).setPassword(credential).createIceServer();
            ret.add(turnServer);
        }
        return ret;
    }

    private class WebSocketObserver implements WebSocket.WebSocketConnectionObserver {

        @Override
        public void onOpen() {
            Log.d(TAG, "WebSocket connection opened to: " + roomUrl);
//            initializePeerConnections();
//            startStreamingVideo();
//            doCall();
//            roomID = "12345";
//            clientID = "";
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    // Check if we have pending register request.
//                    if (roomID != null && clientID != null) {
//                        register(roomID, clientID);
//                    }
//                }
//            });
        }

        @Override
        public void onClose(WebSocketCloseNotification code, String reason) {
            Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason);

//            synchronized (closeEventLock) {
//                closeEvent = true;
//                closeEventLock.notify();
//            }
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    if (state != WebSocketConnectionState.CLOSED) {
//                        state = WebSocketConnectionState.CLOSED;
//                        events.onWebSocketClose();
//                    }
//                }
//            });
        }

        @Override
        public void onTextMessage(String payload) {
            try {
                sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "WSS->C: " + payload);
            final String message = payload;
            try {
                JSONObject jsonObject = new JSONObject(payload);
                final String type = jsonObject.getString("type");
                if(type.equals("offer")) {
//                    initializePeerConnections();
//                    startStreamingVideo();
//                    JSONObject data = new JSONObject(jsonObject.getString("data"));
//                    peerConnection.setRemoteDescription(new SimpleSdpObserver() {
//                        @Override
//                        public void onSetFailure(String s) {
//                            super.onSetFailure(s);
//                            Log.e(TAG, s);
//                        }
//                    }, new SessionDescription(OFFER, data.getString("sdp")));
//                    doAnswer();
                } else if(type.equals("candidate")) {
                    JSONObject data = new JSONObject(jsonObject.getString("data"));
                    Log.d(TAG, "connectToSignallingServer: receiving candidates");
                    IceCandidate candidate = new IceCandidate(data.getString("sdpMid"), data.getInt("sdpMLineIndex"), data.getString("candidate"));
                    //peerConnection.addIceCandidate(candidate);
                } else if(type.equals("answer")) {
                    JSONObject data = new JSONObject(jsonObject.getString("data"));
//                    peerConnection.setRemoteDescription(new SimpleSdpObserver() {
//                        @Override
//                        public void onSetFailure(String s) {
//                            super.onSetFailure(s);
//                            Log.e(TAG, s);
//                        }
//                    }, new SessionDescription(ANSWER, data.getString("sdp")));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    if (state == WebSocketConnectionState.CONNECTED
//                            || state == WebSocketConnectionState.REGISTERED) {
//                        events.onWebSocketMessage(message);
//                    }
//                }
//            });
        }

        @Override
        public void onRawTextMessage(byte[] bytes) { }
        @Override
        public void onBinaryMessage(byte[] bytes) { }
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                if (state != WebSocketConnectionState.ERROR) {
//                    state = WebSocketConnectionState.ERROR;
//                    events.onWebSocketError(errorMessage);
//                }
//            }
//        });
    }


}
