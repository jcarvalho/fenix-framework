package pt.ist.fenixframework.backend.jvstmojb.repository;

import pt.ist.fenixframework.DomainModelUtil;
import pt.ist.fenixframework.DomainObject;
import pt.ist.fenixframework.backend.jvstmojb.ojb.OJBMetadataGenerator;
import pt.ist.fenixframework.dml.DomainClass;
import pt.ist.fenixframework.dml.Role;
import pt.ist.fenixframework.dml.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DomainModelMetadata {

    private static final ConcurrentMap<Class<? extends DomainObject>, DomainModelMetadata> metadataCache =
            new ConcurrentHashMap<>();

    public static DomainModelMetadata getMetadataForType(Class<? extends DomainObject> type) {
        DomainModelMetadata metadata = metadataCache.get(type);
        if (metadata == null) {
            metadata = new DomainModelMetadata(type);
            metadataCache.putIfAbsent(type, metadata);
        }
        return metadata;
    }

    private final String tableName;
    private final String deleteQuery;
    private final String objectReloadQuery;

    public DomainModelMetadata(Class<? extends DomainObject> type) {
        DomainClass domClass = DomainModelUtil.getDomainClassFor(type);
        this.tableName = OJBMetadataGenerator.getExpectedTableName(domClass);

        List<String> slots = computeSlots(domClass);

        this.deleteQuery = "DELETE FROM `" + tableName + "` WHERE OID = ";
        this.objectReloadQuery = computeObjectReloadQuery(slots, tableName);
    }

    public String getTableName() {
        return tableName;
    }

    public String getDeleteQuery(long oid) {
        return deleteQuery + oid;
    }

    public String getObjectReloadQuery() {
        return objectReloadQuery;
    }

    private List<String> computeSlots(DomainClass domClass) {
        List<String> slots = new ArrayList<>();
        while (domClass != null) {
            for (Slot slot : domClass.getSlotsList()) {
                slots.add("`" + DbUtil.convertToDBStyle(slot.getName()) + "`");
            }
            for (Role role : domClass.getRoleSlotsList()) {
                if (role.getName() != null && role.getMultiplicityUpper() == 1) {
                    slots.add(DbUtil.getFkName(role.getName()));
                }
            }
            domClass = (DomainClass) domClass.getSuperclass();
        }
        return slots;
    }

    private String computeObjectReloadQuery(List<String> slots, String tableName) {
        StringBuilder slotList = new StringBuilder();
        for (String slot : slots) {
            if (slotList.length() > 0) {
                slotList.append(',');
            }
            slotList.append(slot);
        }
        return "SELECT " + slotList.toString() + " FROM `" + tableName + "` WHERE OID = ?";
    }


}
