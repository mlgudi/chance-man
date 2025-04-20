package com.chanceman;

import com.chanceman.account.AccountManager;
import com.google.gson.Gson;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Comparator;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

/**
 * Manages the set of rolled items with atomic persistence.
 * Provides thread-safe operations for marking items as rolled,
 * loading from and saving to disk with backups and atomic moves.
 */
@Slf4j
@Singleton
public class RolledItemsManager
{
    private static final int MAX_BACKUPS = 10;
    private final Set<Integer> rolledItems = Collections.synchronizedSet(new LinkedHashSet<>());

    @Inject private AccountManager accountManager;
    @Inject private Gson gson;
    @Setter private ExecutorService executor;

    /**
     * Builds the file path for the current account's rolled-items JSON file.
     *
     * @return path to the rolled items JSON file
     */
    private Path getFilePath()
    {
        return Path.of(RUNELITE_DIR.getPath(), "chanceman", accountManager.getPlayerName(), "chanceman_rolled.json");
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
     * Marks an item as rolled and triggers an asynchronous save.
     *
     * @param itemId The item ID to mark as rolled.
     */
    public void markRolled(int itemId)
    {
        if (rolledItems.add(itemId))
        {
            saveRolledItems();
        }
    }

    /**
     * Loads the set of rolled items from disk into memory.
     * If the file does not exist or is empty, initializes an empty set.
     */
    public void loadRolledItems()
    {
        rolledItems.clear();
        Path file = getFilePath();
        try
        {
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > 0)
            {
                Set<Integer> loaded = gson.fromJson(Files.newBufferedReader(file),
                        new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
                if (loaded != null)
                {
                    rolledItems.addAll(loaded);
                }
            }
        }
        catch (IOException e)
        {
            log.error("Error loading rolled items", e);
        }
    }

    /**
     * Saves the current set of rolled items to disk.
     * Uses a temporary file and backups for atomicity and data safety.
     */
    public void saveRolledItems()
    {
        executor.submit(() -> {
            Path file = getFilePath();
            try
            {
                Files.createDirectories(file.getParent());

                // --- rotate backups ---
                if (Files.exists(file))
                {
                    Path backupsDir = file.getParent().resolve("backups");
                    Files.createDirectories(backupsDir);

                    String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                    Path backupFile = backupsDir.resolve(file.getFileName() + "." + ts + ".bak");
                    Files.copy(file, backupFile, StandardCopyOption.COPY_ATTRIBUTES);

                    // prune older backups, keep only the newest MAX_BACKUPS
                    try (Stream<Path> stream = Files.list(backupsDir))
                    {
                        List<Path> sorted = stream
                                .filter(p -> p.getFileName().toString().startsWith(file.getFileName().toString() + "."))
                                .sorted(Comparator.comparing(Path::getFileName).reversed())
                                .collect(Collectors.toList());

                        for (int i = MAX_BACKUPS; i < sorted.size(); i++)
                        {
                            Files.deleteIfExists(sorted.get(i));
                        }
                    }
                }

                // --- write new state atomically ---
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (BufferedWriter writer = Files.newBufferedWriter(tmp))
                {
                    gson.toJson(rolledItems, writer);
                }
                Files.move(tmp, file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
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