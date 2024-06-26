package com.mitsuaky.stanleyparable.client;

import com.mitsuaky.stanleyparable.common.events.EventType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientEventHandler {
    private static final Logger LOGGER = LogManager.getLogger(ClientEventHandler.class);

    private static final WebSocketClient wsClient = WebSocketClient.getInstance();

    public static void handle(EventType event, String msg) {
        LOGGER.debug("Sending {}:{} to backend", event.getValue(), msg);
        wsClient.sendEvent(event, msg);
    }
}
