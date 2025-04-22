package com.chanceman.menus;

import com.chanceman.ChanceManPlugin;
import com.chanceman.UnlockedItemsManager;
import com.chanceman.lifecycle.implementations.EventUser;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;

@Singleton
public class Restrictions extends EventUser
{
	private static final int[] RUNE_POUCH_TYPE_VARBITS = {
			29,    // RUNE_POUCH_RUNE1
			1622,  // RUNE_POUCH_RUNE2
			1623,  // RUNE_POUCH_RUNE3
			14285, // RUNE_POUCH_RUNE4
			15373, // RUNE_POUCH_RUNE5
			15374  // RUNE_POUCH_RUNE6
	};

	private static final int[] RUNE_POUCH_AMOUNT_VARBITS = {
			1624,  // RUNE_POUCH_AMOUNT1
			1625,  // RUNE_POUCH_AMOUNT2
			1626,  // RUNE_POUCH_AMOUNT3
			14286, // RUNE_POUCH_AMOUNT4
			15375, // RUNE_POUCH_AMOUNT5
			15376  // RUNE_POUCH_AMOUNT6
	};

	private static final WorldArea FOUNTAIN_OF_RUNE_AREA =
			new WorldArea(3367, 3890, 13, 9, 0);

	private boolean isInFountainArea()
	{
		WorldPoint lp = client.getLocalPlayer().getWorldLocation();
		return FOUNTAIN_OF_RUNE_AREA.contains(lp);
	}

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

	private final ChanceManPlugin plugin;
	private final Client client;
	private final UnlockedItemsManager unlockedItemsManager;
	private final HashSet<SkillOp> enabledSkillOps = new HashSet<>();
	private final HashSet<Integer> availableRunes = new HashSet<>();

	@Inject
	public Restrictions(
			ChanceManPlugin plugin,
			Client client,
			UnlockedItemsManager unlockedItemsManager
	) {
		this.plugin = plugin;
		this.client = client;
		this.unlockedItemsManager = unlockedItemsManager;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!unlockedItemsManager.ready()) return;
		enabledSkillOps.clear();
		availableRunes.clear();

		ItemContainer equippedItems = client.getItemContainer(InventoryID.WORN);
		ItemContainer inventoryItems = client.getItemContainer(InventoryID.INV);

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

		EnumComposition pouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < 6; i++)
		{
			int qty     = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
			int typeIdx = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
			if (qty <= 0)
			{
				continue;
			}

			int runeId = pouchEnum.getIntValue(typeIdx);
			if (!plugin.isInPlay(runeId) || !unlockedItemsManager.isUnlocked(runeId))
			{
				continue;
			}

			if (RuneProvider.isInvProvider(runeId))
			{
				availableRunes.addAll(RuneProvider.getProvidedRunes(runeId));
			}
		}
	}

	public boolean isSkillOpEnabled(String option)
	{
		SkillOp op = SkillOp.fromString(option);
		return enabledSkillOps.contains(op);
	}

	public boolean isSpellOpEnabled()
	{
		if (isInFountainArea()) { return true; }

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
