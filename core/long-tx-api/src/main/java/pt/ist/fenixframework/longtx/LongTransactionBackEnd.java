package pt.ist.fenixframework.longtx;

import pt.ist.fenixframework.core.TransactionError;

public interface LongTransactionBackEnd {

    public TransactionalContext getContextForThread();

    public void setContextForThread(TransactionalContext context);

    public void removeContextFromThread();

    public void commitContext(TransactionalContext context) throws TransactionError;

    public void rollbackContext(TransactionalContext transactionalContext);

}
