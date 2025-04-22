package com.chanceman;

import com.chanceman.filters.ItemInfo;
import com.chanceman.lifecycle.implementations.EventUser;
import com.chanceman.lifecycle.implementations.LifeCycle;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the roll animation for unlocking items.
 * It processes roll requests asynchronously and handles the roll animation through the overlay.
 */
@Singleton
public class RollAnimationManager extends EventUser
{
    private final Client client;
    private final ItemManager itemManager;
    private final ItemInfo itemInfo;
    private final ChatMessageManager chatMessageManager;
    private final ClientThread clientThread;
    private final UnlockedItemsManager unlockedManager;
    private final ChanceManOverlay overlay;

    private final Queue<Integer> rollQueue = new ConcurrentLinkedQueue<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isRolling = false;
    private final int rollDuration = 4000; // Continuous phase duration (ms)
    private final int highlightDuration = 1500; // Highlight phase (ms)
    private final Random random = new Random();

    @Getter
    @Setter
    private volatile boolean manualRoll = false;

    @Inject
    public RollAnimationManager(Client client, ItemManager itemManager, ItemInfo itemInfo,
                                ChatMessageManager chatMessageManager, ClientThread clientThread,
                                UnlockedItemsManager unlockedManager, ChanceManOverlay overlay)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.itemInfo = itemInfo;
        this.chatMessageManager = chatMessageManager;
        this.clientThread = clientThread;
        this.unlockedManager = unlockedManager;
        this.overlay = overlay;
    }

    @Override
    public void onStartUp() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newSingleThreadExecutor();
        }
    }

    /**
     * Shuts down the roll animation executor service.
     */
    @Override
    public void onShutDown()
    {
        executor.shutdownNow();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        process();
    }

    /**
     * Enqueues an item ID for the roll animation.
     *
     * @param itemId The item ID to be rolled.
     */
    public void enqueueRoll(int itemId)
    {
        rollQueue.offer(itemId);
    }

    /**
     * Processes the roll queue by initiating a roll animation if not already rolling.
     */
    public void process()
    {
        if (!isRolling && !rollQueue.isEmpty())
        {
            int queuedItemId = rollQueue.poll();
            isRolling = true;
            executor.submit(() -> performRoll(queuedItemId));
        }
    }

    /**
     * Performs the roll animation, unlocking the final item and sending a chat message.
     */
    private void performRoll(int queuedItemId)
    {
        overlay.startRollAnimation(0, rollDuration, this::getRandomLockedItem);
        try {
            Thread.sleep(rollDuration + highlightDuration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int finalRolledItem = overlay.getFinalItem();
        unlockedManager.unlockItem(finalRolledItem);
        final boolean wasManualRoll = isManualRoll();
        clientThread.invoke(() -> {
            String message;
            if (wasManualRoll)
            {
                message = "Unlocked " + "<col=267567>" + getItemName(finalRolledItem) + "</col>" +
                      " by" + "<col=ff0000> pressing a button</col>";
            }
            else
            {
                message = "Unlocked " + "<col=267567>" + getItemName(finalRolledItem) + "</col>"
                        + " by rolling " + "<col=ff0000>" + getItemName(queuedItemId) + "</col>";
            }
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
        });
        setManualRoll(false);
        isRolling = false;
    }

    /**
     * Checks if a roll animation is currently in progress.
     *
     * @return true if a roll is in progress, false otherwise.
     */
    public boolean isRolling() {
        return isRolling;
    }

    /**
     * Retrieves a random locked item from the list of tradeable items.
     *
     * @return A random locked item ID, or a fallback if all items are unlocked.
     */
    public int getRandomLockedItem()
    {
        List<Integer> locked = new ArrayList<>();
        for (int id : itemInfo.getAllTradeableItems())
        {
            if (!unlockedManager.isUnlocked(id))
            {
                locked.add(id);
            }
        }
        if (locked.isEmpty())
        {
            int fallback = overlay.getFinalItem();
            return fallback;
        }
        int selected = locked.get(random.nextInt(locked.size()));
        return selected;
    }

    public String getItemName(int itemId)
    {
        ItemComposition comp = itemManager.getItemComposition(itemId);
        return comp != null ? comp.getName() : "Unknown";
    }
}
