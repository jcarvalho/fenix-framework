package pt.ist.fenixframework.longtx;

import java.util.Map;
import java.util.Set;

import pt.ist.fenixframework.DomainObject;
import pt.ist.fenixframework.core.TransactionError;

import com.google.gson.JsonElement;

/**
 * A {@link TransactionalContext} is the basic building block for
 * Long-Lived Transactions.
 * 
 * Every operation for a given Long-Lived Transaction must run within
 * the {@link TransactionalContext} for that Transaction.
 * 
 * 
 * @author João Pedro Carvalho (joao.pedro.carvalho@ist.utl.pt)
 * 
 */
public final class TransactionalContext extends TransactionalContext_Base {

    /**
     * Creates a new {@link TransactionalContext} with the given name.
     */
    public TransactionalContext(String name) {
        super();
        setName(name);
        setReadSet(new ReadSet());
        setWriteSet(new WriteSet());
        setState(TransactionalContextState.STARTED);
    }

    /**
     * Commits the {@link TransactionalContext}, ensuring every write
     * performed within any of its sub-transactions is visible to the
     * outside world. Not that it requires that the context is NOT bound
     * to any thread.
     * 
     * @param alsoDelete
     *            If the context is to be deleted upon commit success.
     * @throws TransactionError
     *             If a conflict has been detected, and the changes
     *             performed by this context could not be committed.
     *             Note that if a {@link TransactionError} is thrown, the
     *             transaction MUST be manually rolled-back.
     */
    public void commit(boolean alsoDelete) throws TransactionError {
        if (!getState().equals(TransactionalContextState.STARTED)) {
            throw new IllegalStateException("Cannot commit transaction in state " + getState());
        }

        try {
            LongTransaction.getLongTransactionBackEnd().commitContext(this);
            setState(TransactionalContextState.COMMITTED);
            if (alsoDelete) {
                delete();
            }
        } catch (TransactionError e) {
            setState(TransactionalContextState.CONFLICT);
            throw e;
        }
    }

    /**
     * Discards the writes performed in all sub-transactions of the
     * current {@link TransactionalContext}.
     * 
     * @param alsoDelete
     *            If the context is to be deleted upon success.
     */
    public void rollback(boolean alsoDelete) {
        if (getState().equals(TransactionalContextState.COMMITTED)) {
            throw new IllegalStateException("Cannot rollback a committed transaction!");
        }

        LongTransaction.getLongTransactionBackEnd().rollbackContext(this);
        setState(TransactionalContextState.ROLLED_BACK);

        if (alsoDelete) {
            delete();
        }
    }

    /**
     * Marks the specified slot of the given {@link DomainObject} as read by
     * this Long-Lived Transaction.
     * 
     * If this slot has been previously read, this method is a no-op.
     * 
     * @param ownerObject
     *            The accessed object
     * @param slotName
     *            The name of the accessed slot
     */
    public void addReadSetEntry(DomainObject ownerObject, String slotName) {
        ReadSet readSet = getReadSet();

        ReadSet newReadSet = readSet.with(ownerObject, slotName);

        if (readSet != newReadSet) {
            this.setReadSet(newReadSet);
        }
    }

    public void addReadSetEntries(Set<DomainSlotKey> keys) {
        ReadSet readSet = getReadSet();

        ReadSet newReadSet = readSet.with(keys);

        if (readSet != newReadSet) {
            this.setReadSet(newReadSet);
        }
    }

    public void addWriteSetEntries(Map<DomainSlotKey, JsonElement> newEntries) {
        WriteSet writeSet = getWriteSet();

        WriteSet newWriteSet = writeSet.with(newEntries);

        if (writeSet != newWriteSet) {
            this.setWriteSet(newWriteSet);
        }
    }

    /**
     * Deletes the {@link TransactionalContext} as well as the {@link LogEntry}s
     * in the Read Set and Write Set.
     */
    public void delete() {
        deleteDomainObject();
    }
}
