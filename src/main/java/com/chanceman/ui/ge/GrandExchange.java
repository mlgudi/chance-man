package com.chanceman.ui.ge;

import com.chanceman.UnlockedItemsManager;
import com.chanceman.ui.ChildType;
import com.chanceman.ui.WidgetUtil;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Disallows trading by hiding the confirm button in the GE offer UI, and
 * reduces the opacity of GE search results that cannot be traded.
 */
@Singleton
public class GrandExchange
{

	private static final int GE_SEARCH_BUILD_SCRIPT = 751;
	private static final int GE_SEARCH_RESULTS_COMP_ID = 10616883;
	private final static int GE_OFFER_CONTAINER = 30474265;
	private final static int GE_OFFER_GROUP = 465;
	private final static int GE_CONFIRM_BUTTON_ID = 30474269;
	private static final int UNAVAILABLE_OPACITY = 210;

	private final Client client;
	private final EventBus eventBus;
	private final UnlockedItemsManager unlockedItemsManager;

	private boolean offerOpen = false;
	private boolean cannotTrade = false;
	private boolean buttonUpdated = false;

	@Inject
	public GrandExchange(Client client,
						 EventBus eventBus,
						 UnlockedItemsManager unlockedItemsManager)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.unlockedItemsManager = unlockedItemsManager;
	}

	public void startUp() { eventBus.register(this); }
	public void shutDown() { eventBus.unregister(this); }

	/**
	 * Updates the confirm button to allow or disallow trades based on unlocks/rolls.
	 */
	private void updateButton()
	{
		WidgetUtil.apply(client, GE_CONFIRM_BUTTON_ID, w -> w.setHidden(cannotTrade));
		WidgetUtil.applyToAllChildren(
				client, GE_OFFER_CONTAINER, ChildType.DYNAMIC,
				w -> {
					if (w.getType() == WidgetType.TEXT && w.getText().startsWith("<"))
						w.setHidden(cannotTrade);
				});
		this.buttonUpdated = true;
	}

	/**
	 * Reduces the opacity of GE search results that cannot be traded.
	 */
	private void modifySearchResults()
	{
		WidgetUtil.applyToAllChildren(
				client, GE_SEARCH_RESULTS_COMP_ID, ChildType.DYNAMIC, w -> {
					if (w.getIndex() % 3 != 0) return;
					Widget[] siblings = w.getParent().getDynamicChildren();
					if (!(siblings.length > w.getIndex() + 2)) return;
					Widget text = siblings[w.getIndex() + 1];
					Widget sprite = siblings[w.getIndex() + 2];
					int itemId = sprite.getItemId();
					if (!unlockedItemsManager.isUnlocked(itemId))
					{
						text.setOpacity(UNAVAILABLE_OPACITY);
						sprite.setOpacity(UNAVAILABLE_OPACITY);
					}
				}
		);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == GE_SEARCH_BUILD_SCRIPT)
			modifySearchResults();
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.GE_OFFERS_SETUP_BUILD)
		{
			int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
			cannotTrade = !unlockedItemsManager.isUnlocked(itemId);
			buttonUpdated = false;
			offerOpen = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == GE_OFFER_GROUP)
		{
			offerOpen = false;
			buttonUpdated = false;
		}
	}

	@Subscribe
	private void onBeforeRender(BeforeRender event)
	{
		if (offerOpen && !buttonUpdated) updateButton();
	}
}
