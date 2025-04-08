package com.chanceman;


import com.chanceman.account.AccountChanged;
import com.chanceman.account.AccountManager;
import com.chanceman.menus.ActionHandler;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.config.ConfigManager;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    @Inject
    private ChanceManConfig config;
    @Inject
    private ConfigManager configManager;
    @Inject
    private AccountManager accountManager;
    @Inject
    private UnlockedItemsManager unlockedItemsManager;
    @Inject
    private RolledItemsManager rolledItemsManager;
    @Inject
    private RollAnimationManager rollAnimationManager;
    @Inject
    private EventBus eventBus;

    private ChanceManPanel chanceManPanel;
    private NavigationButton navButton;
    private ExecutorService fileExecutor;
    @Getter private final HashSet<Integer> allTradeableItems = new LinkedHashSet<>();
    private static final int GE_SEARCH_BUILD_SCRIPT = 751;

    @Provides
    ChanceManConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ChanceManConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        getInjector().getInstance(ActionHandler.class).startUp();
        eventBus.register(accountManager);
        overlayManager.add(chanceManOverlay);
        fileExecutor = Executors.newSingleThreadExecutor();
        unlockedItemsManager.setExecutor(fileExecutor);
        rolledItemsManager.setExecutor(fileExecutor);
        rollAnimationManager.startUp();
        if (!isNormalWorld())
        {
            return;
        }
        refreshTradeableItems();

        chanceManPanel = new ChanceManPanel(
                unlockedItemsManager, rolledItemsManager, itemManager, allTradeableItems, clientThread,
                rollAnimationManager
        );
        BufferedImage icon = ImageUtil.loadImageResource(
                getClass(), "/net/runelite/client/plugins/chanceman/icon.png");
        navButton = NavigationButton.builder()
                                    .tooltip("ChanceMan")
                                    .icon(icon)
                                    .priority(5)
                                    .panel(chanceManPanel)
                                    .build();
        clientToolbar.addNavigation(navButton);
        overlayManager.add(chanceManOverlay);

        accountManager.init();
    }

    @Override
    protected void shutDown() throws Exception
    {
        eventBus.unregister(accountManager);
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
        if (fileExecutor != null)
        {
            fileExecutor.shutdownNow();
        }
        getInjector().getInstance(ActionHandler.class).shutDown();

        // Reset plugin state for a fresh initialization on restart.
        chanceManPanel = null;
        navButton = null;
        fileExecutor = null;
        allTradeableItems.clear();
        accountManager.reset();
    }

    /**
     * Refreshes the list of tradeable item IDs based on the current configuration.
     */
    private void refreshTradeableItems()
    {
        clientThread.invokeLater(() -> {
            allTradeableItems.clear();
            for (int i = 0; i < 35000; i++)
            {
                ItemComposition comp = itemManager.getItemComposition(i);
                if (comp != null && comp.isTradeable() && !isNotTracked(i) && !ItemsFilter.isBlocked(comp.getName()))
                {
                    if (config.freeToPlay() && comp.isMembers())
                    {
                        continue;
                    }
                    allTradeableItems.add(i);
                }
            }
            rollAnimationManager.setAllTradeableItems(allTradeableItems);
            if (chanceManPanel != null) {
                SwingUtilities.invokeLater(() -> chanceManPanel.updatePanel());
            }
        });
    }

    @Subscribe
    public void onConfigChanged(net.runelite.client.events.ConfigChanged event) {
        if (!event.getGroup().equals("chanceman")) {
            return;
        }
        if (event.getKey().equals("freeToPlay")) {
            refreshTradeableItems();
        }
    }

    @Subscribe
    private void onAccountChanged(AccountChanged event)
    {
        unlockedItemsManager.loadUnlockedItems();
        rolledItemsManager.loadRolledItems();
        if (chanceManPanel != null)
        {
            SwingUtilities.invokeLater(() -> chanceManPanel.updatePanel());
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        rollAnimationManager.process();
        if (chanceManPanel != null)
        {
            SwingUtilities.invokeLater(() -> chanceManPanel.updatePanel());
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == GE_SEARCH_BUILD_SCRIPT) {
            killSearchResults();
        }
    }

    private void killSearchResults() {
        Widget geSearchResults = client.getWidget(162, 51);
        if (geSearchResults == null) {
            return;
        }
        Widget[] children = geSearchResults.getDynamicChildren();
        if (children == null || children.length < 2 || children.length % 3 != 0) {
            return;
        }
        Set<Integer> unlocked = unlockedItemsManager.getUnlockedItems();
        for (int i = 0; i < children.length; i += 3) {
            int offerItemId = children[i + 2].getItemId();
            if (!unlocked.contains(offerItemId)) {
                children[i].setHidden(true);
                children[i + 1].setOpacity(70);
                children[i + 2].setOpacity(70);
            }
        }
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned event)
    {
        if (!accountManager.ready()) return;
        if (!isNormalWorld())
        {
            return;
        }
        TileItem tileItem = (TileItem) event.getItem();
        int itemId = tileItem.getId();
        ItemComposition comp = itemManager.getItemComposition(itemId);
        String name = (comp != null && comp.getName() != null) ? comp.getName() : tileItem.toString();
        if (name.toLowerCase().contains("ensouled"))
        {
            int mappedId = ItemsFilter.getEnsouledHeadId(name);
            if (mappedId != ItemsFilter.DEFAULT_ENSOULED_HEAD_ID)
            {
                itemId = mappedId;
            }
        }
        int canonicalItemId = itemManager.canonicalize(itemId);
        if (!isTradeable(canonicalItemId) || isNotTracked(canonicalItemId))
        {
            return;
        }
        if (tileItem.getOwnership() != TileItem.OWNERSHIP_SELF)
        {
            return;
        }
        if (rolledItemsManager == null)
        {
            return;
        }
        if (!rolledItemsManager.isRolled(canonicalItemId))
        {
            rollAnimationManager.enqueueRoll(canonicalItemId);
            rolledItemsManager.markRolled(canonicalItemId);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!accountManager.ready()) return;
        if (event.getContainerId() == 93)
        {
            Set<Integer> processed = new HashSet<>();
            for (net.runelite.api.Item item : event.getItemContainer().getItems())
            {
                int rawItemId = item.getId();
                int canonicalId = itemManager.canonicalize(rawItemId);
                if (!isTradeable(canonicalId) || isNotTracked(canonicalId))
                {
                    continue;
                }
                if (!processed.contains(canonicalId) && !rolledItemsManager.isRolled(canonicalId))
                {
                    rollAnimationManager.enqueueRoll(canonicalId);
                    rolledItemsManager.markRolled(canonicalId);
                    processed.add(canonicalId);
                }
            }
        }
    }

    public boolean isNormalWorld()
    {
        EnumSet<WorldType> worldTypes = client.getWorldType();
        return !(worldTypes.contains(WorldType.DEADMAN)
                || worldTypes.contains(WorldType.SEASONAL)
                || worldTypes.contains(WorldType.HIGH_RISK)
                || worldTypes.contains(WorldType.PVP));
    }

    public boolean isTradeable(int itemId)
    {
        ItemComposition comp = itemManager.getItemComposition(itemId);
        return comp != null && comp.isTradeable();
    }

    public boolean isNotTracked(int itemId)
    {
        return itemId == 995 || itemId == 13191 || itemId == 13190 ||
                itemId == 7588 || itemId == 1589 || itemId == 7590 || itemId == 7591;
    }

    public boolean isInPlay(int itemId)
    {
        return allTradeableItems.contains(itemId);
    }

    public ItemManager getItemManager() { return itemManager; }

}
