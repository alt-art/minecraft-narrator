package com.mitsuaky.stanleyparable.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mitsuaky.stanleyparable.StanleyParableMod;
import com.mitsuaky.stanleyparable.common.events.GameEventType;
import com.mitsuaky.stanleyparable.common.events.EventType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public class WebSocketClient {
    private static WebSocketClient instance;
    private static final Logger LOGGER = LogManager.getLogger(WebSocketClient.class);
    private static final String SERVER_URI = "ws://127.0.0.1:5000/ws";
    private WebSocket webSocket;
    private final List<EventListener> eventsListeners = new ArrayList<>();
    private Function<Long, Void> onPongCallback;

    public WebSocketClient() {
        instance = this;
        connect();
    }

    public void addEventListener(String event, Function<JsonObject, Void> callback) {
        eventsListeners.removeIf(eventListener -> eventListener.event.equals(event));
        eventsListeners.add(new EventListener(event, callback));
    }

    public void setOnPong(Function<Long, Void> callback) {
        onPongCallback = callback;
    }

    public void sendEvent(EventType event) {
        sendEvent(event, "");
    }

    public void sendEvent(EventType event, String data) {
        if (StanleyParableMod.adventureMode && event instanceof GameEventType) {
            return;
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("event", event.getValue());
        jsonObject.addProperty("data", data);
        try {
            webSocket.sendText(jsonObject.toString(), true);
        } catch (Exception ex) {
            LOGGER.error("Could not send event to websocket: {}", ex.getMessage(), ex);
        }
    }

    public CompletableFuture<WebSocket> sendPing() {
        return webSocket.sendPing(ByteBuffer.wrap(BigInteger.valueOf(System.currentTimeMillis()).toByteArray()));
    }

    public void connect() {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            WebSocket.Builder webSocketBuilder = httpClient.newWebSocketBuilder();
            webSocket = webSocketBuilder.buildAsync(URI.create(WebSocketClient.SERVER_URI), new WebSocketListener()).join();
        } catch (Exception ex) {
            ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.schedule(this::connect, 5, TimeUnit.SECONDS);
        }
    }

    public static WebSocketClient getInstance() {
        if (instance == null) {
            instance = new WebSocketClient();
        }
        return instance;
    }

    private static class EventListener {
        public String event;
        public Function<JsonObject, Void> listener;

        public EventListener(String event, Function<JsonObject, Void> listener) {
            this.event = event;
            this.listener = listener;
        }
    }

    private static class WebSocketListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            LOGGER.info("WebSocket opened");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            LOGGER.info("Received websocket message: {}", data);

            try {
                JsonObject jsonObject = JsonParser.parseString(data.toString()).getAsJsonObject();
                String event = JsonParser.parseString(data.toString()).getAsJsonObject().get("action").getAsString();
                for (EventListener eventListener : WebSocketClient.getInstance().eventsListeners) {
                    if (eventListener.event.equals(event)) {
                        eventListener.listener.apply(jsonObject);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Could not parse websocket message: {}", ex.getMessage(), ex);
            }

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.schedule(() -> WebSocketClient.getInstance().connect(), 5, TimeUnit.SECONDS);
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.schedule(() -> WebSocketClient.getInstance().connect(), 5, TimeUnit.SECONDS);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.sendPong(message);
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            Function<Long, Void> callback = WebSocketClient.getInstance().onPongCallback;
            if (callback != null) {
                long time = new BigInteger(message.array()).longValue();
                callback.apply(time);
            }
            webSocket.request(1);
            return null;
        }
    }
}
