package betterquesting.api2.storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public abstract class SimpleDatabase<T> implements IDatabase<T>
{
    protected final HashMap<UUID, T> mapDB = new HashMap<>();

    @Nonnull
    @Override
    public UUID generateUUID() {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (mapDB.containsKey(id));
        return id;
    }

    @Override
    @Nonnull
    public DBEntry<T> add(@Nonnull UUID uuid, @Nonnull T value)
    {
        DBEntry<T> entry = new DBEntry<>(uuid, value);
        if(mapDB.putIfAbsent(uuid, value) == null)
        {
            return entry;
        } else
        {
            throw new IllegalArgumentException("ID or value is already contained within database");
        }
    }

    @Override
    public synchronized boolean removeUUID(@Nullable UUID key)
    {
        if (key == null) {return false;}
        return mapDB.remove(key) != null;
    }
    
    @Override
    public synchronized boolean removeValue(@Nonnull T value)
    {
        return removeUUID(findUUID(value));
    }
    
    @Override
    @Nullable
    public synchronized UUID findUUID(@Nullable T value)
    {
        if (value == null) {return null;}
        for(Map.Entry<UUID, T> entry : mapDB.entrySet())
        {
            if(entry.getValue() == value) return entry.getKey();
        }
        return null;
    }
    
    @Override
    @Nullable
    public synchronized T getValue(@Nullable UUID uuid)
    {
        if(uuid == null) return null;
        return mapDB.get(uuid);
    }
    
    @Override
    public synchronized int size()
    {
        return mapDB.size();
    }
    
    @Override
    public synchronized void reset()
    {
        mapDB.clear();
    }

    @Override
    public synchronized Set<Map.Entry<UUID, T>> getEntries()
    {
        return Collections.unmodifiableSet(mapDB.entrySet());
    }
}
