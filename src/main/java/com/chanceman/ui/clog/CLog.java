package com.chanceman.ui.clog;

import com.chanceman.RolledItemsManager;
import com.chanceman.UnlockedItemsManager;
import com.chanceman.account.AccountChanged;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Overrides the Collection Log UI when it is opened to display Chance Man progress.
 */
@Singleton
public class CLog {

	enum OpenState {
		CLOSED,
		INIT,
		OPEN;
	}

	private static final int CLOG_UI_SETUP = 7797;
	private static final int WINDOW_CLOSE = 903;
	private static final int COLLECTION_LOG_GROUP = 621;
	private static final int COMBAT_ACHIEVEMENT_BUTTON = 21;
	private static final int COLLECTION_VIEW_SCROLLBAR = 38;

	private static final int MAX_X = 210;
	private static final int X_INCREMENT = 42;
	private static final int Y_INCREMENT = 40;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private EventBus eventBus;
	@Inject private ChatboxPanelManager chatboxPanelManager;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private UnlockedItemsManager unlockedItemsManager;
	@Inject private RolledItemsManager rolledItemsManager;

	@Setter private HashSet<Integer> allTradeableItems;
	private final ArrayList<Integer> rolled = new ArrayList<>();
	private final ArrayList<Integer> unlocked = new ArrayList<>();
	private final HashMap<Integer, Integer> rolledIdToIndex = new HashMap<>();
	private final HashMap<Integer, Integer> unlockedIdToIndex = new HashMap<>();
	private final List<CLogEntry> clogEntries = new ArrayList<>();

	private ChatboxTextInput searchInput;
	private Widget searchButton;
	private List<Widget> entryWidgets;

	private String lastProgressText = "";
	private OpenState clogState = OpenState.CLOSED;
	private int currentHeight = -1;

	@Getter @Setter private boolean showRolled = false;
	@Getter @Setter private boolean pendingScrollUpdate = false;

	public void startUp() {
		eventBus.register(this);
	}

	public void shutDown() {
		eventBus.unregister(this);
	}

	@Subscribe
	private void onScriptPostFired(ScriptPostFired event) {
		if (!ready()) return;

		if (event.getScriptId() == CLOG_UI_SETUP) {
			if (clogState == OpenState.CLOSED)
			{
				clogState = OpenState.INIT;
				return;
			} else if (clogState == OpenState.INIT)
			{
				clogState = OpenState.OPEN;
				update();
				override();
			}
		}

		if (event.getScriptId() == WINDOW_CLOSE) {
			clogState = OpenState.CLOSED;
			reset();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event) {
		if (!ready()) return;
		if (clogState == OpenState.OPEN) updateScrollbar();
		replaceProgress();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		if (event.getGroupId() == COLLECTION_LOG_GROUP) {
			reset();
		}
	}

	@Subscribe
	private void onAccountChanged(AccountChanged event) {
		clearAllData();
		update();
	}

	private boolean ready() {
		return unlockedItemsManager.ready() && allTradeableItems != null;
	}

	private void update() {
		rolled.clear();
		unlocked.clear();
		if (ready()) {
			rolled.addAll(rolledItemsManager.getRolledItems());
			unlocked.addAll(unlockedItemsManager.getUnlockedItems());
		}
	}

	private void reset() {
		unlocked.clear();
		rolled.clear();
		if (entryWidgets != null) {
			entryWidgets.clear();
		}
		closeSearchInput();
	}

	private void closeSearchInput() {
		if (searchInput != null) {
			chatboxPanelManager.close();
			searchInput = null;
		}
	}

	private void clearAllData() {
		unlocked.clear();
		rolled.clear();
		unlockedIdToIndex.clear();
		rolledIdToIndex.clear();
		clogEntries.clear();
		if (entryWidgets != null) {
			entryWidgets.clear();
		}
	}

	private CLogEntry addEntry(int index, boolean initialCreation) {
		int unlockedId = index < unlocked.size() ? unlocked.get(index) : 0;
		int rolledId = index < rolled.size() ? rolled.get(index) : 0;

		unlockedIdToIndex.put(unlockedId, index);
		rolledIdToIndex.put(rolledId, index);

		if (!initialCreation) updateCrossReferences(unlockedId, rolledId);
		ItemComposition u = client.getItemDefinition(unlockedId);
		ItemComposition r = client.getItemDefinition(rolledId);

		return new CLogEntry(
				index,
				unlockedId,
				u.getName(),
				rolledId,
				r.getName(),
				rolledItemsManager.isRolled(unlockedId),
				unlockedItemsManager.isUnlocked(rolledId)
		);
	}

	private void updateCrossReferences(int unlockedId, int rolledId) {
		if (rolledIdToIndex.containsKey(unlockedId)) {
			clogEntries.get(rolledIdToIndex.get(rolledId)).setRolledIsUnlocked(true);
		}
		if (unlockedIdToIndex.containsKey(rolledId)) {
			clogEntries.get(unlockedIdToIndex.get(unlockedId)).setUnlockedIsRolled(true);
		}
	}

	private void replaceProgress() {
		Widget charSummContainer = client.getWidget(ComponentID.CHARACTER_SUMMARY_CONTAINER);
		if (charSummContainer == null) return;

		Widget[] children = charSummContainer.getDynamicChildren();
		if (children == null || children.length < 88) return;

		Widget progress = children[88];
		if (progress == null || progress.getText().equals(lastProgressText)) return;

		String newText = "<col=0dc10d>" + unlocked.size() + "/" + allTradeableItems.size() + "</col>";
		progress.setText(newText);
		lastProgressText = newText;
	}

	private void override() {
		Widget combatAchievementsButton = client.getWidget(COLLECTION_LOG_GROUP, COMBAT_ACHIEVEMENT_BUTTON);
		if (combatAchievementsButton == null) return;

		Widget button = client.getWidget(COLLECTION_LOG_GROUP, COMBAT_ACHIEVEMENT_BUTTON);
		if (button == null) return;
		button.setHidden(true);

		Widget collectionViewHeader = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_HEADER);
		if (collectionViewHeader == null) return;

		replaceHeaderContent(collectionViewHeader);
		createItemList(collectionViewHeader);
	}

	private void replaceHeaderContent(Widget collectionViewHeader) {
		Widget[] headerComponents = collectionViewHeader.getDynamicChildren();
		if (headerComponents.length == 0) return;

		if (headerComponents[0] != null) {
			headerComponents[0].setText("Chance Man");
		}
		if (headerComponents[1] != null) {
			headerComponents[1].setText(progressTextHeader());
		}
		if (headerComponents.length > 2) {
			headerComponents[2].setText("");
		}
	}

	private void createItemList(Widget collectionViewHeader) {
		if (collectionViewHeader == null || collectionViewHeader.getDynamicChildren().length == 0) return;

		createSwapButton(collectionViewHeader);
		createSearchButton(collectionViewHeader);

		Widget collectionView = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		if (collectionView == null) return;

		collectionView.deleteAllChildren();
		addItems(collectionView);
		updateCurrentHeight(collectionView);
		setPendingScrollUpdate(true);
	}

	private void addItems(Widget collectionView) {
		int index = 0;
		int x = 0;
		int y = 0;

		boolean initialCreation = clogEntries.isEmpty();

		for (int i = 0; i < unlocked.size(); i++) {
			if (index >= clogEntries.size()) {
				clogEntries.add(addEntry(index, initialCreation));
			}
			addItem(collectionView, clogEntries.get(index), x, y);

			x += X_INCREMENT;
			if (x > MAX_X) {
				x = 0;
				y += Y_INCREMENT;
			}
			index++;
		}
	}

	private void addItem(Widget collectionView, CLogEntry display, int x, int y) {
		Widget newItem = collectionView.createChild(display.getIndex(), WidgetType.GRAPHIC);
		setupItemWidget(newItem, display, x, y);
		updateEntry(newItem, display);
	}

	private void setupItemWidget(Widget widget, CLogEntry display, int x, int y) {
		widget.setContentType(0);
		widget.setItemQuantity(1);
		widget.setItemQuantityMode(0);
		widget.setModelId(-1);
		widget.setModelType(1);
		widget.setSpriteId(-1);
		widget.setBorderType(1);
		widget.setFilled(false);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setOriginalWidth(36);
		widget.setOriginalHeight(32);
		widget.setHasListener(true);
		widget.setAction(1, "Inspect");
		widget.setOnOpListener((JavaScriptCallback) e -> handleItemAction(display));
	}

	private void updateEntry(Widget entry, CLogEntry display) {
		int newId = showRolled ? display.getRolledId() : display.getUnlockedId();
		ItemComposition comp = client.getItemDefinition(newId);

		entry.setItemId(newId);
		entry.setOpacity(showRolled ? display.rolledOpacity() : display.unlockedOpacity());
		entry.setName(comp.getName());
		entry.revalidate();
	}

	private void handleItemAction(CLogEntry entry) {
		chatMessageManager.queue(QueuedMessage.builder()
											  .type(ChatMessageType.ITEM_EXAMINE)
											  .runeLiteFormattedMessage(entry.actionText(showRolled))
											  .build());
	}

	private void createSwapButton(Widget header) {
		Widget swapButton = header.createChild(-1, WidgetType.GRAPHIC);
		setupButton(swapButton, 1118, 25, "Swap", this::toggleDisplay);
	}

	private void createSearchButton(Widget header) {
		searchButton = header.createChild(-1, WidgetType.GRAPHIC);
		setupButton(searchButton, SpriteID.GE_SEARCH, 5, "Open", this::openSearch);
	}

	private void setupButton(Widget button, int spriteId, int x, String action, Runnable onOpAction) {
		button.setSpriteId(spriteId);
		button.setOriginalWidth(18);
		button.setOriginalHeight(17);
		button.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		button.setOriginalX(x);
		button.setOriginalY(20);
		button.setHasListener(true);
		button.setAction(1, action);
		button.setOnOpListener((JavaScriptCallback) e -> onOpAction.run());
		button.setName("");
		button.revalidate();
	}

	private void toggleDisplay() {
		clientThread.invokeLater(() -> {
			client.playSoundEffect(SoundEffectID.UI_BOOP);
			Widget collectionView = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
			swapItems(collectionView);
		});
	}

	private void swapItems(Widget collectionView) {
		showRolled = !showRolled;

		int index = 0;
		for (CLogEntry display : clogEntries) {
			updateEntry(collectionView.getDynamicChildren()[index], display);
			index++;
		}
	}

	private void openSearch() {
		updateFilter("");
		client.playSoundEffect(SoundEffectID.UI_BOOP);
		searchButton.setAction(1, "Close");
		searchButton.setOnOpListener((JavaScriptCallback) e -> closeSearch());

		searchInput = chatboxPanelManager.openTextInput("Search unlock list")
										 .onChanged(s -> clientThread.invokeLater(() -> updateFilter(s.trim())))
										 .onClose(this::resetSearchButton)
										 .build();
	}

	private void closeSearch() {
		updateFilter("");
		chatboxPanelManager.close();
		client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	private void resetSearchButton() {
		clientThread.invokeLater(() -> updateFilter(""));
		searchButton.setOnOpListener((JavaScriptCallback) e -> openSearch());
		searchButton.setAction(1, "Open");
	}

	private void updateFilter(String input) {
		final Widget collectionView = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		if (collectionView == null) return;

		String filter = input.toLowerCase();
		updateList(collectionView, filter);
	}

	private void updateList(Widget collectionView, String filter) {
		if (entryWidgets == null) {
			entryWidgets = Arrays.stream(collectionView.getDynamicChildren())
								 .sorted(Comparator.comparing(Widget::getRelativeY))
								 .collect(Collectors.toList());
		}

		entryWidgets.forEach(w -> w.setHidden(true));

		Collection<Widget> matchingItems = entryWidgets.stream()
													   .filter(w -> w.getName().toLowerCase().contains(filter))
													   .collect(Collectors.toList());

		repositionMatchingItems(matchingItems);
		updateCurrentHeight(collectionView);
		setPendingScrollUpdate(true);
	}

	private void repositionMatchingItems(Collection<Widget> matchingItems) {
		int x = 0;
		int y = 0;
		for (Widget entry : matchingItems) {
			entry.setHidden(false);
			entry.setOriginalY(y);
			entry.setOriginalX(x);
			entry.revalidate();

			x = (x + X_INCREMENT) % 252;
			if (x == 0) {
				y += Y_INCREMENT;
			}
		}
	}

	private void updateScrollbar() {
		if (!isPendingScrollUpdate()) return;

		Widget collectionView = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		if (collectionView == null) return;

		Widget scrollbar = client.getWidget(COLLECTION_LOG_GROUP, COLLECTION_VIEW_SCROLLBAR);
		if (scrollbar == null) return;

		double scrollPerc = collectionView.getScrollY() / (double) collectionView.getScrollHeight();
		int scrollY = (int) (scrollPerc * currentHeight);

		collectionView.setScrollHeight(currentHeight);
		collectionView.revalidateScroll();

		client.runScript(ScriptID.UPDATE_SCROLLBAR, scrollbar.getId(), collectionView.getId(), currentHeight);
		collectionView.setScrollY(scrollY);
		scrollbar.setScrollY(0);
		collectionView.revalidateScroll();
		scrollbar.revalidateScroll();

		setPendingScrollUpdate(false);
	}

	private void updateCurrentHeight(Widget collectionView) {
		if (collectionView == null) return;

		Widget[] children = collectionView.getDynamicChildren();
		if (children == null) return;

		int visibleCount = (int) Arrays.stream(children)
									   .filter(Predicate.not(Widget::isHidden))
									   .count();

		currentHeight = (visibleCount + 5) / 6 * Y_INCREMENT;
	}

	private String progressTextHeader() {
		return "<col=ff0000>" + unlocked.size() + "<col=ffffff> / " + allTradeableItems.size();
	}
}