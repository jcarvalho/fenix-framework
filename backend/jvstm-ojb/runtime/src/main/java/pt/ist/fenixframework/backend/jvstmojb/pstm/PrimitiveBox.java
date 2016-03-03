package pt.ist.fenixframework.backend.jvstmojb.pstm;

import jvstm.VBoxBody;

import pt.ist.fenixframework.DomainObject;
import pt.ist.fenixframework.backend.jvstmojb.repository.DomainRepository;

import java.sql.SQLException;

class PrimitiveBox<E> extends VBox<E> {

    PrimitiveBox(DomainObject ownerObj, String slotName) {
        super(ownerObj, slotName);
    }

    PrimitiveBox(DomainObject ownerObj, String slotName, VBoxBody<E> body) {
        super(ownerObj, slotName, body);
    }

    @Override
    protected void doReload(Object obj, String attr) throws SQLException {
        DomainRepository.reloadObject((OneBoxDomainObject) obj, TransactionSupport.getCurrentSQLConnection());
    }
}
