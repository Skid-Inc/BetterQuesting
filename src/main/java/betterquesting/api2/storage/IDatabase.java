package betterquesting.api2.storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface IDatabase<T>
{
    /**
     * Adds a new entry with a fresh UUID into the database
     */
    @Nonnull
    DBEntry<T> add(@Nonnull T value);
    @Nonnull
    DBEntry<T> add(@Nonnull UUID uuid, @Nonnull T value);

    /**
     * @return true if the entry existed and was successfully removed
     */
    boolean removeUUID(@Nullable UUID key);
    /**
     * @return true if the entry existed and was successfully removed
     */
    boolean removeValue(@Nonnull T value);

    @Nullable
    UUID findUUID(@Nullable T value);
    @Nullable
    T getValue(@Nullable UUID uuid);
    
    int size();
    void reset();

    Set<Map.Entry<UUID, T>> getEntries();
}
