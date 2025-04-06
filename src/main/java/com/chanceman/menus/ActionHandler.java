package com.chanceman.menus;


import com.chanceman.ChanceManPlugin;
import com.chanceman.UnlockedItemsManager;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.function.Consumer;

@Singleton
public class ActionHandler
{

	private final HashSet<MenuAction> disabledActions = new HashSet<>(){{
		add(MenuAction.CC_OP); // Should cover all inventory item ops
		add(MenuAction.WIDGET_TARGET); // Disable Use options on locked item
		add(MenuAction.WIDGET_TARGET_ON_WIDGET); // Disable using other items on locked item
		add(MenuAction.GROUND_ITEM_FIRST_OPTION);
		add(MenuAction.GROUND_ITEM_SECOND_OPTION);
		add(MenuAction.GROUND_ITEM_THIRD_OPTION);
		add(MenuAction.GROUND_ITEM_FOURTH_OPTION);
		add(MenuAction.GROUND_ITEM_FIFTH_OPTION);
	}};

	private final HashSet<Integer> enabledUIs = new HashSet<>(){{
		add(EnabledUI.BANK.getId());
		add(EnabledUI.DEPOSIT_BOX.getId());
	}};

	@Inject private Client client;
	@Inject private EventBus eventBus;
	@Inject private ChanceManPlugin plugin;
	@Inject private Restrictions restrictions;
	@Inject private UnlockedItemsManager unlockedItemsManager;
	@Getter @Setter private int enabledUIOpen = -1;
	private final Consumer<MenuEntry> DISABLED = e -> {};

	public void startUp()
	{
		eventBus.register(this);
		eventBus.register(restrictions);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		eventBus.unregister(restrictions);
	}

	private boolean enabledUiOpen() { return enabledUIOpen != -1; }
	private boolean inactive()
	{
		if (!unlockedItemsManager.ready()) return true;
		return client.getGameState().getState() < GameState.LOADING.getState();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (enabledUIs.contains(event.getGroupId())) enabledUIOpen = -1;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (enabledUIs.contains(event.getGroupId())) enabledUIOpen = event.getGroupId();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (inactive()) return;

		MenuEntry entry = event.getMenuEntry();
		MenuAction action = entry.getType();
		int id = Math.max(event.getItemId(), entry.getItemId());
		if (isEnabled(id, entry, action)) return;

		String option = Text.removeTags(entry.getOption());
		String target = Text.removeTags(entry.getTarget());
		entry.setTarget("<col=808080>" + target);
		entry.setOption("<col=808080>" + option);
		entry.onClick(DISABLED);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuEntry().onClick() == DISABLED) event.consume();
	}

	private boolean isEnabled(int id, MenuEntry entry, MenuAction action)
	{
		String option = Text.removeTags(entry.getOption());
		String target = Text.removeTags(entry.getTarget());

		if (SkillOp.isSkillOp(option))
		{
			return restrictions.isSkillOpEnabled(option);
		}
		else if (Spell.isSpell(target))
		{
			return restrictions.isSpellOpEnabled();
		}

		boolean enabled;
		if (enabledUiOpen())
		{
			enabled = option.startsWith("Deposit") || option.startsWith("Examine") || option.startsWith("Withdraw");
		} else {
			enabled = !disabledActions.contains(action);
		}
		if (enabled) return true;

		if (id == 0 || id == -1 || !plugin.isInPlay(id)) return true;
		return (unlockedItemsManager.isUnlocked(entry.getItemId()));
	}

}
