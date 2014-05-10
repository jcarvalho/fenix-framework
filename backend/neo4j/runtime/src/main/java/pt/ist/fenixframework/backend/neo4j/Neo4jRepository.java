package pt.ist.fenixframework.backend.neo4j;

import static org.neo4j.index.lucene.ValueContext.numeric;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.HighlyAvailableGraphDatabaseFactory;
import org.neo4j.graphdb.schema.ConstraintDefinition;
import org.neo4j.graphdb.schema.ConstraintType;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.tooling.GlobalGraphOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.ist.fenixframework.DomainRoot;
import pt.ist.fenixframework.FenixFramework;

final class Neo4jRepository {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jRepository.class);
    static final String OID_PROPERTY = "oid";

    private final GraphDatabaseService graphDb;

    Neo4jRepository(Neo4jConfig config) {
        if (config.useHA) {
            Map<String, String> properties = new HashMap<>();

            properties.put("ha.server_id", config.haServerId);
            properties.put("ha.initial_hosts", config.haInitialHosts);
            properties.put("ha.cluster_server", config.haClusterServer);
            properties.put("ha.server", config.haServer);
            if (config.onlineBackupServer != null) {
                properties.put("online_backup_server", config.onlineBackupServer);
            }

            logger.info("Server {} attempting to join cluster {}", config.haServerId, config.haInitialHosts);

            graphDb =
                    new HighlyAvailableGraphDatabaseFactory().newHighlyAvailableDatabaseBuilder(config.databasePath)
                            .setConfig(properties).newGraphDatabase();

            logger.info("Cluster successfully joined!");
        } else {
            graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(config.databasePath).newGraphDatabase();
        }

        bootstrapSchemas();
        ensureDomainRoot();

        DomainClassInfo.initializeClassInfos(FenixFramework.getDomainModel(), 0, graphDb);
    }

    private void bootstrapSchemas() {
        try (Transaction tx = graphDb.beginTx()) {
            Schema schema = graphDb.schema();
            if (!hasConstraint(schema.getConstraints(BuiltinLabels.FF_DOMAIN_CLASS_INFO), "domainClass")) {
                logger.info("Bootstrapped DCI schema");
                schema.constraintFor(BuiltinLabels.FF_DOMAIN_CLASS_INFO).assertPropertyIsUnique("domainClass").create();
            }
            tx.success();
        }
    }

    private boolean hasConstraint(Iterable<ConstraintDefinition> constraints, String constraintName) {
        for (ConstraintDefinition constraint : constraints) {
            if (constraint.isConstraintType(ConstraintType.UNIQUENESS)) {
                for (String propertyKey : constraint.getPropertyKeys()) {
                    if (propertyKey.equals(constraintName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    enum BuiltinLabels implements Label {
        FF_DOMAIN_ROOT, FF_DOMAIN_CLASS_INFO;
    }

    private void ensureDomainRoot() {
        try (Transaction tx = graphDb.beginTx()) {
            ResourceIterable<Node> roots = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(BuiltinLabels.FF_DOMAIN_ROOT);
            if (!roots.iterator().hasNext()) {
                Node node = createNewNode(DomainRoot.class, 1l);
                node.addLabel(BuiltinLabels.FF_DOMAIN_ROOT);
                logger.info("Bootstrapped DomainRoot on node {}", node);
            }
            tx.success();
        }
    }

    Node createNewNode(Class<?> clazz, Long oid) {
        Node node = graphDb.createNode(DynamicLabel.label(clazz.getName().replace('.', '_')));

        graphDb.index().forNodes(clazz.getName()).add(node, OID_PROPERTY, numeric(oid));

        node.setProperty(OID_PROPERTY, oid);
        return node;
    }

    Node getNodeForObject(Neo4jDomainObject object) {
        return graphDb.index().forNodes(object.getClass().getName()).get(OID_PROPERTY, numeric(object.getOid())).getSingle();
    }

    void deleteDomainObject(Neo4jDomainObject object) {
        graphDb.index().forNodes(object.getClass().getName()).remove(object.get$node());
        object.get$node().delete();
    }

    void shutdown() {
        graphDb.shutdown();
    }

    GraphDatabaseService getGraphDb() {
        return graphDb;
    }
}
