package betterquesting.api2.storage;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

public final class DBEntry<T> implements Comparable<DBEntry<T>> {
    private final UUID uuid;
    @Nonnull
    private final T obj;

    public DBEntry(@Nonnull UUID uuid, @Nonnull T obj) {
        if (uuid == null) {
            throw new IllegalArgumentException("Entry UUID cannot be negative");
        } else if (obj == null) {
            throw new NullPointerException("Entry value cannot be null");
        }

        this.uuid = uuid;
        this.obj = obj;
    }

    @Nonnull
    public UUID getUUID() {
        return this.uuid;
    }

    @Nonnull
    public T getValue() {
        return this.obj;
    }

    @Override
    public int compareTo(DBEntry<T> o) {
        return this.uuid.compareTo(o.uuid);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DBEntry)) {
            return false;
        }
        DBEntry<?> entry = (DBEntry<?>) other;
        return this.uuid.equals(entry.uuid) && Objects.equals(this.obj, entry.obj);
    }
}