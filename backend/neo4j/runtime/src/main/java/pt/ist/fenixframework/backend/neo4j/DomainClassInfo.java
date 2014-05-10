package pt.ist.fenixframework.backend.neo4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.index.lucene.QueryContext;
import org.neo4j.tooling.GlobalGraphOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.ist.fenixframework.DomainRoot;
import pt.ist.fenixframework.backend.neo4j.Neo4jRepository.BuiltinLabels;
import pt.ist.fenixframework.dml.DomainClass;
import pt.ist.fenixframework.dml.DomainModel;

public class DomainClassInfo {

    private static final Logger logger = LoggerFactory.getLogger(DomainClassInfo.class);

    private volatile static Map<Class<?>, DomainClassInfo> classInfoMap;

    private volatile static DomainClassInfo[] classInfoById;

    private volatile static long serverOidBase;

    static void initializeClassInfos(DomainModel model, int serverId, GraphDatabaseService graphDb) {
        serverOidBase = (long) serverId << 48;  // the server id provides de 16 most significant bits of the OID

        try (Transaction tx = graphDb.beginTx()) {
            int maxId = 0;

            Map<Class<?>, DomainClassInfo> map = new IdentityHashMap<>();
            ArrayList<DomainClassInfo> array = new ArrayList<DomainClassInfo>();

            GlobalGraphOperations ops = GlobalGraphOperations.at(graphDb);

            for (Node node : ops.getAllNodesWithLabel(BuiltinLabels.FF_DOMAIN_CLASS_INFO)) {
                String className = (String) node.getProperty("domainClass");
                int cid = (int) node.getProperty("classId");

                Index<Node> index = graphDb.index().forNodes(className);

                DomainClassInfo info = new DomainClassInfo(node, findClass(className), cid);

                long from = serverOidBase + ((long) cid << 32);
                long to = from + 0xFFFFFFFFL;

                IndexHits<Node> hits =
                        index.query(QueryContext.numericRange(Neo4jRepository.OID_PROPERTY, from, to).sortNumeric(
                                Neo4jRepository.OID_PROPERTY, true));

                if (hits.hasNext()) {
                    long maxOid = (long) hits.next().getProperty(Neo4jRepository.OID_PROPERTY);
                    info.setLastKey((int) maxOid);
                    logger.debug("Setting lastKey for {} to {}", className, (int) maxOid);
                }

                hits.close();

                maxId = Math.max(maxId, cid);

                addNewInfo(map, array, info);
            }

            // create all other records, skipping DomainRoot of course
            for (DomainClass domClass : model.getDomainClasses()) {
                boolean isDomainRoot = domClass.getFullName().equals(DomainRoot.class.getName());

                Class<?> javaClass = findClass(domClass.getFullName());
                if (!map.containsKey(javaClass)) {
                    Node node = graphDb.createNode(BuiltinLabels.FF_DOMAIN_CLASS_INFO);
                    DomainClassInfo dci = new DomainClassInfo(node, javaClass, isDomainRoot ? 0 : ++maxId);

                    addNewInfo(map, array, dci);
                    logger.info("Registered new domain class '{}' with id {}", javaClass.getName(), dci.classId);

                    node.setProperty("domainClass", javaClass.getName());
                    node.setProperty("classId", dci.classId);
                }
            }

            // finish the initialization by assigning to the static variables
            classInfoMap = Collections.unmodifiableMap(map);
            classInfoById = new DomainClassInfo[maxId + 1];
            array.toArray(classInfoById);
            tx.success();
        }
    }

    private static void addNewInfo(Map<Class<?>, DomainClassInfo> map, ArrayList<DomainClassInfo> array, DomainClassInfo info) {
        if (info.domainClass != null) {
            map.put(info.domainClass, info);
        }

        int index = info.classId;
        int size = array.size();
        if (size <= index) {
            array.ensureCapacity(index + 1);
            while (size < index) {
                array.add(null);
                size++;
            }
            array.add(info);
        } else {
            array.set(info.classId, info);
        }
    }

    public static int mapClassToId(Class<?> objClass) {
        DomainClassInfo domainClassInfo = classInfoMap.get(objClass);
        if (domainClassInfo == null) {
            throw new RuntimeException("Domain class not registered: " + objClass.getCanonicalName());
        }
        return domainClassInfo.classId;
    }

    private static Class<?> mapIdToClass(int cid) {
        if ((cid < 0) || (cid >= classInfoById.length)) {
            return null;
        } else {
            return classInfoById[cid].domainClass;
        }
    }

    private static int mapOidToClassId(long oid) {
        if (oid == 1) {
            return 0;
        } else {
            return (((int) (oid >> 32)) & 0x0000FFFF); // shift class id to
            // rightmost position and
            // clear server id bits
        }
    }

    public static Class<?> mapOidToClass(long oid) {
        return mapIdToClass(mapOidToClassId(oid));
    }

    public static long getNextOidFor(Class<?> objClass) {
        int nextKey;
        DomainClassInfo info = classInfoMap.get(objClass);

        synchronized (info) {
            int lastKey = info.getLastKey();
            nextKey = lastKey + 1;
            info.setLastKey(nextKey);
        }

        if (logger.isDebugEnabled()) {
            StringBuilder message = new StringBuilder();
            message.append("New OID: Server(");
            message.append((int) (serverOidBase >> 48));
            message.append("), Class(");
            message.append(info.classId);
            message.append("), Object(");
            message.append(nextKey);
            message.append("): ");
            message.append(serverOidBase);
            message.append(" + ");
            message.append(((long) info.classId << 32));
            message.append(" + ");
            message.append(nextKey);
            message.append(" = ");
            message.append((serverOidBase + ((long) info.classId << 32) + nextKey));
            logger.debug(message.toString());
        }
        // build and return OID
        return serverOidBase + ((long) info.classId << 32) + nextKey;
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // the non-static part starts here

    public final Class<?> domainClass;
    public final int classId;
    /** The maximum object key used for objects of this class in this server */
    private transient int lastKey = 0;

    public DomainClassInfo(Node node, Class<?> domainClass, int classId) {
        this.domainClass = domainClass;
        this.classId = classId;
    }

    protected int getLastKey() {
        return this.lastKey;
    }

    protected void setLastKey(int lastKey) {
        this.lastKey = lastKey;
    }

    @Override
    public String toString() {
        return "DomainClassInfo for class '" + domainClass.getName() + "' with id: " + classId;
    }

}
