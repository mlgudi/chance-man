package com.chanceman.menus;

import com.chanceman.UnlockedItemsManager;
import com.chanceman.filters.ItemInfo;
import com.chanceman.lifecycle.implementations.EventUser;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Singleton
public class ActionHandler extends EventUser
{

	private static final Set<MenuAction> disabledActions = EnumSet.of(
			MenuAction.CC_OP,               // inventory “Use” on locked
			MenuAction.WIDGET_TARGET,       // “Use” on widgets
			MenuAction.WIDGET_TARGET_ON_WIDGET  // “Use” on widget -> widget
	);

	private static final Set<MenuAction> GROUND_ACTIONS = EnumSet.of(
			MenuAction.GROUND_ITEM_FIRST_OPTION,
			MenuAction.GROUND_ITEM_SECOND_OPTION,
			MenuAction.GROUND_ITEM_THIRD_OPTION,
			MenuAction.GROUND_ITEM_FOURTH_OPTION,
			MenuAction.GROUND_ITEM_FIFTH_OPTION
	);

	private static final HashSet<Integer> ENABLED_UIS = new HashSet<>() {{
		add(EnabledUI.BANK.getId());
		add(EnabledUI.DEPOSIT_BOX.getId());
	}};

	private final Client client;
	private final Restrictions restrictions;
	private final UnlockedItemsManager unlockedItemsManager;
	private final ItemManager itemManager;
	private final ItemInfo itemInfo;
	@Getter @Setter private int enabledUIOpen = -1;

	@Inject
	public ActionHandler(
			Client client,
			Restrictions restrictions,
			UnlockedItemsManager unlockedItemsManager,
			ItemManager itemManager,
			ItemInfo itemInfo
	) {
		this.client = client;
		this.restrictions = restrictions;
		this.unlockedItemsManager = unlockedItemsManager;
		this.itemManager = itemManager;
		this.itemInfo = itemInfo;
	}

	// A no-op click handler that marks a menu entry as disabled.
	private final Consumer<MenuEntry> DISABLED = e -> { };

	private boolean enabledUiOpen() {
		return enabledUIOpen != -1;
	}

	private boolean inactive() {
		if (!unlockedItemsManager.ready()) return true;
		return client.getGameState().getState() < GameState.LOADING.getState();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		if (ENABLED_UIS.contains(event.getGroupId()))
			enabledUIOpen = -1;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (ENABLED_UIS.contains(event.getGroupId()))
			enabledUIOpen = event.getGroupId();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (inactive()) return;

		MenuEntry entry = event.getMenuEntry();
		MenuAction action = entry.getType();
		int id = getItemId(event, entry);
		boolean enabled;
		// Check if the entry looks like it's for a ground item.
		if (isGroundItem(entry)) {
			enabled = !isLockedGroundItem(id);
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
		handleGroundItems(itemManager, itemInfo, unlockedItemsManager, event);
	}

	/**
	 * Normalize a MenuEntryAdded into the base item ID.
	 */
	private int getItemId(MenuEntryAdded event, MenuEntry entry)
	{
		int raw = GROUND_ACTIONS.contains(entry.getType())
				? event.getIdentifier()
				: Math.max(event.getItemId(), entry.getItemId());
		return itemManager.canonicalize(raw);
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
	 * @param itemId canonicalized item ID of a ground item
	 * @return true if it’s tradeable, tracked, and still locked
	 */
	private boolean isLockedGroundItem(int itemId)
	{
		return itemInfo.isTradeable(itemId)
				&& !ItemInfo.isNotTracked(itemId)
				&& !unlockedItemsManager.isUnlocked(itemId);
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
		if (option.equalsIgnoreCase("clean") || option.equalsIgnoreCase("rub"))
		{
			if (!itemInfo.isInPlay(id)) { return true; }
			return unlockedItemsManager.isUnlocked(id);
		}
		if (SkillOp.isSkillOp(option))
			return restrictions.isSkillOpEnabled(option);
		else if (Spell.isSpell(target))
			return restrictions.isSpellOpEnabled();

		boolean enabled;
		if (enabledUiOpen()) {
			enabled = option.startsWith("Deposit") || option.startsWith("Examine") || option.startsWith("Withdraw")
					|| option.startsWith("Release");
		} else {
			enabled = !disabledActions.contains(action);
		}
		if (enabled)
			return true;
		if (id == 0 || id == -1 || !itemInfo.isInPlay(id))
			return true;
		return unlockedItemsManager.isUnlocked(id);
	}

	/**
	 * A static helper to further safeguard ground item actions.
	 * If a ground item is locked, this method consumes the event.
	 */
	public static void handleGroundItems(ItemManager itemManager, ItemInfo itemInfo,
										 UnlockedItemsManager unlockedItemsManager, MenuOptionClicked event) {
		String option = event.getMenuEntry().getOption().toLowerCase();
		if (event.getMenuAction() != null &&
				(event.getMenuAction().toString().contains("GROUND_ITEM")
						|| option.contains("take")
						|| option.contains("pick-up")
						|| option.contains("pickup"))) {
			int rawItemId = event.getId() != -1 ? event.getId() : event.getMenuEntry().getItemId();
			int canonicalGroundId = itemManager.canonicalize(rawItemId);
			if (itemInfo.isTradeable(canonicalGroundId)
					&& !ItemInfo.isNotTracked(canonicalGroundId)
					&& unlockedItemsManager != null
					&& !unlockedItemsManager.isUnlocked(canonicalGroundId)) {
				event.consume();
			}
		}
	}
}
