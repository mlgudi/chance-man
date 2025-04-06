package com.chanceman;

import com.chanceman.account.AccountManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

/**
 * Manages the set of rolled items.
 * This class persists the rolled item IDs to a JSON file so that rolled items remain tracked across sessions.
 */
@Slf4j
public class RolledItemsManager
{
    private final Set<Integer> rolledItems = Collections.synchronizedSet(new HashSet<>());
    @Inject private AccountManager accountManager;
    @Inject private Gson gson;
    @Setter private ExecutorService executor;

    private File getFile()
    {
        return Path.of(RUNELITE_DIR.getPath(), "chanceman", accountManager.getPlayerName(), "chanceman_rolled.json").toFile();
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
        rolledItems.clear();
        executor.submit(() -> {
            File file = getFile();
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
            File file = getFile();
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
