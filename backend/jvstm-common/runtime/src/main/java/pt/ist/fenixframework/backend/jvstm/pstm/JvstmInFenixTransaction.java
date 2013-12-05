package pt.ist.fenixframework.backend.jvstm.pstm;

public interface JvstmInFenixTransaction {

    public void setReadOnly();

    public boolean txAllowsWrite();

//    public void logRelationAdd(String relationName, AbstractDomainObject o1, AbstractDomainObject o2);
//
//    public void logRelationRemove(String relationName, AbstractDomainObject o1, AbstractDomainObject o2);

    // these should be inherited from a Transaction inferface in JVSTM (if only it existed)

    public <T> T getBoxValue(VBox<T> vbox);

    public boolean isBoxValueLoaded(VBox vbox);

    /**
     * Get the value of the given {@link VBox} in a previous snapshot
     * of the world.
     * 
     * Note that reads to previous versions should not be put in the
     * read-set, as the programmer is aware that he is getting an older
     * version.
     * 
     * @param vbox
     *            The box to be read
     * @param version
     *            The version of to read
     * @return
     *         The value of the box in the given version
     */
    public <T> T getPreviousBoxValue(VBox<T> vbox, int version);

}
