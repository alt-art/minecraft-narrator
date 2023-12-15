package com.mitsuaky.stanleyparable;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = "stanleyparable", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class EventSubscriber {
    private static final Logger LOGGER = LogManager.getLogger(EventSubscriber.class);

    public enum Event {
        ITEM_CRAFTED("item_crafted"),
        BLOCK_BROKEN("block_broken"),
        PLAYER_DEATH("player_death"),
        ADVANCEMENT("advancement");

        private final String value;

        Event(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum Action {
        IGNORE("ignore"),
        CANCEL_EVENT("cancel_event"),
        SEND_CHAT("send_chat");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        public static Action fromString(String text) {
            for (Action b : Action.values()) {
                if (b.value.equalsIgnoreCase(text)) {
                    return b;
                }
            }
            return null;
        }
    }

        public static String getAsID(Item item) {
            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            return id.toString();
        }

        @SubscribeEvent
        public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
            LOGGER.debug("ItemCraftedEvent triggered");
            if (event.getEntity() == null || event.getCrafting().isEmpty()) {
                LOGGER.debug("ItemCraftedEvent triggered without valid entity or crafting item");
                return;
            }

            String item = getAsID(event.getCrafting().getItem());
            int amount = event.getCrafting().getCount();
            Player player = event.getEntity();
            ItemCraftedEventData eventData = new ItemCraftedEventData(item, amount);
            IncomingEvent<ItemCraftedEventData> incomingEvent = new IncomingEvent<>(Event.ITEM_CRAFTED, eventData);
            processApiResponse(player, event, incomingEvent.toJson());
        }

        @SubscribeEvent
        public static void onBlockBreak(BlockEvent.BreakEvent event) {
            LOGGER.debug("BlockBreakEvent triggered");
            if (event.getPlayer() == null || event.getState().isAir()) {
                LOGGER.debug("BlockBreakEvent triggered without valid player or block state");
                return;
            }

            String tool = getAsID(event.getPlayer().getMainHandItem().getItem());
            Player player = event.getPlayer();
            BlockBrokenEventData eventData = new BlockBrokenEventData(event.getState().getBlock().getDescriptionId(), tool);
            IncomingEvent<BlockBrokenEventData> incomingEvent = new IncomingEvent<>(Event.BLOCK_BROKEN, eventData);
            processApiResponse(player, event, incomingEvent.toJson());
        }

        @SubscribeEvent
        public static void onPlayerDeath(LivingDeathEvent event) {
            LOGGER.debug("LivingDeathEvent triggered");
            if (event.getEntity() == null || !(event.getEntity() instanceof Player player)) {
                LOGGER.debug("LivingDeathEvent triggered but is not a player");
                return;
            }

            String deathCause = event.getSource().getMsgId();
            PlayerDeathEventData eventData = new PlayerDeathEventData(deathCause);
            IncomingEvent<PlayerDeathEventData> incomingEvent = new IncomingEvent<>(Event.PLAYER_DEATH, eventData);
            processApiResponse(player, event, incomingEvent.toJson());
        }

        @SubscribeEvent
        public static void onAchievement(AdvancementEvent event) {
            LOGGER.debug("AdvancementEvent triggered");
            if (event.getEntity() == null) {
                LOGGER.debug("AdvancementEvent triggered without valid player");
                return;
            }

            AdvancementEventData eventData = new AdvancementEventData(event.getAdvancement().toString());
            IncomingEvent<AdvancementEventData> incomingEvent = new IncomingEvent<>(Event.ADVANCEMENT, eventData);
            processApiResponse(event.getEntity(), event, incomingEvent.toJson());
        }

        private static void processApiResponse(Player player, net.minecraftforge.eventbus.api.Event event, JsonObject jsonEvent) {
            JsonObject response = APICommunicator.sendEvent(jsonEvent);
            if (response != null) {
                handleResponse(player, event, response);
            } else {
                LOGGER.error("Received null response from API");
            }
        }

        private static void handleResponse(Player player, net.minecraftforge.eventbus.api.Event event, JsonObject response) {
            Action action = Action.fromString(response.get("action").getAsString());
            if (action == null) {
                LOGGER.error("Received invalid action from API");
                return;
            }
            switch (action) {
                case IGNORE:
                    LOGGER.debug("Ignoring event: " + event.getClass().getSimpleName());
                    break;
                case CANCEL_EVENT:
                    LOGGER.debug("Cancelling event: " + event.getClass().getSimpleName());
                    if (event.isCancelable()){
                        event.setCanceled(true);
                    }
                    break;
                case SEND_CHAT:
                    LOGGER.debug("Sending chat message: " + response.getAsJsonObject("data").get("text").getAsString());
                    String chatMessage = response.getAsJsonObject("data").get("text").getAsString();
                    player.sendSystemMessage(Component.literal(chatMessage));
                    break;
                default:
                    LOGGER.warn("Unhandled action: " + action);
            }
        }
    }

    class BaseEventData {
        JsonObject toJson() {
            return new JsonObject();
        }
    }

    class ItemCraftedEventData extends BaseEventData {
        String item;
        int amount;

        ItemCraftedEventData(String item, int amount) {
            this.item = item;
            this.amount = amount;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("item", item);
            json.addProperty("amount", amount);
            return json;
        }
    }

    class BlockBrokenEventData extends BaseEventData {
        String block;
        String tool;

        BlockBrokenEventData(String block, String tool) {
            this.block = block;
            this.tool = tool;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("block", block);
            json.addProperty("tool", tool);
            return json;
        }
    }

    class PlayerDeathEventData extends BaseEventData {
        String cause;

        PlayerDeathEventData(String cause) {
            this.cause = cause;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("cause", cause);
            return json;
        }
    }

    class AdvancementEventData extends BaseEventData {
        String advancement;

        AdvancementEventData(String advancement) {
            this.advancement = advancement;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("advancement", advancement);
            return json;
        }
    }

    class IncomingEvent<T extends BaseEventData> {
        String event;
        T data;

        IncomingEvent(EventSubscriber.Event event, T data) {
            this.event = event.getValue();
            this.data = data;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("event", event);
            json.add("data", data.toJson());
            return json;
        }
    }
