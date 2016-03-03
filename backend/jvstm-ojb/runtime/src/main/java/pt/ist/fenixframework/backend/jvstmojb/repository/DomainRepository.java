package pt.ist.fenixframework.backend.jvstmojb.repository;

import pt.ist.fenixframework.backend.jvstmojb.pstm.OneBoxDomainObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DomainRepository {

    public static void reloadObject(OneBoxDomainObject object, Connection conn) throws SQLException {
        DomainModelMetadata metadata = DomainModelMetadata.getMetadataForType(object.getClass());
        PreparedStatement stmt = conn.prepareStatement(metadata.getObjectReloadQuery());
        stmt.setLong(1, object.getOid());
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            object.readFromResultSet(rs);
        }
    }

}
