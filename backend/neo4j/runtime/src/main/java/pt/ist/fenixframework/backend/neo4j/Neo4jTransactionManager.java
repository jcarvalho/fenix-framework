package pt.ist.fenixframework.backend.neo4j;

import java.lang.annotation.Annotation;
import java.util.concurrent.Callable;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.kernel.DeadlockDetectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.CallableWithoutException;
import pt.ist.fenixframework.CommitListener;
import pt.ist.fenixframework.core.AbstractTransactionManager;

public class Neo4jTransactionManager extends AbstractTransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jTransactionManager.class);

    private static final Atomic DEFAULT_ATOMIC = new Atomic() {

        @Override
        public Class<? extends Annotation> annotationType() {
            return Atomic.class;
        }

        @Override
        public TxMode mode() {
            return TxMode.SPECULATIVE_READ;
        }

        @Override
        public boolean flattenNested() {
            return true;
        }
    };

    private final ThreadLocal<Neo4jTransaction> transactions = new ThreadLocal<>();

    private final GraphDatabaseService graphDb;

    public Neo4jTransactionManager(GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }

    @Override
    public Neo4jTransaction getTransaction() {
        return transactions.get();
    }

    @Override
    public <T> T withTransaction(CallableWithoutException<T> command) {
        try {
            return withTransaction(command, DEFAULT_ATOMIC);
        } catch (Exception e) {
            throw new RuntimeException("CallableWithoutException has thrown an Exception...", e);
        }
    }

    @Override
    public <T> T withTransaction(Callable<T> command) throws Exception {
        return withTransaction(command, DEFAULT_ATOMIC);
    }

    @Override
    public <T> T withTransaction(Callable<T> command, Atomic atomic) throws Exception {
        Neo4jTransaction current = getTransaction();

        // If we are inside a transaction and we can flatten nested
        // transactions, just invoke the command directly
        if (current != null && atomic.flattenNested()) {
            return command.call();
        }

        boolean isPromoted = current != null;

        if (isPromoted) {
            // Commit the current transaction
            commit();
        }

        int tries = 0;
        while (true) {
            try {
                begin();
                T result = command.call();
                commit();
                return result;
            } catch (DeadlockDetectedException | TransactionFailureException e) {
                logger.debug("Neo4j exception detected! {}", e.getMessage());
                if (tries++ > 200) {
                    throw new RuntimeException("Sorry, too many restarts...");
                }
            }
        }
    }

    @Override
    public void addCommitListener(CommitListener listener) {
        // Temporary
    }

    @Override
    public void begin(boolean readOnly) throws NotSupportedException, SystemException {
        logger.trace("Beginning Transaction");
        transactions.set(new Neo4jTransaction(graphDb.beginTx()));
    }

    @Override
    public void resume(javax.transaction.Transaction tobj) throws InvalidTransactionException, IllegalStateException,
            SystemException {
        if (!(tobj instanceof Neo4jTransaction)) {
            throw new InvalidTransactionException(String.valueOf(tobj));
        }

        this.transactions.set((Neo4jTransaction) tobj);
    }

    @Override
    public void setTransactionTimeout(int seconds) throws SystemException {
        throw new UnsupportedOperationException("Timeouts are not supported.");
    }

    @Override
    public Neo4jTransaction suspend() throws SystemException {
        Neo4jTransaction tx = this.transactions.get();
        this.transactions.remove();
        return tx;
    }

    @Override
    protected void backendCommit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException,
            SecurityException, IllegalStateException, SystemException {
        logger.trace("Comitting transaction");
        try {
            this.transactions.get().commit();
        } finally {
            this.transactions.remove();
        }
    }

    @Override
    protected void backendRollback() throws SecurityException, SystemException {
        logger.trace("Rolling back transaction");
        try {
            this.transactions.get().rollback();
        } finally {
            this.transactions.remove();
        }
    }
}
