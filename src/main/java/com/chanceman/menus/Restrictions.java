package com.chanceman.menus;

import com.chanceman.ChanceManPlugin;
import com.chanceman.UnlockedItemsManager;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class Restrictions
{

	public static final int SPELL_REQUIREMENT_OVERLAY_NORMAL = 14287050;
	public static final int AUTOCAST_REQUIREMENT_OVERLAY_NORMAL = 13172738;

	private static final HashMap<Integer, SkillOp> ITEM_TO_OP = new HashMap<>();
	static
	{
		for (SkillItem skillItem : SkillItem.values())
		{
			ITEM_TO_OP.put(skillItem.getId(), skillItem.getSkillOp());
		}
	}

	@Inject private ChanceManPlugin plugin;
	@Inject private Client client;
	@Inject private UnlockedItemsManager unlockedItemsManager;
	private final HashSet<SkillOp> enabledSkillOps = new HashSet<>();
	private final HashSet<Integer> availableRunes = new HashSet<>();

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!unlockedItemsManager.ready()) return;
		enabledSkillOps.clear();
		availableRunes.clear();

		ItemContainer equippedItems = client.getItemContainer(InventoryID.EQUIPMENT);
		ItemContainer inventoryItems = client.getItemContainer(InventoryID.INVENTORY);

		if (equippedItems != null)
		{
			Arrays.stream(equippedItems.getItems()).forEach(item -> {
				int id = item.getId();
				if (!plugin.isInPlay(id) || !unlockedItemsManager.isUnlocked(id)) return;
				if (RuneProvider.isEquppedProvider(id)) availableRunes.addAll(RuneProvider.getProvidedRunes(id));
				if (SkillItem.isSkillItem(id)) enabledSkillOps.add(ITEM_TO_OP.get(id));
			});
		}

		if (inventoryItems != null)
		{
			Arrays.stream(inventoryItems.getItems()).forEach(item -> {
				int id = item.getId();
				if (!plugin.isInPlay(id) || !unlockedItemsManager.isUnlocked(id)) return;
				if (RuneProvider.isInvProvider(id)) availableRunes.addAll(RuneProvider.getProvidedRunes(id));
				if (SkillItem.isSkillItem(id)) enabledSkillOps.add(ITEM_TO_OP.get(id));
			});
		}
	}

	public boolean isSkillOpEnabled(String option)
	{
		SkillOp op = SkillOp.fromString(option);
		return enabledSkillOps.contains(op);
	}

	public boolean isSpellOpEnabled()
	{
		Widget spellOverlay = client.getWidget(SPELL_REQUIREMENT_OVERLAY_NORMAL);
		if (spellOverlay != null) return processChildren(spellOverlay);

		Widget autocastOverlay = client.getWidget(AUTOCAST_REQUIREMENT_OVERLAY_NORMAL);
		if (autocastOverlay != null) return processChildren(autocastOverlay);
		return false;
	}

	public boolean processChildren(Widget widget)
	{
		Widget[] children = widget.getDynamicChildren();
		if (children == null) return true;

		for (Widget child : children)
		{
			int id = child.getItemId();
			if (id == -1) continue;

			if (plugin.isInPlay(id) && !availableRunes.contains(id))
				return false;
		}
		return true;
	}
}
