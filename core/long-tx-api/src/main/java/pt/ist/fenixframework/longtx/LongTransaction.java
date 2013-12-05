package pt.ist.fenixframework.longtx;

import pt.ist.fenixframework.FenixFramework;

/**
 * Entry Point for Long-Lived Transactions.
 * 
 * Applications that wish to use Long-Lived Transactions must
 * create a {@link TransactionalContext}, and set bind it to the current
 * thread. This will ensure that every transaction in this thread
 * is run within the context of the Transaction.
 * 
 * @author João Pedro Carvalho (joao.pedro.carvalho@ist.utl.pt)
 * 
 */
public final class LongTransaction {

    public static TransactionalContext getContextForThread() {
        return getLongTransactionBackEnd().getContextForThread();
    }

    public static void removeContextFromThread() {
        getLongTransactionBackEnd().removeContextFromThread();
    }

    public static void setContextForThread(TransactionalContext context) {
        getLongTransactionBackEnd().setContextForThread(context);
    }

    static final LongTransactionBackEnd getLongTransactionBackEnd() {
        return (LongTransactionBackEnd) FenixFramework.getConfig().getBackEnd();
    }
}
