package pt.ist.fenixframework.backend.neo4j;

import java.io.Serializable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Partial;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import pt.ist.fenixframework.DomainObject;
import pt.ist.fenixframework.FenixFramework;
import pt.ist.fenixframework.core.AbstractDomainObjectAdapter;
import pt.ist.fenixframework.core.DomainObjectAllocator;
import pt.ist.fenixframework.core.SharedIdentityMap;
import pt.ist.fenixframework.util.JsonConverter;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public abstract class Neo4jDomainObject extends AbstractDomainObjectAdapter {

    private long oid;
    private final Node node;

    protected Neo4jDomainObject() {
        super();
        this.node = getRepository().createNewNode(this.getClass(), this.oid);
    }

    protected Neo4jDomainObject(DomainObjectAllocator.OID oid) {
        super(oid);
        if (oid.oid instanceof Long) {
            this.oid = (Long) oid.oid;
            this.node = getRepository().getNodeForObject(this);
        } else {
            this.node = (Node) oid.oid;
            this.oid = (Long) node.getProperty(Neo4jRepository.OID_PROPERTY);
        }
    }

    @Override
    protected final void ensureOid() {
        // find successive ids until one is available
        while (true) {
            this.oid = DomainClassInfo.getNextOidFor(this.getClass());
            Object cached = SharedIdentityMap.getCache().cache(this);
            if (cached == this) {
                // break the loop once we got this instance cached
                return;
            }
        }
    }

    @Override
    public final Long getOid() {
        return oid;
    }

    @Override
    public final String getExternalId() {
        return String.valueOf(getOid());
    }

    @Override
    protected void deleteDomainObject() {
        getRepository().deleteDomainObject(this);
    }

    private Neo4jRepository getRepository() {
        return FenixFramework.<Neo4jConfig> getConfig().getBackEnd().getRepository();
    }

    public final Node get$node() {
        return node;
    }

    // Node helper methods

    protected final <T extends Neo4jDomainObject> T getDomainObject(RelationshipType type, Direction direction) {
        Relationship rel = node.getSingleRelationship(type, direction);
        if (rel == null) {
            return null;
        } else {
            return Neo4jBackEnd.relationshipToObject(get$node(), rel);
        }
    }

    protected final <T extends DomainObject> void setDomainObject(RelationshipType type, Direction direction, T value) {
        Relationship rel = node.getSingleRelationship(type, direction);
        if (rel != null) {
            rel.delete();
        }
        if (value != null) {
            if (direction == Direction.INCOMING) {
                ((Neo4jDomainObject) value).node.createRelationshipTo(node, type);
            } else {
                node.createRelationshipTo(((Neo4jDomainObject) value).node, type);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected final <T> T getPropertyValue(String slotName) {
        if (node.hasProperty(slotName)) {
            return (T) node.getProperty(slotName);
        }
        return null;
    }

    protected final <T> void setPropertyValue(String slotName, T value) {
        if (value == null) {
            node.removeProperty(slotName);
        } else {
            node.setProperty(slotName, value);
        }
    }

    // Specialized getPropertyValue

    protected final <T extends Enum<T>> T getEnumPropertyValue(String slotName, Class<T> enumClass) {
        String value = getPropertyValue(slotName);
        return value == null ? null : Enum.valueOf(enumClass, value);
    }

    protected final DateTime getDateTimePropertyValue(String slotName) {
        Long millis = getPropertyValue(slotName);
        return millis == null ? null : new DateTime(millis);
    }

    protected final LocalTime getLocalTimePropertyValue(String slotName) {
        String value = getPropertyValue(slotName);
        return value == null ? null : new LocalTime(value);
    }

    protected final LocalDate getLocalDatePropertyValue(String slotName) {
        String value = getPropertyValue(slotName);
        return value == null ? null : new LocalDate(value);
    }

    private static final JsonParser PARSER = new JsonParser();

    protected final JsonElement getJsonElementPropertyValue(String slotName) {
        String value = getPropertyValue(slotName);
        return value == null ? null : PARSER.parse(value);
    }

    protected final Partial getPartialPropertyValue(String slotName) {
        JsonElement value = getJsonElementPropertyValue(slotName);
        return value == null ? null : JsonConverter.getPartialFromJson(value);
    }

    protected final Serializable getSerializablePropertyValue(String slotName) {
        JsonElement value = getJsonElementPropertyValue(slotName);
        return value == null ? null : JsonConverter.getSerializableFromJson(value);
    }

    // Specialized setPropertyValue

    protected final void setEnumPropertyValue(String slotName, Enum<?> value) {
        if (value == null) {
            node.removeProperty(slotName);
        } else {
            node.setProperty(slotName, value.name());
        }
    }

    protected final void setDateTimePropertyValue(String slotName, DateTime value) {
        if (value == null) {
            node.removeProperty(slotName);
        } else {
            node.setProperty(slotName, value.getMillis());
        }
    }

    protected final void setLocalTimePropertyValue(String slotName, LocalTime value) {
        if (value == null) {
            node.removeProperty(slotName);
        } else {
            node.setProperty(slotName, value.toString());
        }
    }

    protected final void setLocalDatePropertyValue(String slotName, LocalDate value) {
        if (value == null) {
            node.removeProperty(slotName);
        } else {
            node.setProperty(slotName, value.toString());
        }
    }

    protected final void setJsonElementPropertyValue(String slotName, JsonElement value) {
        if (value == null) {
            node.removeProperty(slotName);
        } else {
            node.setProperty(slotName, value.toString());
        }
    }

    protected final void setPartialPropertyValue(String slotName, Partial value) {
        if (value == null) {
            node.removeProperty(slotName);
        } else {
            node.setProperty(slotName, JsonConverter.getJsonFor(value).toString());
        }
    }

    protected final void setSerializablePropertyValue(String slotName, Serializable value) {
        if (value == null) {
            node.removeProperty(slotName);
        } else {
            node.setProperty(slotName, JsonConverter.getJsonFor(value).toString());
        }
    }
}
