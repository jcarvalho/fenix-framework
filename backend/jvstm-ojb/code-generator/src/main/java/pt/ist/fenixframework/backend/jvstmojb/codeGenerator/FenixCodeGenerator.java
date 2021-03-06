package pt.ist.fenixframework.backend.jvstmojb.codeGenerator;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import pt.ist.fenixframework.backend.jvstmojb.JvstmOJBBackEnd;
import pt.ist.fenixframework.backend.jvstmojb.JvstmOJBConfig;
import pt.ist.fenixframework.backend.jvstmojb.dml.runtime.ConsistencyChecks;
import pt.ist.fenixframework.backend.jvstmojb.ojb.OJBFunctionalSetWrapper;
import pt.ist.fenixframework.backend.jvstmojb.pstm.AbstractDomainObject;
import pt.ist.fenixframework.backend.jvstmojb.pstm.LoggingRelation;
import pt.ist.fenixframework.backend.jvstmojb.pstm.RelationList;
import pt.ist.fenixframework.backend.jvstmojb.pstm.TransactionSupport;
import pt.ist.fenixframework.backend.jvstmojb.repository.DbUtil;
import pt.ist.fenixframework.backend.jvstmojb.repository.ResultSetReader;
import pt.ist.fenixframework.backend.jvstmojb.repository.ToSqlConverter;
import pt.ist.fenixframework.dml.CodeGenerator;
import pt.ist.fenixframework.dml.CompilerArgs;
import pt.ist.fenixframework.dml.DomainClass;
import pt.ist.fenixframework.dml.DomainEntity;
import pt.ist.fenixframework.dml.DomainModel;
import pt.ist.fenixframework.dml.ExternalizationElement;
import pt.ist.fenixframework.dml.Role;
import pt.ist.fenixframework.dml.Slot;
import pt.ist.fenixframework.dml.ValueType;
import pt.ist.fenixframework.dml.ValueTypeSerializationGenerator;

public abstract class FenixCodeGenerator extends CodeGenerator {

    protected static final String TO_SQL_CONVERTER_CLASS = ToSqlConverter.class.getName();

    protected static final String RESULT_SET_READER_CLASS = ResultSetReader.class.getName();

    protected static final String[] IMPORTS = new String[] { RelationList.class.getName(),
            OJBFunctionalSetWrapper.class.getName(), ValueTypeSerializationGenerator.SERIALIZER_CLASS_FULL_NAME };

    protected static final String DOMAIN_CLASS_ROOT = AbstractDomainObject.class.getName();

    protected static final String DIRECT_RELATION_TYPE_CLASS = LoggingRelation.class.getName();

    protected static final String TRANSACTION_SUPPORT_CLASS = TransactionSupport.class.getName();

    private final ValueType jsonElementVt;

    public FenixCodeGenerator(CompilerArgs compArgs, DomainModel domainModel) {
        super(compArgs, domainModel);
        jsonElementVt = domainModel.findValueType("JsonElement");
    }

    @Override
    protected String getDomainClassRoot() {
        return DOMAIN_CLASS_ROOT;
    }

    protected String[] getImportFiles() {
        return IMPORTS;
    }

    @Override
    protected void generateFilePreamble(String subPackageName, PrintWriter out) {
        generatePackageDecl(subPackageName, out);
        for (String importFile : getImportFiles()) {
            println(out, "import " + importFile + ";");
        }
        newline(out);
    }

    @Override
    protected void generateBaseClassBody(DomainClass domClass, PrintWriter out) {
        super.generateBaseClassBody(domClass, out);
        generateCheckDisconnected(domClass, out);
        generateDatabaseReader(domClass, out);
        generateSlotConsistencyPredicates(domClass, out);
    }

    @Override
    protected void generateBaseClassConstructorsBody(DomainClass domClass, PrintWriter out) {
        super.generateBaseClassConstructorsBody(domClass, out);
        final Slot ojbConcreteClassSlot = domClass.findSlot("ojbConcreteClass");
        if (ojbConcreteClassSlot != null && calculateHierarchyLevel(domClass) == 1) {
            newline(out);
            print(out, "setOjbConcreteClass(getClass().getName());");
        }
    }

    private int calculateHierarchyLevel(DomainEntity domainEntity) {
        return hasSuperclass(domainEntity) ? calculateHierarchyLevel(((DomainClass) domainEntity).getSuperclass()) + 1 : 0;
    }

    private boolean hasSuperclass(DomainEntity domainEntity) {
        return domainEntity != null && domainEntity instanceof DomainClass
                && ((DomainClass) domainEntity).getSuperclass() != null;
    }

    @Override
    protected String getDirectRelationType() {
        return DIRECT_RELATION_TYPE_CLASS;
    }

    @Override
    protected void generateStaticRelationSlots(Role role, PrintWriter out) {
        super.generateStaticRelationSlots(role, out);

        if (role.isDirect()) {
            String relationName = role.getRelation().getName();

            // set the relationName of the LoggingRelation object
            newline(out);
            printWords(out, "static");
            newBlock(out);
            printWords(out, relationName + ".relation");
            print(out, ".setRelationName(\"");
            print(out, getTypeFullName(role.getOtherRole().getType()));
            print(out, ".");
            print(out, role.getRelation().getName());
            print(out, "\");");

            if (role.getMultiplicityUpper() != 1 && role.getOtherRole().getMultiplicityUpper() != 1) {

                // a relation many-to-many need a listener...
                Role otherRole = role.getOtherRole();
                String firstType = getTypeFullName(otherRole.getType());
                String secondType = getTypeFullName(role.getType());

                newline(out);
                printWords(out, relationName + ".relation");
                print(out, ".addListener(new ");
                print(out, makeGenericType("pt.ist.fenixframework.dml.runtime.RelationAdapter", firstType, secondType));
                print(out, "()");
                newBlock(out);

                println(out, "@Override");
                printMethod(out, "public", "void", "beforeAdd", makeArg(firstType, "arg0"), makeArg(secondType, "arg1"));
                startMethodBody(out);
                generateRelationRegisterCall("addRelationTuple", role, otherRole, out);
                endMethodBody(out);

                println(out, "@Override");
                printMethod(out, "public", "void", "beforeRemove", makeArg(firstType, "arg0"), makeArg(secondType, "arg1"));
                startMethodBody(out);
                generateRelationRegisterCall("removeRelationTuple", role, otherRole, out);
                endMethodBody(out);

                closeBlock(out);
                print(out, ");");
            }

            // close the static block
            closeBlock(out);
        }
    }

    protected void generateRelationRegisterCall(String regMethodName, Role r0, Role r1, PrintWriter out) {
        String r0name = r0.getName();
        String r1name = r1.getName();

        print(out, TRANSACTION_SUPPORT_CLASS);
        print(out, ".");
        print(out, regMethodName);
        print(out, "(\"");
        print(out, getEntityFullName(r0.getRelation()));
        print(out, "\", arg1, \"");
        print(out, r1name == null ? "" : r1name);
        print(out, "\", arg0, \"");
        print(out, r0name == null ? "" : r0name);
        print(out, "\");");
    }

    @Override
    protected void generateSetterBody(String setterName, Slot slot, PrintWriter out) {
        if (!setterName.startsWith("set$")) {
            String slotName = slot.getName();
            print(out, getSlotExpression(slotName));
            print(out, ".put(this, \"");
            print(out, slotName);
            print(out, "\", ");
            print(out, slotName);
            print(out, ");");
        } else {
            super.generateSetterBody(setterName, slot, out);
        }
    }

    @Override
    protected void generateInitSlot(Slot slot, PrintWriter out) {
        onNewline(out);
        printWords(out, slot.getName());
        print(out, " = ");
        print(out, getNewSlotExpression(slot));
        print(out, ";");

        // initialize primitive slots with their default value
        generateInitializePrimitiveIfNeeded(slot, out);
    }

    protected void generateInitializePrimitiveIfNeeded(Slot slot, PrintWriter out) {
        if (findWrapperEntry(slot.getTypeName()) == null) {
            generateSlotInitialization(slot.getName(), out);
        }
    }

    protected void generateSlotInitialization(String name, PrintWriter out) {
        // This method generates the code that initializes all of the
        // non-primitive slots to null, so that the corresponding vbox
        // appears in the TxIntrospector.getWriteSetLog() set of
        // entries. This will probably disappear once we have the
        // HBase-based version of the fenix-framework, where each box
        // knows to which object and attribute it belongs to. If we
        // had that, the getWriteSetLog method could use the JVSTM
        // write-set instead of the DBChanges.attrChangeLogs. This,
        // of course, assuming that creating a box always calls its
        // put method, with the consequence of making that box go into
        // the write-set. Even though I've not checked this rigth
        // now, I strongly believe that's the behavior of the JVSTM.
        onNewline(out);
        print(out, "if (!allocateOnly) this.");
        print(out, name + ".put(this, \"" + name);
        println(out, "\", null);");
    }

    protected String getNewSlotExpression(Slot slot) {
        return "VBox.makeNew(this, \"" + slot.getName() + "\", allocateOnly, false)";
    }

    protected String getNewRoleOneSlotExpression(Role role) {
        return "VBox.makeNew(this, \"" + role.getName() + "\", allocateOnly, false)";
    }

    @Override
    protected String getNewRoleStarSlotExpression(Role role) {
        StringBuilder buf = new StringBuilder();

        // generate the relation aware collection
        String thisType = getTypeFullName(role.getOtherRole().getType());
        buf.append("new ");
        buf.append(getRelationAwareTypeFor(role));
        buf.append("((");
        buf.append(thisType);
        buf.append(")this, ");
        buf.append(getRelationMethodNameFor(role));
        buf.append(", \"");
        buf.append(role.getName());
        buf.append("\", allocateOnly)");

        return buf.toString();
    }

    @Override
    protected String getRelationAwareBaseTypeFor(Role role) {
        // FIXME: handle other types of collections other than sets
        return "RelationList";
    }

    protected String getBoxBaseType() {
        return "VBox";
    }

    @Override
    protected String getRoleArgs(Role role) {
        String args = super.getRoleArgs(role);
        if (role.getName() != null && role.getMultiplicityUpper() == 1) {
            if (args.length() > 0) {
                args += ", ";
            }
            args += "\"" + role.getName() + "\"";
        }
        return args;
    }

    @Override
    protected String getRoleOneBaseType() {
        return "dml.runtime.RoleOneFenix";
    }

    @Override
    protected void generateGetterBody(String slotName, String typeName, PrintWriter out) {
        print(out, "return ");
        generateGetSlotExpression(slotName, out);
        print(out, ";");
    }

    protected void generateGetSlotExpression(String slotName, PrintWriter out) {
        print(out, getSlotExpression(slotName));
        print(out, ".get(this, \"");
        print(out, slotName);
        print(out, "\")");
    }

    @Override
    protected void generateRelationGetter(String getterName, Role role, PrintWriter out) {
        String paramListType = makeGenericType("java.util.Set", getTypeFullName(role.getType()));
        generateRelationGetter(role, paramListType, out);
    }

    protected void generateRelationGetter(Role role, String paramListType, PrintWriter out) {
        generateRelationGetter("get" + capitalize(role.getName()) + "Set", getSlotExpression(role.getName()), paramListType, out);
    }

    protected void generateRelationGetter(String getterName, String valueToReturn, String typeName, PrintWriter out) {
        newline(out);
        printFinalMethod(out, "public", typeName, getterName);

        startMethodBody(out);
        print(out, "return ");
        print(out, valueToReturn);
        print(out, ";");
        endMethodBody(out);
    }

    @Override
    protected void generateInitRoleSlot(Role role, PrintWriter out) {
        super.generateInitRoleSlot(role, out);
        if (role.getMultiplicityUpper() == 1) {
            generateSlotInitialization(role.getName(), out);
        }
    }

    @Override
    protected void generateRoleSlotMethodsMultStarGetters(Role role, PrintWriter out) {
        generateRelationGetter("get" + capitalize(role.getName()) + "Set", role, out);
        generateOJBSetter(role.getName(), "OJBFunctionalSetWrapper", out);
    }

    @Override
    protected void generateSlotAccessors(Slot slot, PrintWriter out) {
        super.generateSlotAccessors(slot, out);
        generateExternalizationGetter(slot.getName(), slot.getSlotType(), out);
        generateInternalizationSetter(slot.getName(), slot.getSlotType(), out);
    }

    protected void generateExternalizationGetter(String name, ValueType type, PrintWriter out) {
        newline(out);
        String returnType = getSqlTypeName(type);
        printFinalMethod(out, "private", returnType, "get$" + name);

        startMethodBody(out);
        // handle nulls (if the value is null, then return null)
        print(out, type.getFullname());
        print(out, " value = ");
        generateGetSlotExpression(name, out);
        println(out, ";");

        print(out, "return ");

        if (DomainModel.isNullableType(type)) {
            print(out, "(value == null) ? null : ");
        }

        print(out, getExternalizationExpression(type));
        print(out, ";");
        endMethodBody(out);
    }

    protected String getExternalizationExpression(ValueType vt) {

        StringBuilder expression = new StringBuilder();

        // Use the ValueTypeSerializer class to get the final type, 
        // and then convert it to the correct SQL type

        expression.append(TO_SQL_CONVERTER_CLASS);
        expression.append(".getValueFor");
        if (vt.isEnum()) {
            expression.append("Enum(value)");
            return expression.toString();
        }
        expression.append(ValueTypeSerializationGenerator.getSerializedFormTypeName(vt, true));
        expression.append("(");
        if (vt.isBuiltin() || vt.isEnum()) {
            expression.append("value)");
        } else {
            expression.append(ValueTypeSerializationGenerator.SERIALIZER_CLASS_SIMPLE_NAME);
            expression.append(".");
            expression.append(ValueTypeSerializationGenerator.SERIALIZATION_METHOD_PREFIX);
            expression.append(ValueTypeSerializationGenerator.makeSafeValueTypeName(vt));
            expression.append("(value))");
        }

        return expression.toString();
    }

    protected void generateInternalizationSetter(String name, ValueType type, PrintWriter out) {
        newline(out);
        print(out, "private final void set$");
        print(out, name);
        print(out, "(");

        ValueType vt = getExternalizationType(type);
        print(out, vt.getFullname());
        print(out, " arg0, int txNumber)");

        startMethodBody(out);
        print(out, "this.");
        print(out, name);
        print(out, ".persistentLoad(");

        if (DomainModel.isNullableType(vt)) {
            print(out, "(arg0 == null) ? null : ");
        }

        print(out, getRsReaderExpression(type));
        print(out, ", txNumber);");
        endMethodBody(out);
    }

    protected ValueType getExternalizationType(ValueType vt) {
        while (!(vt.isBuiltin() || vt.isEnum())) {
            List<ExternalizationElement> extElems = vt.getExternalizationElements();
            if (extElems.size() != 1) {
                // Value Types with more than one externalization element
                // are externalized to Json
                return jsonElementVt;
            }

            ExternalizationElement extElem = extElems.get(0);
            vt = extElem.getType();
        }

        return vt;
    }

    protected String getSqlTypeName(ValueType vt) {
        ValueType extType = getExternalizationType(vt);
        String toSqlMethodName = "getValueFor" + (extType.isEnum() ? "Enum" : extType.getDomainName());

        for (Method m : ToSqlConverter.class.getDeclaredMethods()) {
            if (m.getName().equals(toSqlMethodName)) {
                return m.getReturnType().getName();
            }
        }

        throw new Error("Something's wrong.  Couldn't find the appropriate base value type.");
    }

    protected String getRsReaderExpression(ValueType vt) {
        if (vt.isBuiltin() || vt.isEnum()) {
            return "value";
        }

        StringBuilder builder = new StringBuilder();

        builder.append(ValueTypeSerializationGenerator.SERIALIZER_CLASS_SIMPLE_NAME);
        builder.append(".");
        builder.append(ValueTypeSerializationGenerator.DESERIALIZATION_METHOD_PREFIX);
        builder.append(ValueTypeSerializationGenerator.makeSafeValueTypeName(vt));
        builder.append("(value)");

        return builder.toString();
    }

    protected void generateOJBSetter(String slotName, String typeName, PrintWriter out) {
        newline(out);
        printFinalMethod(out, "public", "void", "set$" + slotName, makeArg(typeName, slotName));

        startMethodBody(out);
        printWords(out, getSlotExpression(slotName));
        print(out, ".setFromOJB(this, \"");
        print(out, slotName);
        print(out, "\", ");
        print(out, slotName);
        print(out, ");");
        endMethodBody(out);
    }

    @Override
    protected void generateRoleSlotMethodsMultOne(Role role, PrintWriter out) {
        super.generateRoleSlotMethodsMultOne(role, out);

        String typeName = getTypeFullName(role.getType());
        String slotName = role.getName();
        // generateGetter("public", "get$" + slotName, slotName, typeName, out);
        // generateOJBSetter(slotName, typeName, out);
        generateOidOJBGetter(slotName, out);
    }

    protected void generateOidOJBGetter(String name, PrintWriter out) {
        newline(out);
        printMethod(out, "private", "java.lang.Long", "get$oid" + capitalize(name));
        startMethodBody(out);
        print(out, DOMAIN_CLASS_ROOT);
        print(out, " value = (" + DOMAIN_CLASS_ROOT + ") ");
        generateGetSlotExpression(name, out);
        println(out, ";");
        print(out, "return (value == null) ? null : value.getOid();");
        endMethodBody(out);
    }

    protected void generateCheckDisconnected(DomainClass domClass, PrintWriter out) {
        newline(out);
        printMethod(out, "protected", "void", "checkDisconnected");
        startMethodBody(out);

        if (domClass.hasSuperclass()) {
            println(out, "super.checkDisconnected();");
        }

        println(out, "DO_State castedState = (DO_State)this.get$obj$state(false);");

        Iterator<Role> roleSlotsIter = domClass.getRoleSlots();
        while (roleSlotsIter.hasNext()) {
            Role role = roleSlotsIter.next();

            if (role.getName() != null) {
                onNewline(out);

                print(out, "if (");

                if (role.getMultiplicityUpper() == 1) {
                    print(out, "castedState.");
                    print(out, role.getName());
                    print(out, " != null");
                } else {
                    print(out, "get$rl$");
                    print(out, role.getName());
                    print(out, "().size() > 0");
                }

                print(out, ") handleAttemptToDeleteConnectedObject(\"");
                print(out, capitalize(role.getName()));
                println(out, "\");");
            }
        }

        endMethodBody(out);
    }

    // -----------------------------------------------------------------------------------
    // code related to the database reading/writing

    protected void generateDatabaseReader(DomainClass domClass, PrintWriter out) {
        newline(out);
        printMethod(out, "protected", "void", "readSlotsFromResultSet", makeArg("java.sql.ResultSet", "rs"),
                makeArg("int", "txNumber"));
        print(out, " throws java.sql.SQLException");
        startMethodBody(out);

        if (domClass.hasSuperclass()) {
            println(out, "super.readSlotsFromResultSet(rs, txNumber);");
        }

        for (Slot slot : domClass.getSlotsList()) {
            generateOneSlotRsReader(out, slot.getName(), slot.getSlotType());
        }

        for (Role role : domClass.getRoleSlotsList()) {
            if (role.getName() != null && role.getMultiplicityUpper() == 1) {
                generateOneRoleSlotRsReader(out, role.getName());
            }
        }

        endMethodBody(out);
    }

    protected void generateOneSlotRsReader(PrintWriter out, String name, ValueType type) {
        onNewline(out);
        print(out, "set$");
        print(out, name);
        print(out, "(");
        printRsReaderExpressions(out, type, DbUtil.convertToDBStyle(name));
        print(out, ", txNumber);");
    }

    protected void printRsReaderExpressions(PrintWriter out, ValueType vt, String colBaseName) {
        print(out, RESULT_SET_READER_CLASS);
        print(out, ".read");

        if (vt.isEnum()) {
            print(out, "Enum(");
            print(out, vt.getFullname());
            print(out, ".class, ");
        } else {
            print(out, ValueTypeSerializationGenerator.getSerializedFormTypeName(vt, true));
            print(out, "(");
        }

        print(out, "rs, \"");
        print(out, colBaseName);
        print(out, "\")");
    }

    protected void generateOneRoleSlotRsReader(PrintWriter out, String name) {
        onNewline(out);

        print(out, "this.");
        print(out, name);
        print(out, ".persistentLoad(");

        print(out, RESULT_SET_READER_CLASS);
        print(out, ".readDomainObject(rs, \"OID_");
        print(out, DbUtil.convertToDBStyle(name));
        print(out, "\"), txNumber);");
    }

    @Override
    protected void generateSlotDeclaration(PrintWriter out, String type, String name) {
        printWords(out, "private", type, name);
        println(out, ";");
    }

    @Override
    protected void generateRoleSlotMethods(Role role, PrintWriter out) {
        super.generateRoleSlotMethods(role, out);

        if (role.needsMultiplicityChecks()) {
            generateMultiplicityConsistencyPredicate(role, out);
        }
    }

    protected void generateSlotConsistencyPredicates(DomainClass domClass, PrintWriter out) {
        if (domClass.hasSlotWithOption(Slot.Option.REQUIRED)) {
            generateRequiredConsistencyPredicate(domClass, out);
        }
    }

    protected void generateRequiredConsistencyPredicate(DomainClass domClass, PrintWriter out) {
        newline(out);
        println(out, "@pt.ist.fenixframework.consistencyPredicates.ConsistencyPredicate");
        printMethod(out, "private", "boolean", "checkRequiredSlots");
        startMethodBody(out);

        for (Slot slot : domClass.getSlotsList()) {
            if (slot.hasOption(Slot.Option.REQUIRED)) {
                String slotName = slot.getName();

                print(out, ConsistencyChecks.class.getName() + ".checkRequired(this, \"");
                print(out, slotName);
                print(out, "\", get");
                print(out, capitalize(slotName));
                println(out, "());");
            }
        }
        print(out, "return true;");
        endMethodBody(out);
    }

    protected void generateMultiplicityConsistencyPredicate(Role role, PrintWriter out) {
        String slotName = role.getName();
        String slotAccessExpression = getSlotExpression(slotName);
        String capitalizedSlotName = capitalize(slotName);

        newline(out);
        println(out, "@pt.ist.fenixframework.consistencyPredicates.ConsistencyPredicate");
        printMethod(out, "public final", "boolean", "checkMultiplicityOf" + capitalizedSlotName);
        startMethodBody(out);

        int lower = role.getMultiplicityLower();
        int upper = role.getMultiplicityUpper();

        if (lower > 0) {
            print(out, "if (");
            if (upper == 1) {
                print(out, "get");
                print(out, capitalizedSlotName);
                print(out, "() == null");
            } else {
                print(out, slotAccessExpression);
                print(out, ".size() < " + lower);
            }
            println(out, ") return false;");
        }

        if ((upper > 1) && (upper != Role.MULTIPLICITY_MANY)) {
            print(out, "if (");
            print(out, slotAccessExpression);
            print(out, ".size() > " + upper);
            println(out, ") return false;");
        }

        print(out, "return true;");
        endMethodBody(out);
    }

    @Override
    protected String getBackEndName() {
        return JvstmOJBBackEnd.BACKEND_NAME;
    }

    @Override
    protected String getDefaultConfigClassName() {
        return JvstmOJBConfig.class.getName();
    }

    @Override
    protected void generateStaticKeyFunctionForRole(Role role, PrintWriter out) {
        // Nothing to do, this backend does not use Key Functions
    }

}
