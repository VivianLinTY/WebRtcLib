package com.appwebrtc;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.Nullable;


import com.appwebrtc.util.AsyncHttpURLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public class WebSocketRTCClient implements AppRTCClient, WebSocketChannelClient.WebSocketChannelEvents {
    private static final String TAG = "WebSocketRTCClient";
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";

    private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }
    private enum MessageType { MESSAGE, LEAVE }

    private final Handler handler;
    private boolean initiator;
    private SignalingEvents events;
    private WebSocketChannelClient wsClient;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private String messageUrl;
    private String leaveUrl;

    public WebSocketRTCClient(SignalingEvents events) {
        this.events = events;
        roomState = ConnectionState.NEW;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Start AppRTCClient.Interface
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }
    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
        String connectionUrl = getConnectionUrl(connectionParameters);
        Log.d(TAG, "Connect to WWS: " + connectionUrl);
        roomState = ConnectionState.NEW;
        wsClient = new WebSocketChannelClient(handler, this);

        RoomParametersFetcher.RoomParametersFetcherEvents callbacks =
                new RoomParametersFetcher.RoomParametersFetcherEvents() {
            @Override
            public void onSignalingParametersReady(final SignalingParameters params) {
                WebSocketRTCClient.this.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        WebSocketRTCClient.this.signalingParametersReady(params);
                    }
                });
            }
            @Override
            public void onSignalingParametersError(String description) {
                WebSocketRTCClient.this.reportError(description);
            }
        };
        new RoomParametersFetcher(connectionUrl, null, connectionParameters.roomId, callbacks).makeRequest();
    }
    // Callback issued when room parameters are extracted. Runs on local looper thread.
    private void signalingParametersReady(final SignalingParameters signalingParameters) {
        Log.d(TAG, "Room connection completed.");
        if (connectionParameters.loopback
                && (!signalingParameters.initiator || signalingParameters.offerSdp != null)) {
            reportError("Loopback room is busy.");
            return;
        }
        if (!connectionParameters.loopback && !signalingParameters.initiator
                && signalingParameters.offerSdp == null) {
            Log.w(TAG, "No offer SDP in room response.");
        }
        initiator = signalingParameters.initiator;
//        messageUrl = getMessageUrl(connectionParameters, signalingParameters);
//        leaveUrl = getLeaveUrl(connectionParameters, signalingParameters);
//        Log.d(TAG, "Message URL: " + messageUrl);
//        Log.d(TAG, "Leave URL: " + leaveUrl);
        roomState = ConnectionState.CONNECTED;
        // Fire connection and signaling parameters events.
        events.onConnectedToRoom(signalingParameters);
        // Connect and register WebSocket client.
        wsClient.connect(signalingParameters.wssUrl, signalingParameters.wssPostUrl);
        //wsClient.register(connectionParameters.roomId, signalingParameters.clientId);
    }

    @Override
    public void sendOfferSdp(SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "offer");
                    message.put("from", connectionParameters.roomId);
                    message.put("to", "");
                    JSONObject data = new JSONObject();
                    data.put("sdp", sdp.description);
                    data.put("connectionId", connectionParameters.roomId);
                    message.put("data", data);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
//                sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
//                if (connectionParameters.loopback) {
//                    // In loopback mode rename this offer to answer and route it back.
//                    SessionDescription sdpAnswer = new SessionDescription(
//                            SessionDescription.Type.fromCanonicalForm("answer"), sdp.description);
//                    events.onRemoteDescription(sdpAnswer);
//                }
            }
        });
    }

    @Override
    public void sendAnswerSdp(SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (connectionParameters.loopback) {
                    Log.e(TAG, "Sending answer in loopback mode.");
                    return;
                }
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                wsClient.send(json.toString());
            }
        });
    }

    @Override
    public void sendLocalIceCandidate(IceCandidate candidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "candidate");
                jsonPut(json, "from", "");
                jsonPut(json, "to", "");
                JSONObject data = new JSONObject();
                jsonPut(data,"sdpMLineIndex", candidate.sdpMLineIndex);
                jsonPut(data,"sdpMid", candidate.sdpMid);
                jsonPut(data,"candidate", candidate.sdp);
                jsonPut(json,"data", data);

                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate in non connected state.");
                        return;
                    }
                    //sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidate(candidate);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    wsClient.send(json.toString());
                }
            }
        });
    }

    @Override
    public void sendLocalIceCandidateRemovals(IceCandidate[] candidates) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate removals in non connected state.");
                        return;
                    }
                    sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidatesRemoved(candidates);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    wsClient.send(json.toString());
                }
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
                handler.getLooper().quit();
            }
        });
    }
    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            sendPostMessage(MessageType.LEAVE, leaveUrl, null);
        }
        roomState = ConnectionState.CLOSED;
        if (wsClient != null) {
            wsClient.disconnect(true);
        }
    }
    //End AppRTCClient.Interface
    ////////////////////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //Start WebSocketChannelClient.WebSocketChannelEvents
    @Override
    public void onWebSocketMessage(String message) {
//        if (wsClient.getState() != WebSocketChannelClient.WebSocketConnectionState.REGISTERED) {
//            Log.e(TAG, "Got WebSocket message in non registered state.");
//            return;
//        }
        try{
//            JSONObject json = new JSONObject(message);
//            String msgText = json.getString("msg");
//            String errorText = json.optString("error");
//            if (msgText.length() > 0) {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type");
            if (type.equals("candidate")) {
                String data = json.getString("data");
                JSONObject jdata  = new JSONObject(data);
                events.onRemoteIceCandidate(toJavaCandidate(jdata));
            }else if (type.equals("remove-candidates")) {
                JSONArray candidateArray = json.getJSONArray("candidates");
                IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                for (int i = 0; i < candidateArray.length(); ++i) {
                    candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                }
                events.onRemoteIceCandidatesRemoved(candidates);
            } else if (type.equals("answer")) {
                if (initiator) {
                    JSONObject data = new JSONObject(json.getString("data"));
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type), data.getString("sdp"));
                    events.onRemoteDescription(sdp);
                    initiator = false;
                } else {
                    reportError("Received answer for call initiator: " + message);
                }
            } else if (type.equals("offer")) {
                if (!initiator) {
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                    events.onRemoteDescription(sdp);
                } else {
                    reportError("Received offer for call receiver: " + message);
                }
            } else if (type.equals("bye")) {
                events.onChannelClose();
            } else {
                reportError("Unexpected WebSocket message: " + message);
            }
//            } else {
//                if (errorText.length() > 0) {
//                    reportError("WebSocket error message: " + errorText);
//                } else {
//                    reportError("Unexpected WebSocket message: " + message);
//                }
//            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }
    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }
    //End WebSocketChannelClient.WebSocketChannelEvents
    ////////////////////////////////////////////////////////////////////////////////////////////////


    // Send SDP or ICE candidate to a room server.
    private void sendPostMessage(final MessageType messageType, final String url, final String message)
    {
        if(true) return;
        String logInfo = url;
        if (message != null) {
            logInfo += ". Message: " + message;
        }
        Log.d(TAG, "C->GAE: " + logInfo);
        AsyncHttpURLConnection post = new AsyncHttpURLConnection("POST", url, message,
                new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        reportError("GAE POST error: " + errorMessage);
                    }
                    @Override
                    public void onHttpComplete(String response) {
                        if (messageType == MessageType.MESSAGE) {
                            try {
                                JSONObject roomJson = new JSONObject(response);
                                String result = roomJson.getString("result");
                                if (!result.equals("SUCCESS")) {
                                    reportError("GAE POST error: " + result);
                                }
                            } catch (JSONException e) {
                                reportError("GAE POST JSON error: " + e.toString());
                            }
                        }
                    }
                });
        post.send();
    }
    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }
    // Converts a JSON candidate to a Java object.
    IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("candidate"));
    }
    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    // Helper functions to get connection, post message and leave message URLs
    private String getConnectionUrl(RoomConnectionParameters connectionParameters) {
//        return connectionParameters.roomUrl + "/" + ROOM_JOIN + "/" + connectionParameters.roomId
//                + getQueryString(connectionParameters);
        return connectionParameters.roomUrl;
    }
    private String getMessageUrl(
            RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_MESSAGE + "/" + connectionParameters.roomId
                + "/" + signalingParameters.clientId + getQueryString(connectionParameters);
    }
    private String getLeaveUrl(
            RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_LEAVE + "/" + connectionParameters.roomId + "/"
                + signalingParameters.clientId + getQueryString(connectionParameters);
    }
    private String getQueryString(RoomConnectionParameters connectionParameters) {
        if (connectionParameters.urlParameters != null) {
            return "?" + connectionParameters.urlParameters;
        } else {
            return "";
        }
    }
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    private void sendMessage(Object message) {
        //socket.emit("message", message);
        Log.v(TAG, "C->WSS " + message.toString());
        wsClient.send(message.toString());
    }

}
