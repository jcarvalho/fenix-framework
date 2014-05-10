package pt.ist.fenixframework.backend.neo4j;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import org.neo4j.graphdb.Transaction;

import pt.ist.fenixframework.FenixFramework;
import pt.ist.fenixframework.core.AbstractTransaction;
import pt.ist.fenixframework.txintrospector.TxIntrospector;

public class Neo4jTransaction extends AbstractTransaction {

    private final Transaction underlying;

    Neo4jTransaction(Transaction underlying) {
        this.underlying = underlying;
    }

    @Override
    protected void backendCommit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException,
            SecurityException, IllegalStateException, SystemException {
        underlying.success();
        underlying.close();
    }

    @Override
    protected void backendRollback() throws IllegalStateException, SystemException {
        underlying.failure();
        underlying.close();
    }

    @Override
    public TxIntrospector getTxIntrospector() {
        // NYI
        return null;
    }

    public static Transaction getCurrentTx() {
        Neo4jTransaction tx = (Neo4jTransaction) FenixFramework.getTransaction();
        return tx == null ? null : tx.underlying;
    }

}
