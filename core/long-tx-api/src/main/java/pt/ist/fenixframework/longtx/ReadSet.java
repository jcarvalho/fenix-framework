package pt.ist.fenixframework.longtx;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import pt.ist.fenixframework.DomainObject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public final class ReadSet {

    private final Set<DomainSlotKey> keys;

    ReadSet() {
        this.keys = Collections.emptySet();
    }

    private ReadSet(Set<DomainSlotKey> keys) {
        this.keys = Collections.unmodifiableSet(keys);
    }

    public ReadSet(JsonElement json) {
        Set<DomainSlotKey> keys = new HashSet<>();

        JsonArray elements = json.getAsJsonArray();

        for (JsonElement element : elements) {
            keys.add(new DomainSlotKey(element));
        }

        this.keys = Collections.unmodifiableSet(keys);
    }

    public ReadSet with(DomainObject ownerObject, String slotName) {
        DomainSlotKey slotKey = new DomainSlotKey(ownerObject, slotName);
        if (keys.contains(slotKey)) {
            return this;
        }
        Set<DomainSlotKey> duplicateSet = new HashSet<DomainSlotKey>(keys);
        duplicateSet.add(slotKey);
        return new ReadSet(duplicateSet);
    }

    public ReadSet with(Set<DomainSlotKey> newKeys) {
        if (this.keys.containsAll(newKeys)) {
            return this;
        }
        Set<DomainSlotKey> duplicateSet = new HashSet<>(keys);
        duplicateSet.addAll(newKeys);
        return new ReadSet(duplicateSet);
    }

    public Set<DomainSlotKey> getKeys() {
        return this.keys;
    }

    public JsonElement toJSON() {
        JsonArray array = new JsonArray();
        for (DomainSlotKey key : keys) {
            array.add(key.toJson());
        }
        return array;
    }

}
