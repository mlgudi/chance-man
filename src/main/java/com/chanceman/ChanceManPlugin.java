package com.chanceman;

import com.chanceman.events.ItemRolled;
import com.chanceman.filters.EnsouledHeadMapping;
import com.chanceman.lifecycle.LifeCycleHub;
import com.chanceman.menus.ActionHandler;
import com.chanceman.filters.ItemInfo;
import com.google.gson.Gson;
import com.google.inject.Provides;
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
import javax.inject.Provider;
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

    private static final BufferedImage NAV_BUTTON_ICON = ImageUtil.loadImageResource(
            ChanceManPlugin.class, "/net/runelite/client/plugins/chanceman/icon.png"
    );

    @Inject
    private LifeCycleHub lifeCycleHub;
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
    private ItemInfo itemInfo;
    @Inject
    private ActionHandler actionHandler;
    @Inject
    private Provider<ChanceManPanel> panelProvider;
    @Inject
    private EventBus eventBus;

    private ChanceManPanel chanceManPanel;
    private NavigationButton navButton;
    private ExecutorService fileExecutor;
    private static final int GE_SEARCH_BUILD_SCRIPT = 751;

    @Provides
    ChanceManConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ChanceManConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        lifeCycleHub.startUp();

        overlayManager.add(chanceManOverlay);
        fileExecutor = Executors.newSingleThreadExecutor();
        unlockedItemsManager.setExecutor(fileExecutor);
        rolledItemsManager.setExecutor(fileExecutor);

        if (!isNormalWorld())
        {
            return;
        }

        chanceManPanel = panelProvider.get();
        navButton = NavigationButton.builder()
                                    .tooltip("ChanceMan")
                                    .icon(NAV_BUTTON_ICON)
                                    .priority(5)
                                    .panel(chanceManPanel)
                                    .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        lifeCycleHub.shutDown();
        if (clientToolbar != null && navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }
        if (overlayManager != null)
        {
            overlayManager.remove(chanceManOverlay);
        }
        if (fileExecutor != null)
        {
            fileExecutor.shutdownNow();
        }

        // Reset plugin state for a fresh initialization on restart.
        navButton = null;
        chanceManPanel = null;
        fileExecutor = null;
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == GE_SEARCH_BUILD_SCRIPT) { killSearchResults(); }
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
        if (!isNormalWorld()) return;

        TileItem tileItem = event.getItem();
        if (tileItem.getOwnership() != TileItem.OWNERSHIP_SELF)
        {
            return;
        }

        int itemId = tileItem.getId();
        ItemComposition comp = itemManager.getItemComposition(itemId);
        String name = (comp != null && comp.getName() != null) ? comp.getName() : tileItem.toString();
        if (name.toLowerCase().contains("ensouled")) {
            int mappedId = ItemInfo.getEnsouledHeadId(name);
            if (mappedId != EnsouledHeadMapping.DEFAULT_ENSOULED_HEAD_ID) { itemId = mappedId; }
        }
        processRollCandidate(itemId);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!accountManager.ready()) return;
        if (!isNormalWorld()) return;

        if (event.getContainerId() == 93)
        {
            Set<Integer> processed = new HashSet<>();
            for (net.runelite.api.Item item : event.getItemContainer().getItems())
            {
                int canonicalId = itemManager.canonicalize(item.getId());
                processRollCandidate(canonicalId);
                processed.add(canonicalId);
            }
        }
    }

    private void processRollCandidate(int itemId)
    {
        int canonicalItemId = itemManager.canonicalize(itemId);
        if (!itemInfo.isTradeable(canonicalItemId) || ItemInfo.isNotTracked(canonicalItemId))
        {
            return;
        }
        if (!rolledItemsManager.isRolled(canonicalItemId))
        {
            eventBus.post(new ItemRolled(canonicalItemId));
        }
    }

    public boolean isNormalWorld()
    {
        EnumSet<WorldType> worldTypes = client.getWorldType();
        return !(worldTypes.contains(WorldType.DEADMAN)
                || worldTypes.contains(WorldType.SEASONAL)
                || worldTypes.contains(WorldType.BETA_WORLD)
                || worldTypes.contains(WorldType.PVP_ARENA)
                || worldTypes.contains(WorldType.QUEST_SPEEDRUNNING)
                || worldTypes.contains(WorldType.TOURNAMENT_WORLD));
    }

}
