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
 * Manages the set of unlocked items with robust, atomic persistence and 10‑file backup rotation.
 */
@Slf4j
@Singleton
public class UnlockedItemsManager
{
    private static final int MAX_BACKUPS = 10;

    private final Set<Integer> unlockedItems = Collections.synchronizedSet(new LinkedHashSet<>());

    @Inject private AccountManager accountManager;
    @Inject private Gson gson;
    @Setter private ExecutorService executor;

    public boolean ready()
    {
        return accountManager.getPlayerName() != null;
    }

    private Path getFilePath()
    {
        return Path.of(RUNELITE_DIR.getPath(),
                "chanceman",
                accountManager.getPlayerName(),
                "chanceman_unlocked.json");
    }

    public boolean isUnlocked(int itemId)
    {
        return unlockedItems.contains(itemId);
    }

    public void unlockItem(int itemId)
    {
        if (unlockedItems.add(itemId))
        {
            saveUnlockedItems();
        }
    }

    public void loadUnlockedItems()
    {
        unlockedItems.clear();
        Path file = getFilePath();
        try
        {
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > 0)
            {
                Set<Integer> loaded = gson.fromJson(
                        Files.newBufferedReader(file),
                        new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
                if (loaded != null)
                {
                    unlockedItems.addAll(loaded);
                }
            }
        }
        catch (IOException e)
        {
            log.error("Error loading unlocked items", e);
        }
    }

    public void saveUnlockedItems()
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
                    gson.toJson(unlockedItems, writer);
                }
                Files.move(tmp, file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
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
