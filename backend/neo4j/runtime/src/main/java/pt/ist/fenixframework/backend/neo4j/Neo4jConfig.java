package pt.ist.fenixframework.backend.neo4j;

import pt.ist.fenixframework.Config;

public class Neo4jConfig extends Config {

    String databasePath;

    boolean useHA = false;

    String haServerId;

    String haInitialHosts;

    String haClusterServer;

    String onlineBackupServer;

    String haServer;

    protected void useHAFromString(String value) {
        this.useHA = Boolean.valueOf(value);
    }

    // Config API

    protected Neo4jBackEnd backEnd;

    @Override
    protected void init() {
        checkRequired(databasePath, "databasePath");
        if (useHA) {
            checkRequired(haServerId, "haServerId");
            checkRequired(haInitialHosts, "haInitialHosts");
            checkRequired(haClusterServer, "haClusterServer");
            checkRequired(haServer, "haServer");
        }
        this.backEnd = new Neo4jBackEnd(this);
    }

    @Override
    public Neo4jBackEnd getBackEnd() {
        return this.backEnd;
    }

    @Override
    public String getBackEndName() {
        return Neo4jBackEnd.BACKEND_NAME;
    }
}
