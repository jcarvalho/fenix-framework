package pt.ist.fenixframework.backend.jvstm.longtx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import jvstm.NestedTransaction;
import jvstm.ReadWriteTransaction;
import jvstm.Transaction;
import jvstm.VBoxBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.ist.fenixframework.backend.jvstm.JVSTMDomainObject;
import pt.ist.fenixframework.backend.jvstm.pstm.JvstmInFenixTransaction;
import pt.ist.fenixframework.backend.jvstm.pstm.OwnedVBox;
import pt.ist.fenixframework.backend.jvstm.pstm.VBox;
import pt.ist.fenixframework.longtx.DomainSlotKey;
import pt.ist.fenixframework.longtx.TransactionalContext;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

public class LongLivedTransaction extends NestedTransaction implements JvstmInFenixTransaction {

    private static final Logger logger = LoggerFactory.getLogger(LongLivedTransaction.class);

    private final TransactionalContext context;
    private final JvstmInFenixTransaction parent;
    private final int version;

    private final Map<VBox<?>, Object> contextCache = new HashMap<VBox<?>, Object>();

    public LongLivedTransaction(TransactionalContext context, JvstmInFenixTransaction parent) {
        super((ReadWriteTransaction) parent);
        this.context = context;
        this.parent = parent;
        if (context.getVersion() == null) {
            context.setVersion(Transaction.current().getNumber());
            logger.debug("Setting version of {} to {}", context, context.getVersion());
        }
        this.version = context.getVersion();

        logger.debug("Beggining new LongLivedTransaction with context {} and parent {}", context, parent);
    }

    /**
     */
    @Override
    public <T> T getBoxValue(jvstm.VBox<T> vbox) {
        throw new UnsupportedOperationException("Cannot deal with native VBoxes...");
    }

    @Override
    public Transaction makeNestedTransaction(boolean readOnly) {
        return new NestedTransaction(this);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void tryCommit() {
        // Go through the ReadSet and the WriteSet
        // No validations are performed here, since the purpose of this
        // transaction is to manipulate the read/write set.

        Map<jvstm.VBox, Object> originalWriteSet = new HashMap<jvstm.VBox, Object>(boxesWritten);
        Set<jvstm.VBox> originalReadSet = new HashSet<>(bodiesRead.keySet());

        // Clear write-set, will be populated with LogEntries
        boxesWritten.clear();
        bodiesRead.clear();

        Map<DomainSlotKey, JsonElement> writeSetEntries = new HashMap<>(originalWriteSet.size());

        for (Entry<jvstm.VBox, Object> entry : originalWriteSet.entrySet()) {
            OwnedVBox<?> vbox = (OwnedVBox<?>) entry.getKey();

            JVSTMDomainObject owner = vbox.getOwnerObject();

            JsonElement json =
                    entry.getValue().equals(NULL_VALUE) ? JsonNull.INSTANCE : owner.getJSONElementForSlot(vbox.getSlotName(),
                            entry.getValue());

            writeSetEntries.put(new DomainSlotKey(owner, vbox.getSlotName()), json);
        }

        context.addWriteSetEntries(writeSetEntries);

        Set<DomainSlotKey> readSetKeys = new HashSet<>();
        for (jvstm.VBox box : originalReadSet) {
            OwnedVBox vbox = (OwnedVBox) box;
            readSetKeys.add(new DomainSlotKey(vbox.getOwnerObject(), vbox.getSlotName()));
        }

        context.addReadSetEntries(readSetKeys);

        // Add the new read/written boxes to the parent transaction, which
        // will ensure the LogEntries are properly committed
        super.tryCommit();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public <T> T getBoxValue(VBox<T> vbox) {
        T result = getLocalValue(vbox);
        if (result == null) {
            OwnedVBox<T> ownedVbox = (OwnedVBox<T>) vbox;
            if (ownedVbox.getOwnerObject().getClass().getName().startsWith("pt.ist.fenixframework.longtx")) {
                // For classes annotated with @NoLogEntries
                // always check the parent transaction, in the current version
                return parent.getBoxValue(vbox);
            }

            result = lookupBoxValueInContext(ownedVbox);
            if (result == null) {
                result = parent.getPreviousBoxValue(vbox, version);

                if (bodiesRead == EMPTY_MAP) {
                    bodiesRead = new HashMap<jvstm.VBox, VBoxBody>();
                }
                bodiesRead.put(vbox, null);
            }
        }

        return result == NULL_VALUE ? null : result;
    }

    @SuppressWarnings("unchecked")
    private final <T> T lookupBoxValueInContext(OwnedVBox<T> vbox) {
        if (contextCache.containsKey(vbox)) {
            return (T) contextCache.get(vbox);
        }

        DomainSlotKey key = new DomainSlotKey(vbox.getOwnerObject(), vbox.getSlotName());
        JsonElement contents = context.getWriteSet().getContentsFor(key);

        if (contents != null) {
            T value = contents.isJsonNull() ? null : (T) vbox.getOwnerObject().getValueFromJSON(vbox.getSlotName(), contents);
            contextCache.put(vbox, value);
            return value;
        }

        return null;
    }

    @Override
    public boolean isBoxValueLoaded(VBox<?> vbox) {
        return parent.isBoxValueLoaded(vbox);
    }

    @Override
    public void setReadOnly() {
        parent.setReadOnly();
    }

    @Override
    public boolean txAllowsWrite() {
        return parent.txAllowsWrite();
    }

    @Override
    public <T> T getPreviousBoxValue(VBox<T> vbox, int version) {
        return parent.getPreviousBoxValue(vbox, version);
    }

}
