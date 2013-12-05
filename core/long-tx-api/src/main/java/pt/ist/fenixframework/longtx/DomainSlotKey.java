package pt.ist.fenixframework.longtx;

import pt.ist.fenixframework.DomainObject;
import pt.ist.fenixframework.FenixFramework;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class DomainSlotKey {

    public final DomainObject ownerObject;

    public final String slotName;

    private int hash = 0;

    public DomainSlotKey(DomainObject ownerObject, String slotName) {
        this.ownerObject = ownerObject;
        this.slotName = slotName;
    }

    DomainSlotKey(JsonElement json) {
        String[] parts = json.getAsString().split(":");
        this.ownerObject = FenixFramework.getDomainObject(parts[0]);
        this.slotName = parts[1];
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DomainSlotKey) {
            DomainSlotKey other = (DomainSlotKey) obj;
            return other.ownerObject.equals(this.ownerObject) && other.slotName.equals(this.slotName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            h = ownerObject.hashCode() + slotName.hashCode();
            hash = h;
        }
        return h;
    }

    public JsonElement toJson() {
        return new JsonPrimitive(ownerObject.getExternalId() + ":" + slotName);
    }

}
