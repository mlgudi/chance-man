package com.chanceman;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Manages the set of rolled items.
 * This class persists the rolled item IDs to a JSON file so that rolled items remain tracked across sessions.
 */
@Slf4j
public class RolledItemsManager
{
    private final Set<Integer> rolledItems = Collections.synchronizedSet(new HashSet<>());
    private final String filePath;
    private final Gson gson;
    private final ExecutorService executor;

    /**
     * Constructs a RolledItemsManager for a given player.
     *
     * @param playerName The player's name.
     */
    public RolledItemsManager(String playerName, Gson gson, ExecutorService executor)
    {
        this.gson = gson;
        this.executor = executor;
        filePath = RuneLite.RUNELITE_DIR + File.separator +
                "chanceman" + File.separator +
                playerName + File.separator +
                "chanceman_rolled.json";
    }


    /**
     * Checks if an item has been rolled.
     *
     * @param itemId The item ID.
     * @return true if the item has been rolled, false otherwise.
     */
    public boolean isRolled(int itemId)
    {
        return rolledItems.contains(itemId);
    }

    /**
     * Marks an item as rolled and saves the state.
     *
     * @param itemId The item ID to mark as rolled.
     */
    public void markRolled(int itemId)
    {
        rolledItems.add(itemId);
        saveRolledItems();
    }

    /**
     * Loads the set of rolled items from the JSON file.
     */
    public void loadRolledItems()
    {
        executor.submit(() -> {
            File file = new File(filePath);
            if (!file.exists())
            {
                file.getParentFile().mkdirs();
                return;
            }
            try (FileReader reader = new FileReader(file))
            {
                Type setType = new TypeToken<Set<Integer>>() {}.getType();
                Set<Integer> loaded = gson.fromJson(reader, setType);
                if (loaded != null)
                {
                    rolledItems.addAll(loaded);
                }
            }
            catch (IOException e)
            {
                log.error("Error loading rolled items", e);
            }
        });
    }

    /**
     * Saves the current set of rolled items to the JSON file.
     */
    public synchronized void saveRolledItems()
    {
        executor.submit(() -> {
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file))
            {
                gson.toJson(rolledItems, writer);
            }
            catch (IOException e)
            {
                log.error("Error saving rolled items", e);
            }
        });
    }

    /**
     * Retrieves an unmodifiable set of rolled item IDs.
     *
     * @return An unmodifiable set of rolled item IDs.
     */
    public Set<Integer> getRolledItems()
    {
        return Collections.unmodifiableSet(rolledItems);
    }
}
