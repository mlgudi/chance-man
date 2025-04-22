package com.chanceman.filters;

import com.chanceman.ChanceManConfig;
import com.chanceman.UnlockedItemsManager;
import com.chanceman.events.ItemListRefreshed;
import com.chanceman.lifecycle.implementations.EventUser;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides information about items relevant to Chance Man, and static helper methods for item filtering.
 */
@Singleton
public class ItemInfo extends EventUser
{

    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager itemManager;
    private final ChanceManConfig config;
    private final UnlockedItemsManager unlockedItemsManager;

    @Getter private final Set<Integer> allTradeableItems = ConcurrentHashMap.newKeySet();
    @Getter private boolean initialized = false;

    @Inject
    public ItemInfo(Client client, ClientThread clientThread, ItemManager itemManager,
                    ChanceManConfig config, UnlockedItemsManager unlockedItemsManager)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.itemManager = itemManager;
        this.config = config;
        this.unlockedItemsManager = unlockedItemsManager;
    }

    @Override
    public void onStartUp()
    {
        refreshTradeableItems();
    }

    @Override
    public void onShutDown()
    {
        allTradeableItems.clear();
        this.initialized = false;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!isInitialized() && client.getGameState() == GameState.LOGGED_IN)
        {
            refreshTradeableItems();
        }
    }

    @Subscribe
    public void onConfigChanged(net.runelite.client.events.ConfigChanged event)
    {
        if (!event.getGroup().equals("chanceman")) { return; }
        if (event.getKey().equals("freeToPlay")) { refreshTradeableItems(); }
        else if (event.getKey().equals("enableFlatpacks")) { refreshTradeableItems(); }
        else if (event.getKey().equals("enableItemSets")) { refreshTradeableItems(); }
        else if (event.getKey().equals("requireWeaponPoison")) { refreshTradeableItems(); }
    }

    /**
     * Refreshes the list of tradeable item IDs based on the current player and configuration.
     */
    private void refreshTradeableItems()
    {
        if (client.getItemCount() == 0) return;

        clientThread.invokeLater(() -> {
            allTradeableItems.clear();
            for (int i = 0; i < client.getItemCount(); i++) {
                ItemComposition comp = itemManager.getItemComposition(i);
                if (comp != null && comp.isTradeable() && !isNotTracked(i)
                    && !ItemInfo.isBlocked(i, config.enableFlatpacks(), config.enableItemSets())) {
                    if (config.freeToPlay() && comp.isMembers()) {
                        continue;
                    }
                    if (!ItemInfo.isPoisonEligible(i, config.requireWeaponPoison(),
                                                   unlockedItemsManager.getUnlockedItems())) {
                        continue;
                    }
                    allTradeableItems.add(i);
                }
            }
            if (!allTradeableItems.isEmpty()) this.initialized = true;
            post(new ItemListRefreshed());
        });
    }

    /**
     * Checks if an item is in play within Chance Man. I.e., it is a tradeable item not excluded by filters.
     *
     * @param itemId The item ID.
     * @return True if the item is in play within Chance Man.
     */
    public boolean isInPlay(int itemId)
    {
        return allTradeableItems.contains(itemId);
    }

    /**
     * Checks if an item is tradeable.
     * @param itemId The item ID.
     * @return True if the item is tradeable.
     */
    public boolean isTradeable(int itemId)
    {
        ItemComposition comp = itemManager.getItemComposition(itemId);
        return comp != null && comp.isTradeable();
    }

    /**
     * Checks if an item is not tracked.
     *
     * @param itemId The item ID.
     * @return True if the item is not tracked.
     */
    public static boolean isNotTracked(int itemId)
    {
        return itemId == 995 || itemId == 13191 || itemId == 13190 ||
               itemId == 7587 || itemId == 7588 || itemId == 7589 || itemId == 7590 || itemId == 7591;
    }

    /**
     * Checks if an item is blocked.
     * An item is blocked if it is in the blocked set,
     * or if it is a flatpack and flatpacks are disabled,
     * or if it is an item set and item sets are disabled.
     *
     * @param itemId the item id
     * @param enableFlatpacks true if flatpack items are allowed
     * @param enableItemSets true if item set items are allowed
     * @return true if the item is blocked; false otherwise
     */
    public static boolean isBlocked(int itemId, boolean enableFlatpacks, boolean enableItemSets) {
        return (!enableFlatpacks && Flatpacks.isFlatpack(itemId))
               || (!enableItemSets && ItemSets.isItemSet(itemId))
               || BlockedItems.getBLOCKED_ITEMS().contains(itemId);
    }

    /**
     * Returns the correct ensouled head ID for the given item name.
     * If not found, returns DEFAULT_ENSOULED_HEAD_ID.
     *
     * @param itemName the item name to check.
     * @return the ensouled head ID if found; otherwise DEFAULT_ENSOULED_HEAD_ID.
     */
    public static int getEnsouledHeadId(String itemName) {
        return (itemName == null)
                ? EnsouledHeadMapping.DEFAULT_ENSOULED_HEAD_ID
                : EnsouledHeadMapping.getENSOULED_HEAD_MAP().getOrDefault(
                itemName.toLowerCase(), EnsouledHeadMapping.DEFAULT_ENSOULED_HEAD_ID);
    }

    /**
     * Scans the provided text and returns the ensouled head ID if any known ensouled head key is found.
     * Returns DEFAULT_ENSOULED_HEAD_ID if no match is found.
     *
     * @param text the text to scan.
     * @return the ensouled head ID if a key is found; otherwise DEFAULT_ENSOULED_HEAD_ID.
     */
    public static int getEnsouledHeadIdFromText(String text) {
        if (text == null) {
            return EnsouledHeadMapping.DEFAULT_ENSOULED_HEAD_ID;
        }
        String lowerText = text.toLowerCase();
        for (String key : EnsouledHeadMapping.getENSOULED_HEAD_MAP().keySet()) {
            if (lowerText.contains(key)) {
                return EnsouledHeadMapping.getENSOULED_HEAD_MAP().get(key);
            }
        }
        return EnsouledHeadMapping.DEFAULT_ENSOULED_HEAD_ID;
    }

    /**
     * Checks if a poisonable weapon variant is eligible for rolling.
     * Base weapons are always eligible; for poisoned variants, if requireWeaponPoison is true,
     * the corresponding global weapon poison must also be unlocked.
     *
     * @param itemId the item id to check
     * @param requireWeaponPoison if true, the matching global poison must be unlocked
     * @param unlockedItems the set of unlocked item ids
     * @return true if eligible; false otherwise
     */
    public static boolean isPoisonEligible(int itemId, boolean requireWeaponPoison, Set<Integer> unlockedItems) {
        return PoisonWeapons.isPoisonVariantEligible(itemId, requireWeaponPoison, unlockedItems);
    }

    private boolean isGlobalWeaponPoison(int itemId) {
        return itemId == PoisonWeapons.WEAPON_POISON.getBaseId() ||
               itemId == PoisonWeapons.WEAPON_POISON_.getBaseId() ||
               itemId == PoisonWeapons.WEAPON_POISON__.getBaseId();
    }
}
