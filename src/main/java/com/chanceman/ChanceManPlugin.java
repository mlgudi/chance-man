package com.chanceman;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChanceManPlugin locks tradeable items until unlocked via a random roll.
 * It handles the initialization of managers for rolled and unlocked items,
 * as well as the UI overlay and panel.
 */
@PluginDescriptor(
        name = "ChanceMan",
        description = "Locks tradeable items until unlocked via a random roll.",
        tags = {"osrs", "chance", "roll", "lock", "unlock"}
)
public class ChanceManPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ChanceManOverlay chanceManOverlay;

    @Inject
    private Gson gson;

    private UnlockedItemsManager unlockedItemsManager;
    private RolledItemsManager rolledItemsManager;
    private RollAnimationManager rollAnimationManager;

    private ChanceManPanel chanceManPanel;
    private NavigationButton navButton;

    // List of tradeable item IDs (excluding coins)
    private final List<Integer> allTradeableItems = new ArrayList<>();
    // Map to track ground items: key = "worldPoint_itemId", value = tick count when spawned.
    private final Map<String, Integer> groundItemTicks = new HashMap<>();

    @Override
    protected void startUp() throws Exception
    {
        if (!isNormalWorld())
        {
            return;
        }

        clientThread.invokeLater(() -> {
            for (int i = 0; i < 30000; i++)
            {
                ItemComposition comp = itemManager.getItemComposition(i);
                if (comp != null && comp.isTradeable() && i != 995)
                {
                    allTradeableItems.add(i);
                }
            }
        });
        // Initialization of managers and UI will be handled in onGameTick.
    }

    @Override
    protected void shutDown() throws Exception
    {
        if (clientToolbar != null && navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }
        if (overlayManager != null)
        {
            overlayManager.remove(chanceManOverlay);
        }
        if (rollAnimationManager != null)
        {
            rollAnimationManager.shutdown();
        }
        if (unlockedItemsManager != null)
        {
            unlockedItemsManager.shutdown();
        }
    }

    /**
     * Processes game ticks, initializing managers and UI when the local player is available,
     * updating the roll animation, and cleaning up ground item tick data.
     *
     * @param event The game tick event.
     */
    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getLocalPlayer() != null && chanceManPanel == null)
        {
            String playerName = client.getLocalPlayer().getName();
            unlockedItemsManager = new UnlockedItemsManager(playerName, gson);
            unlockedItemsManager.loadUnlockedItems();

            rolledItemsManager = new RolledItemsManager(playerName, gson);
            rolledItemsManager.loadRolledItems();

            rollAnimationManager = new RollAnimationManager(
                    unlockedItemsManager,
                    chanceManOverlay,
                    allTradeableItems,
                    itemManager,
                    client,
                    chatMessageManager,
                    this,
                    clientThread
            );

            // Pass the rollAnimationManager reference to the panel.
            chanceManPanel = new ChanceManPanel(unlockedItemsManager, rolledItemsManager, itemManager, allTradeableItems, clientThread, rollAnimationManager);

            BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/net/runelite/client/plugins/chanceman/icon.png");
            navButton = NavigationButton.builder()
                    .tooltip("ChanceMan")
                    .icon(icon)
                    .priority(5)
                    .panel(chanceManPanel)
                    .build();
            clientToolbar.addNavigation(navButton);
            overlayManager.add(chanceManOverlay);
        }

        if (rollAnimationManager != null)
        {
            rollAnimationManager.process();
        }

        if (chanceManPanel != null)
        {
            SwingUtilities.invokeLater(() -> chanceManPanel.updatePanel());
        }

        int currentTick = client.getTickCount();
        groundItemTicks.entrySet().removeIf(entry -> entry.getValue() < currentTick);
    }

    /**
     * Handles the event when an item spawns on the ground.
     * If the item is locked and has not yet been rolled, it enqueues a roll.
     *
     * @param event The item spawned event.
     */
    @Subscribe
    public void onItemSpawned(ItemSpawned event)
    {
        if (!isNormalWorld())
        {
            return;
        }
        TileItem tileItem = (TileItem) event.getItem();
        int itemId = tileItem.getId();
        if (!isTradeable(itemId) || isCoin(itemId))
        {
            return;
        }
        if (tileItem.getOwnership() != TileItem.OWNERSHIP_SELF)
        {
            return;
        }
        // Check if the item has already been rolled once.
        if (rolledItemsManager != null && rolledItemsManager.isRolled(itemId))
        {
            return;
        }
        WorldPoint wp = event.getTile().getWorldLocation();
        String key = wp.toString() + "_" + itemId;
        int currentTick = client.getTickCount();
        if (groundItemTicks.containsKey(key) && groundItemTicks.get(key) < currentTick)
        {
            return;
        }
        groundItemTicks.put(key, currentTick);
        if (rollAnimationManager != null)
        {
            rollAnimationManager.enqueueRoll(itemId);
            rolledItemsManager.markRolled(itemId);
        }
    }

    /**
     * Handles inventory changes. When items are added to the inventory (for example, via quest rewards),
     * this method checks for locked items that have not been rolled before and enqueues a roll for each.
     *
     * @param event The item container changed event.
     */
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        // Assuming container ID 93 corresponds to the inventory.
        if (event.getContainerId() == 93)
        {
            // Iterate over all items in the inventory.
            for (net.runelite.api.Item item : event.getItemContainer().getItems())
            {
                int itemId = item.getId();
                if (!isTradeable(itemId) || isCoin(itemId))
                {
                    continue;
                }
                // If the item is locked and not yet rolled, trigger a roll.
                if (unlockedItemsManager != null && !unlockedItemsManager.isUnlocked(itemId)
                        && rolledItemsManager != null && !rolledItemsManager.isRolled(itemId))
                {
                    if (rollAnimationManager != null)
                    {
                        rollAnimationManager.enqueueRoll(itemId);
                    }
                    rolledItemsManager.markRolled(itemId);
                }
            }
        }
    }

    /**
     * Handles menu option clicks.
     * For ground items, it continues to block actions if locked.
     * For inventory items, if the item is locked (i.e. not yet unlocked),
     * only the "examine" and "drop" actions are allowed; all others are consumed.
     *
     * @param event The menu option clicked event.
     */
    @Subscribe
    public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event)
    {
        String option = event.getMenuEntry().getOption().toLowerCase();

        // Handle ground item actions.
        if (event.getMenuAction() != null &&
                (event.getMenuAction().toString().contains("GROUND_ITEM") ||
                        option.contains("take") || option.contains("pick-up") || option.contains("pickup")))
        {
            int groundItemId = event.getId() != -1 ? event.getId() : event.getMenuEntry().getItemId();
            if (isTradeable(groundItemId) && !isCoin(groundItemId)
                    && unlockedItemsManager != null && !unlockedItemsManager.isUnlocked(groundItemId))
            {
                event.consume();
                return;
            }
        }

        // Handle inventory item actions.
        if (event.getMenuEntry().getItemId() != -1)
        {
            int itemId = event.getMenuEntry().getItemId();
            String itemName = getItemName(itemId).toLowerCase();

            // Skip non-tradeable items, coins, coin pouches, and clue scrolls.
            if (!isTradeable(itemId)
                    || isCoin(itemId)
                    || itemName.contains("coin pouch")
                    || itemName.contains("clue scroll"))
            {
                return;
            }

            // For locked inventory items, allow only "examine" and "drop" actions.
            if (unlockedItemsManager != null && !unlockedItemsManager.isUnlocked(itemId))
            {
                if (!option.equals("examine") && !option.equals("drop"))
                {
                    event.consume();
                }
            }
        }
    }



    private boolean isNormalWorld()
    {
        EnumSet<WorldType> worldTypes = client.getWorldType();
        return !(worldTypes.contains(WorldType.DEADMAN)
                || worldTypes.contains(WorldType.SEASONAL)
                || worldTypes.contains(WorldType.HIGH_RISK)
                || worldTypes.contains(WorldType.PVP));
    }

    private boolean isTradeable(int itemId)
    {
        ItemComposition comp = itemManager.getItemComposition(itemId);
        return comp != null && comp.isTradeable();
    }

    private boolean isCoin(int itemId)
    {
        return itemId == 995;
    }

    public String getItemName(int itemId)
    {
        ItemComposition comp = itemManager.getItemComposition(itemId);
        return comp != null ? comp.getName() : "Unknown";
    }
}
