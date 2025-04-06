package com.chanceman;

import com.chanceman.account.AccountManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
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
 * Manages the set of unlocked items.
 * This class persists the unlocked item IDs to a JSON file so that items remain unlocked across sessions.
 * It uses an asynchronous executor for saving the data.
 */
@Slf4j
@Singleton
public class UnlockedItemsManager
{
    private final Set<Integer> unlockedItems = Collections.synchronizedSet(new HashSet<>());
    @Inject private AccountManager accountManager;
    @Inject private Gson gson;
    @Setter private ExecutorService executor;

    public boolean ready() { return accountManager.getPlayerName() != null; }

    private File getFile()
    {
        return Path.of(RUNELITE_DIR.getPath(), "chanceman", accountManager.getPlayerName(), "chanceman_unlocked.json").toFile();
    }

	/**
     * Checks if an item is unlocked.
     *
     * @param itemId The item ID.
     * @return true if the item is unlocked, false otherwise.
     */
    public boolean isUnlocked(int itemId)
    {
        return unlockedItems.contains(itemId);
    }

    /**
     * Unlocks an item and saves the state.
     *
     * @param itemId The item ID to unlock.
     */
    public void unlockItem(int itemId)
    {
        unlockedItems.add(itemId);
        saveUnlockedItems();
    }

    /**
     * Loads the set of unlocked items from the JSON file.
     */
    public void loadUnlockedItems()
    {
        unlockedItems.clear();
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
                    unlockedItems.addAll(loaded);
                }
            }
            catch (IOException e)
            {
                log.error("Error loading unlocked items", e);
            }
        });
    }

    /**
     * Saves the current set of unlocked items asynchronously to the JSON file.
     */
    public synchronized void saveUnlockedItems()
    {
        executor.submit(() -> {
            File file = getFile();
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file))
            {
                gson.toJson(unlockedItems, writer);
            }
            catch (IOException e)
            {
                log.error("Error saving unlocked items", e);
            }
        });
    }

    /**
     * Retrieves an unmodifiable set of unlocked item IDs.
     *
     * @return An unmodifiable set of unlocked item IDs.
     */
    public Set<Integer> getUnlockedItems()
    {
        return Collections.unmodifiableSet(unlockedItems);
    }
}
