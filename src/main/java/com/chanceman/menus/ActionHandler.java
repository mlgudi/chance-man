package com.chanceman.menus;

import com.chanceman.ChanceManPlugin;
import com.chanceman.UnlockedItemsManager;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.function.Consumer;

@Singleton
public class ActionHandler {

	private final HashSet<MenuAction> disabledActions = new HashSet<>() {{
		add(MenuAction.CC_OP); // covers all inventory item ops
		add(MenuAction.WIDGET_TARGET); // disable Use options on locked items
		add(MenuAction.WIDGET_TARGET_ON_WIDGET); // disable using other items on locked items
		add(MenuAction.GROUND_ITEM_FIRST_OPTION);
		add(MenuAction.GROUND_ITEM_SECOND_OPTION);
		add(MenuAction.GROUND_ITEM_THIRD_OPTION);
		add(MenuAction.GROUND_ITEM_FOURTH_OPTION);
		add(MenuAction.GROUND_ITEM_FIFTH_OPTION);
	}};

	private final HashSet<Integer> enabledUIs = new HashSet<>() {{
		add(EnabledUI.BANK.getId());
		add(EnabledUI.DEPOSIT_BOX.getId());
	}};

	@Inject
	private Client client;
	@Inject
	private EventBus eventBus;
	@Inject
	private ChanceManPlugin plugin;
	@Inject
	private Restrictions restrictions;
	@Inject
	private UnlockedItemsManager unlockedItemsManager;
	@Getter
	@Setter
	private int enabledUIOpen = -1;

	// A no-op click handler that marks a menu entry as disabled.
	private final Consumer<MenuEntry> DISABLED = e -> { };

	public void startUp() {
		eventBus.register(this);
		eventBus.register(restrictions);
	}

	public void shutDown() {
		eventBus.unregister(this);
		eventBus.unregister(restrictions);
	}

	private boolean enabledUiOpen() {
		return enabledUIOpen != -1;
	}

	private boolean inactive() {
		if (!unlockedItemsManager.ready()) return true;
		return client.getGameState().getState() < GameState.LOADING.getState();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		if (enabledUIs.contains(event.getGroupId()))
			enabledUIOpen = -1;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (enabledUIs.contains(event.getGroupId()))
			enabledUIOpen = event.getGroupId();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (inactive()) return;

		MenuEntry entry = event.getMenuEntry();
		MenuAction action = entry.getType();
		int id = Math.max(event.getItemId(), entry.getItemId());
		boolean enabled;
		// Check if the entry looks like it's for a ground item.
		if (isGroundItem(entry)) {
			enabled = !isLockedGroundItem(entry);
		} else {
			enabled = isEnabled(id, entry, action);
		}
		// If not enabled, grey out the text and set the click handler to DISABLED.
		if (!enabled) {
			String option = Text.removeTags(entry.getOption());
			String target = Text.removeTags(entry.getTarget());
			entry.setOption("<col=808080>" + option);
			entry.setTarget("<col=808080>" + target);
			entry.onClick(DISABLED);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		// If the entry is disabled, consume the event.
		if (event.getMenuEntry().onClick() == DISABLED) {
			event.consume();
			return;
		}
		// Extra safeguard for ground items.
		handleGroundItems(plugin.getItemManager(), unlockedItemsManager, event, plugin);
	}

	/**
	 * Returns true if the entry appears to be for a ground item.
	 */
	private boolean isGroundItem(MenuEntry entry) {
		String option = Text.removeTags(entry.getOption()).toLowerCase();
		MenuAction action = entry.getType();
		return action.toString().contains("GROUND_ITEM")
				|| option.contains("take")
				|| option.contains("pick-up")
				|| option.contains("pickup");
	}

	/**
	 * Checks if a MenuEntry represents a locked ground item.
	 * That is, if it's a ground item action (such as "Take") for an item that is tradeable,
	 * not excluded, and still locked.
	 */
	private boolean isLockedGroundItem(MenuEntry entry) {
		String option = Text.removeTags(entry.getOption()).toLowerCase();
		// Always allow "Drop" actions.
		if (option.equalsIgnoreCase("drop"))
			return false;
		int rawItemId = entry.getIdentifier() != -1 ? entry.getIdentifier() : entry.getItemId();
		int canonicalId = plugin.getItemManager().canonicalize(rawItemId);
		return plugin.isTradeable(canonicalId)
				&& !plugin.isNotTracked(canonicalId)
				&& !unlockedItemsManager.isUnlocked(canonicalId);
	}

	/**
	 * This method handles non-ground items (or any other cases) by checking if the item is enabled.
	 * It returns true if the action should be allowed.
	 */
	private boolean isEnabled(int id, MenuEntry entry, MenuAction action) {
		String option = Text.removeTags(entry.getOption());
		String target = Text.removeTags(entry.getTarget());

		// Always allow "Drop"
		if (option.equalsIgnoreCase("drop"))
			return true;

		if (SkillOp.isSkillOp(option))
			return restrictions.isSkillOpEnabled(option);
		else if (Spell.isSpell(target))
			return restrictions.isSpellOpEnabled();

		boolean enabled;
		if (enabledUiOpen()) {
			enabled = option.startsWith("Deposit") || option.startsWith("Examine") || option.startsWith("Withdraw");
		} else {
			enabled = !disabledActions.contains(action);
		}
		if (enabled)
			return true;
		if (id == 0 || id == -1 || !plugin.isInPlay(id))
			return true;
		return unlockedItemsManager.isUnlocked(entry.getItemId());
	}

	/**
	 * A static helper to further safeguard ground item actions.
	 * If a ground item is locked, this method consumes the event.
	 */
	public static void handleGroundItems(ItemManager itemManager, UnlockedItemsManager unlockedItemsManager,
										 MenuOptionClicked event, ChanceManPlugin plugin) {
		String option = event.getMenuEntry().getOption().toLowerCase();
		if (event.getMenuAction() != null &&
				(event.getMenuAction().toString().contains("GROUND_ITEM")
						|| option.contains("take")
						|| option.contains("pick-up")
						|| option.contains("pickup"))) {
			int rawItemId = event.getId() != -1 ? event.getId() : event.getMenuEntry().getItemId();
			int canonicalGroundId = itemManager.canonicalize(rawItemId);
			if (plugin.isTradeable(canonicalGroundId)
					&& !plugin.isNotTracked(canonicalGroundId)
					&& unlockedItemsManager != null
					&& !unlockedItemsManager.isUnlocked(canonicalGroundId)) {
				event.consume();
			}
		}
	}
}
