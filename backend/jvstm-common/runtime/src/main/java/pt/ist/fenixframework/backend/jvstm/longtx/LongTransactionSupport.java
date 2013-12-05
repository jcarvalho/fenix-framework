package pt.ist.fenixframework.backend.jvstm.longtx;

import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.ist.fenixframework.backend.jvstm.JVSTMDomainObject;
import pt.ist.fenixframework.backend.jvstm.pstm.VBox;
import pt.ist.fenixframework.core.TransactionError;
import pt.ist.fenixframework.longtx.DomainSlotKey;
import pt.ist.fenixframework.longtx.ReadSet;
import pt.ist.fenixframework.longtx.TransactionalContext;
import pt.ist.fenixframework.longtx.WriteSet;

import com.google.gson.JsonElement;

public class LongTransactionSupport {

    private static final Logger logger = LoggerFactory.getLogger(LongTransactionSupport.class);

    private static final ThreadLocal<TransactionalContext> contexts = new ThreadLocal<TransactionalContext>();

    public static TransactionalContext getContextForThread() {
        return contexts.get();
    }

    public static void setContextForThread(TransactionalContext context) {
        if (contexts.get() != null) {
            throw new IllegalStateException("Already inside a Transactional Context");
        }
        contexts.set(context);
    }

    public static void removeContextFromThread() {
        contexts.remove();
    }

    public static void rollbackContext(TransactionalContext transactionalContext) {
        // No-Op, deleting the context is enough for rollback
    }

    public static boolean isInsideContext() {
        return contexts.get() != null;
    }

    // Worker methods

    public static void commitContext(TransactionalContext context) throws TransactionError {
        // Don't do anything if nothing was written
        if (!context.getWriteSet().isEmpty()) {
            if (!validateContext(context)) {
                throw new TransactionError();
            }
            mergeContext(context);
        }
    }

    private static boolean validateContext(TransactionalContext context) {
        int version = context.getVersion();

        ReadSet readSet = context.getReadSet();
        for (DomainSlotKey key : readSet.getKeys()) {
            JVSTMDomainObject owner = (JVSTMDomainObject) key.ownerObject;

            VBox<?> box = owner.getBoxForSlot(key.slotName);

            // Read the box to ensure it is put in the read set of the current transaction.
            // This way, if a concurrent transaction writes to this box between this check
            // and the commit of this transaction, it will abort and re-check
            box.get();

            if (box.body.version > version) {
                // This means that the box was written after the Long Transaction started,
                // meaning it has to be aborted.
                //
                // While this is the best approach in terms of correctness, it has great
                // limitations, due to the size of the transaction.

                logger.debug("Long Transaction {} aborted because of box: {}. Current version: {}, Tx version: {}",
                        context.getExternalId(), box, box.body.version, version);
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void mergeContext(TransactionalContext context) {
        WriteSet writeSet = context.getWriteSet();
        for (Entry<DomainSlotKey, JsonElement> entry : writeSet.entrySet()) {

            JVSTMDomainObject owner = (JVSTMDomainObject) entry.getKey().ownerObject;

            VBox box = owner.getBoxForSlot(entry.getKey().slotName);
            if (entry.getValue().isJsonNull()) {
                box.put(null);
            } else {
                box.put(owner.getValueFromJSON(entry.getKey().slotName, entry.getValue()));
            }
        }
    }
}
