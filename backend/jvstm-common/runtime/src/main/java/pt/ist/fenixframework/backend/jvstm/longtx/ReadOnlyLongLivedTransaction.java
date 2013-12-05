package pt.ist.fenixframework.backend.jvstm.longtx;

import jvstm.VBox;
import pt.ist.fenixframework.backend.jvstm.pstm.JvstmInFenixTransaction;
import pt.ist.fenixframework.backend.jvstm.pstm.OwnedVBox;
import pt.ist.fenixframework.core.WriteOnReadError;
import pt.ist.fenixframework.longtx.TransactionalContext;

public class ReadOnlyLongLivedTransaction extends LongLivedTransaction {

    public ReadOnlyLongLivedTransaction(TransactionalContext context, JvstmInFenixTransaction parent) {
        super(context, parent);
    }

    @Override
    public void setReadOnly() {
        // No-op. Already inside a read transaction
    }

    @Override
    public <T> void setBoxValue(VBox<T> box, T value) {
        OwnedVBox<T> vbox = (OwnedVBox<T>) box;
        if (vbox.getOwnerObject().getClass().getName().startsWith("pt.ist.fenixframework.longtx")) {
            // Allow writes for LogEntries
            super.setBoxValue(vbox, value);
        } else {
            throw new WriteOnReadError();
        }
    }

    @Override
    public boolean txAllowsWrite() {
        return false;
    }

}
