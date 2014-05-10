package pt.ist.fenixframework.backend.neo4j;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import pt.ist.fenixframework.DomainObject;
import pt.ist.fenixframework.DomainRoot;
import pt.ist.fenixframework.TransactionManager;
import pt.ist.fenixframework.backend.BackEnd;
import pt.ist.fenixframework.core.AbstractDomainObject;
import pt.ist.fenixframework.core.DomainObjectAllocator;
import pt.ist.fenixframework.core.SharedIdentityMap;

public class Neo4jBackEnd implements BackEnd {

    public static final String BACKEND_NAME = "neo4j";

    protected final TransactionManager transactionManager;
    protected final Neo4jRepository repository;

    public Neo4jBackEnd(Neo4jConfig config) {
        this.repository = new Neo4jRepository(config);
        this.transactionManager = new Neo4jTransactionManager(repository.getGraphDb());
    }

    @Override
    public String getName() {
        return BACKEND_NAME;
    }

    @Override
    public DomainRoot getDomainRoot() {
        return fromOid(1L);
    }

    @Override
    public <T extends DomainObject> T getDomainObject(String externalId) {
        if (externalId == null || externalId.isEmpty()) {
            return null;
        }
        return fromOid(Long.parseLong(externalId));
    }

    @Override
    public TransactionManager getTransactionManager() {
        return this.transactionManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DomainObject> T fromOid(Object oid) {
        AbstractDomainObject obj = SharedIdentityMap.getCache().lookup(oid);
        if (obj == null) {
            obj = DomainObjectAllocator.allocateObject(DomainClassInfo.mapOidToClass((Long) oid), oid);
            obj = SharedIdentityMap.getCache().cache(obj);
        }
        return (T) obj;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Neo4jDomainObject> T forNode(Node node) {
        Long oid = (Long) node.getProperty(Neo4jRepository.OID_PROPERTY);
        AbstractDomainObject obj = SharedIdentityMap.getCache().lookup(oid);
        if (obj == null) {
            obj = DomainObjectAllocator.allocateObject(DomainClassInfo.mapOidToClass(oid), node);
            obj = SharedIdentityMap.getCache().cache(obj);
        }
        return (T) obj;
    }

    @Override
    public void shutdown() {
        repository.shutdown();
    }

    public Neo4jRepository getRepository() {
        return repository;
    }

    static <T extends Neo4jDomainObject> T relationshipToObject(Node node, Relationship relationship) {
        return forNode(relationship.getOtherNode(node));
    }

    @Override
    public boolean isNewInstance() {
        return false;
    }
}
