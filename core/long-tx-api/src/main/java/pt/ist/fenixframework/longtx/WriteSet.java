package pt.ist.fenixframework.longtx;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonElement;

public final class WriteSet {

    private static final int MAX_SET_SIZE = Integer.parseInt(System.getProperty("MAX_WRITE_SET_SIZE", "1000"));

    private final Map<DomainSlotKey, JsonElement> entries;
    private final WriteSet next;

    public WriteSet(JsonElement json) {
        this.entries = new HashMap<>();
        this.next = null;
    }

    public WriteSet() {
        this.entries = Collections.emptyMap();
        this.next = null;
    }

    private WriteSet(Map<DomainSlotKey, JsonElement> entries, WriteSet next) {
        this.entries = Collections.unmodifiableMap(entries);
        this.next = next;
    }

    public WriteSet with(Map<DomainSlotKey, JsonElement> newEntries) {
        if (newEntries.isEmpty()) {
            return this;
        } else if (this.entries.size() < MAX_SET_SIZE) {
            for (Entry<DomainSlotKey, JsonElement> entry : this.entries.entrySet()) {
                if (!newEntries.containsKey(entry.getKey())) {
                    newEntries.put(entry.getKey(), entry.getValue());
                }
            }
            return new WriteSet(newEntries, this.next);
        } else {
            return new WriteSet(newEntries, this);
        }
    }

    public JsonElement getContentsFor(DomainSlotKey slotKey) {
        JsonElement value = this.entries.get(slotKey);
        if (value != null) {
            return value;
        }
        if (this.next == null) {
            return null;
        }
        return next.getContentsFor(slotKey);
    }

    public boolean isEmpty() {
        // If the WriteSet is empty, only one node is created,
        // and it must be this one, so perform only a local check.
        return entries.isEmpty();
    }

    public JsonElement toJSON() {
        return null;
    }

    public WriteSet getNext() {
        return next;
    }

    public Set<Entry<DomainSlotKey, JsonElement>> entrySet() {
        Map<DomainSlotKey, JsonElement> finalMap = new HashMap<>();
        WriteSet currentSet = this;
        while (currentSet != null) {
            for (Entry<DomainSlotKey, JsonElement> entry : currentSet.entries.entrySet()) {
                if (!finalMap.containsKey(entry.getKey())) {
                    finalMap.put(entry.getKey(), entry.getValue());
                }
            }
            currentSet = currentSet.next;
        }
        return finalMap.entrySet();
    }
}
