package com.chanceman;

import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
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
        // Use BorderLayout so that header and Roll button remain fixed.
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(45, 45, 45));

        // Header at the top (NORTH)
        JPanel headerPanel = buildHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Center: a panel with two columns side by side (rolled & unlocked)
        // Each column is wrapped in its own JScrollPane so that only the column scrolls.
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
        // Optionally set a preferred size on the scroll pane (e.g., width)
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

        // Add the columns panel to the center region (it will not scroll as a whole)
        add(columnsPanel, BorderLayout.CENTER);

        // Roll button at the bottom (SOUTH)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(getBackground());
        rollButton.addActionListener(this::performManualRoll);
        buttonPanel.add(rollButton);
        add(buttonPanel, BorderLayout.SOUTH);

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
     * Triggers a manual roll animation when the Roll button is clicked.
     * If a roll is already in progress or if there are no locked items, it does nothing.
     *
     * @param e The action event.
     */
    private void performManualRoll(ActionEvent e)
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
     */
    public void updatePanel()
    {
        SwingUtilities.invokeLater(() ->
        {
            Set<Integer> rolled = rolledItemsManager.getRolledItems();
            Set<Integer> unlocked = unlockedItemsManager.getUnlockedItems();

            rolledPanel.removeAll();
            for (Integer id : rolled)
            {
                ImageIcon icon = getItemIcon(id);
                if (icon != null)
                {
                    JLabel label = new JLabel(icon);
                    label.setToolTipText("Loading...");
                    rolledPanel.add(label);
                    getItemNameAsync(id, name -> {
                        label.setToolTipText(name);
                        label.repaint();
                    });
                }
            }

            unlockedPanel.removeAll();
            for (Integer id : unlocked)
            {
                ImageIcon icon = getItemIcon(id);
                if (icon != null)
                {
                    JLabel label = new JLabel(icon);
                    label.setToolTipText("Loading...");
                    unlockedPanel.add(label);
                    getItemNameAsync(id, name -> {
                        label.setToolTipText(name);
                        label.repaint();
                    });
                }
            }

            rolledPanel.revalidate();
            rolledPanel.repaint();
            unlockedPanel.revalidate();
            unlockedPanel.repaint();
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
