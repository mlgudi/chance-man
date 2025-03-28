package com.chanceman;

import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Panel for displaying rolled and unlocked items.
 * It provides UI for manual roll actions and displays item icons.
 */
public class ChanceManPanel extends PluginPanel
{
    private final UnlockedItemsManager unlockedItemsManager;
    private final RolledItemsManager rolledItemsManager;
    private final ItemManager itemManager;
    private final List<Integer> allTradeableItems;
    private final Random random = new Random();
    private final ClientThread clientThread;
    private final RollAnimationManager rollAnimationManager;

    // Cache item images to prevent redundant fetching
    private final Map<Integer, ImageIcon> itemIconCache = new HashMap<>();

    // Panels for displaying icons (their content scrolls via individual scroll panes)
    private final JPanel rolledPanel = new JPanel();
    private final JPanel unlockedPanel = new JPanel();
    private final JButton rollButton = new JButton("Roll");

    // Holds the search text
    private String searchText = "";

    // Count labels
    private final JLabel rolledCountLabel = new JLabel("Rolled: 0/0");
    private final JLabel unlockedCountLabel = new JLabel("Unlocked: 0/0");

    // Filter buttons and active filter state
    private JToggleButton filterUnlockedNotRolledButton;
    private JToggleButton filterUnlockedAndRolledButton;
    private String activeFilter = "NONE";

    /**
     * Constructs a ChanceManPanel.
     *
     * @param unlockedItemsManager Manager for unlocked items.
     * @param rolledItemsManager   Manager for rolled items.
     * @param itemManager          The item manager.
     * @param allTradeableItems    List of all tradeable item IDs.
     * @param clientThread         The client thread for scheduling UI updates.
     * @param rollAnimationManager The roll animation manager to trigger animations.
     */
    public ChanceManPanel(
            UnlockedItemsManager unlockedItemsManager,
            RolledItemsManager rolledItemsManager,
            ItemManager itemManager,
            List<Integer> allTradeableItems,
            ClientThread clientThread,
            RollAnimationManager rollAnimationManager
    )
    {
        this.unlockedItemsManager = unlockedItemsManager;
        this.rolledItemsManager = rolledItemsManager;
        this.itemManager = itemManager;
        this.allTradeableItems = allTradeableItems;
        this.clientThread = clientThread;
        this.rollAnimationManager = rollAnimationManager;
        init();
    }

    /**
     * Initializes the panel UI components.
     */
    private void init()
    {
        Font smallerFont = new Font("Arial", Font.BOLD, 11);
        rolledCountLabel.setFont(smallerFont);
        unlockedCountLabel.setFont(smallerFont);

        // Use BorderLayout so that header, search bar, and Roll button remain fixed.
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(45, 45, 45));

        // Header at the top (NORTH)
        JPanel headerPanel = buildHeaderPanel();

        // Search bar for filtering items
        JPanel searchBarPanel = buildSearchBar();

        // Combine header and search bar into a top panel
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(getBackground());
        topPanel.add(headerPanel);
        topPanel.add(searchBarPanel);

        // Add filter panel below the search bar
        topPanel.add(buildFilterPanel());
        add(topPanel, BorderLayout.NORTH);

        // Center: a panel with two columns side by side (rolled & unlocked)
        JPanel columnsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        columnsPanel.setBackground(getBackground());

        // Configure the rolled panel
        rolledPanel.setLayout(new BoxLayout(rolledPanel, BoxLayout.Y_AXIS));
        rolledPanel.setBackground(Color.DARK_GRAY);
        JScrollPane rolledScrollPane = new JScrollPane(
                rolledPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        rolledScrollPane.setPreferredSize(new Dimension(150, 300));

        // Rolled container with titled border
        JPanel rolledContainer = new JPanel(new BorderLayout());
        rolledContainer.setBorder(BorderFactory.createTitledBorder("Rolled Items"));
        rolledContainer.setBackground(getBackground());
        rolledContainer.add(rolledScrollPane, BorderLayout.CENTER);

        // Configure the unlocked panel
        unlockedPanel.setLayout(new BoxLayout(unlockedPanel, BoxLayout.Y_AXIS));
        unlockedPanel.setBackground(Color.DARK_GRAY);
        JScrollPane unlockedScrollPane = new JScrollPane(
                unlockedPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        unlockedScrollPane.setPreferredSize(new Dimension(150, 300));

        // Unlocked container with titled border
        JPanel unlockedContainer = new JPanel(new BorderLayout());
        unlockedContainer.setBorder(BorderFactory.createTitledBorder("Unlocked Items"));
        unlockedContainer.setBackground(getBackground());
        unlockedContainer.add(unlockedScrollPane, BorderLayout.CENTER);

        // Add both containers to the columns panel
        columnsPanel.add(rolledContainer);
        columnsPanel.add(unlockedContainer);
        add(columnsPanel, BorderLayout.CENTER);

        // Build a bottom panel that stacks two rows: one for the count labels, one for the Roll button
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(getBackground());

        // Row 1: Count labels
        JPanel countersPanel = new JPanel(new GridLayout(1, 2));
        countersPanel.setBackground(getBackground());

        // Left cell for Rolled: X/Y
        JPanel rolledLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rolledLabelPanel.setBackground(getBackground());
        rolledLabelPanel.add(rolledCountLabel);

        // Right cell for Unlocked: X/Y
        JPanel unlockedLabelPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        unlockedLabelPanel.setBackground(getBackground());
        unlockedLabelPanel.add(unlockedCountLabel);

        countersPanel.add(rolledLabelPanel);
        countersPanel.add(unlockedLabelPanel);

        // Row 2: Roll button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(getBackground());
        rollButton.addActionListener(this::performManualRoll);
        buttonPanel.add(rollButton);

        bottomPanel.add(countersPanel);
        bottomPanel.add(buttonPanel);

        add(bottomPanel, BorderLayout.SOUTH);

        // Populate the UI
        updatePanel();
    }

    /**
     * Builds the header panel with icon and title.
     */
    private JPanel buildHeaderPanel()
    {
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBackground(getBackground());

        ImageIcon headerIcon = new ImageIcon(getClass().getResource("/net/runelite/client/plugins/chanceman/icon.png"));
        JLabel iconLabel = new JLabel(headerIcon);

        JLabel titleLabel = new JLabel("Chance Man");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(new Color(200, 200, 200));

        headerPanel.add(iconLabel);
        headerPanel.add(titleLabel);
        return headerPanel;
    }

    /**
     * Builds the search bar panel.
     */
    private JPanel buildSearchBar()
    {
        JPanel searchBarPanel = new JPanel(new BorderLayout());
        searchBarPanel.setBackground(new Color(45, 45, 45));
        searchBarPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Create a container for the search icon and search field
        JPanel searchContainer = new JPanel(new BorderLayout());
        searchContainer.setBackground(new Color(30, 30, 30));
        searchContainer.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        // Search icon
        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setForeground(new Color(200, 200, 200));
        searchIcon.setBorder(new EmptyBorder(0, 5, 0, 5));

        // Search field
        JTextField searchField = new JTextField();
        searchField.setBackground(new Color(30, 30, 30));
        searchField.setForeground(Color.LIGHT_GRAY);
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        searchField.setCaretColor(Color.LIGHT_GRAY);

        // Listen for key releases to trigger real-time filtering
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                SwingUtilities.invokeLater(() -> {
                    searchText = searchField.getText().toLowerCase();
                    updatePanel();
                });
            }
        });

        searchContainer.add(searchIcon, BorderLayout.WEST);
        searchContainer.add(searchField, BorderLayout.CENTER);
        searchBarPanel.add(searchContainer, BorderLayout.CENTER);

        return searchBarPanel;
    }

    /**
     * Builds the filter panel containing two toggle buttons with emoji.
     */
    private JPanel buildFilterPanel()
    {
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        filterPanel.setBackground(getBackground());

        // Create the toggle buttons
        filterUnlockedNotRolledButton = new JToggleButton("🔓");
        filterUnlockedAndRolledButton = new JToggleButton("🔀");

        // Add tooltips to help users understand each filter
        filterUnlockedNotRolledButton.setToolTipText("Show items that are unlocked but not rolled");
        filterUnlockedAndRolledButton.setToolTipText("Show items that are both unlocked and rolled");

        // Add action listeners to update activeFilter and refresh the panel
        filterUnlockedNotRolledButton.addActionListener(e ->
        {
            if (filterUnlockedNotRolledButton.isSelected())
            {
                activeFilter = "UNLOCKED_NOT_ROLLED";
                // Deselect the other button
                filterUnlockedAndRolledButton.setSelected(false);
            }
            else
            {
                activeFilter = "NONE";
            }
            updatePanel();
        });

        filterUnlockedAndRolledButton.addActionListener(e ->
        {
            if (filterUnlockedAndRolledButton.isSelected())
            {
                activeFilter = "UNLOCKED_AND_ROLLED";
                // Deselect the other button
                filterUnlockedNotRolledButton.setSelected(false);
            }
            else
            {
                activeFilter = "NONE";
            }
            updatePanel();
        });

        filterPanel.add(filterUnlockedNotRolledButton);
        filterPanel.add(filterUnlockedAndRolledButton);

        return filterPanel;
    }

    /**
     * Triggers a manual roll animation when the Roll button is clicked.
     * If a roll is already in progress or if there are no locked items, it does nothing.
     *
     * @param e The action event.
     */
    private void performManualRoll(java.awt.event.ActionEvent e)
    {
        // Prevent triggering if a roll is already in progress.
        if (rollAnimationManager.isRolling())
        {
            return;
        }

        // Get list of locked items.
        List<Integer> locked = allTradeableItems.stream()
                .filter(id -> !unlockedItemsManager.isUnlocked(id))
                .collect(Collectors.toList());

        if (locked.isEmpty())
        {
            JOptionPane.showMessageDialog(
                    this,
                    "All items are unlocked!",
                    "ChanceMan",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // Choose a random locked item.
        int randomItemId = locked.get(random.nextInt(locked.size()));

        // Enqueue a roll request for this item.
        rollAnimationManager.enqueueRoll(randomItemId);
    }

    /**
     * Updates the panel UI with the current rolled and unlocked items.
     * Also updates count labels with rolled/unlocked item amounts.
     * This method fetches item definitions on the client thread to avoid thread errors,
     * then updates the UI on the Swing thread.
     */
    public void updatePanel()
    {
        clientThread.invokeLater(() ->
        {
            List<Integer> filteredRolled = new ArrayList<>();
            for (Integer id : rolledItemsManager.getRolledItems())
            {
                ItemComposition comp = itemManager.getItemComposition(id);
                if (comp != null)
                {
                    String name = comp.getName().toLowerCase();
                    if (searchText.isEmpty() || name.contains(searchText))
                    {
                        filteredRolled.add(id);
                    }
                }
            }

            List<Integer> filteredUnlocked = new ArrayList<>();
            for (Integer id : unlockedItemsManager.getUnlockedItems())
            {
                ItemComposition comp = itemManager.getItemComposition(id);
                if (comp != null)
                {
                    String name = comp.getName().toLowerCase();
                    if (searchText.isEmpty() || name.contains(searchText))
                    {
                        filteredUnlocked.add(id);
                    }
                }
            }

            // Apply filtering
            if (activeFilter.equals("UNLOCKED_NOT_ROLLED"))
            {
                filteredUnlocked.removeIf(id -> rolledItemsManager.getRolledItems().contains(id));
                filteredRolled.clear();
            }
            else if (activeFilter.equals("UNLOCKED_AND_ROLLED"))
            {
                filteredUnlocked.removeIf(id -> !rolledItemsManager.getRolledItems().contains(id));
                filteredRolled.removeIf(id -> !unlockedItemsManager.getUnlockedItems().contains(id));
            }

            int totalTrackable = allTradeableItems.size();
            int rolledCount = rolledItemsManager.getRolledItems().size();
            int unlockedCount = unlockedItemsManager.getUnlockedItems().size();

            SwingUtilities.invokeLater(() ->
            {
                // Update the labels: "Rolled X/Y" and "Unlocked X/Y"
                rolledCountLabel.setText("Rolled: " + rolledCount + "/" + totalTrackable);
                unlockedCountLabel.setText("Unlocked: " + unlockedCount + "/" + totalTrackable);

                rolledPanel.removeAll();
                for (Integer id : filteredRolled)
                {
                    ImageIcon icon = getItemIcon(id);
                    if (icon != null)
                    {
                        JLabel label = new JLabel(icon);
                        label.setToolTipText("Loading...");
                        rolledPanel.add(label);
                        // Asynchronously get item name for the tooltip
                        getItemNameAsync(id, name ->
                        {
                            label.setToolTipText(name);
                            label.repaint();
                        });
                    }
                }

                unlockedPanel.removeAll();
                for (Integer id : filteredUnlocked)
                {
                    ImageIcon icon = getItemIcon(id);
                    if (icon != null)
                    {
                        JLabel label = new JLabel(icon);
                        label.setToolTipText("Loading...");
                        unlockedPanel.add(label);
                        // Asynchronously get item name for the tooltip
                        getItemNameAsync(id, name ->
                        {
                            label.setToolTipText(name);
                            label.repaint();
                        });
                    }
                }
                // Revalidate & repaint panels
                rolledPanel.revalidate();
                rolledPanel.repaint();
                unlockedPanel.revalidate();
                unlockedPanel.repaint();
            });
        });
    }

    /**
     * Retrieves the item icon for a given item ID, caching the result.
     *
     * @param itemId The item ID.
     * @return The ImageIcon of the item, or null if not available.
     */
    private ImageIcon getItemIcon(int itemId)
    {
        if (itemIconCache.containsKey(itemId))
        {
            return itemIconCache.get(itemId);
        }

        BufferedImage image = itemManager.getImage(itemId, 1, false);
        if (image == null)
        {
            return null;
        }

        ImageIcon icon = new ImageIcon(image);
        itemIconCache.put(itemId, icon);
        return icon;
    }

    /**
     * Asynchronously retrieves the item name for a given item ID and passes it to the provided callback.
     *
     * @param itemId   The item ID.
     * @param callback Consumer to receive the item name.
     */
    private void getItemNameAsync(int itemId, Consumer<String> callback)
    {
        clientThread.invokeLater(() -> {
            ItemComposition comp = itemManager.getItemComposition(itemId);
            String name = (comp != null) ? comp.getName() : "Unknown";
            SwingUtilities.invokeLater(() -> callback.accept(name));
        });
    }
}
