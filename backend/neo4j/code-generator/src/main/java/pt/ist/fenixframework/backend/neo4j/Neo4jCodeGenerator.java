package pt.ist.fenixframework.backend.neo4j;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicRelationshipType;

import pt.ist.fenixframework.dml.CodeGenerator;
import pt.ist.fenixframework.dml.CompilerArgs;
import pt.ist.fenixframework.dml.DomainModel;
import pt.ist.fenixframework.dml.Role;
import pt.ist.fenixframework.dml.Slot;
import pt.ist.fenixframework.dml.ValueType;
import pt.ist.fenixframework.dml.ValueTypeSerializationGenerator;

public class Neo4jCodeGenerator extends CodeGenerator {

    public Neo4jCodeGenerator(CompilerArgs compArgs, DomainModel domainModel) {
        super(compArgs, domainModel);
    }

    @Override
    protected String getDomainClassRoot() {
        return Neo4jDomainObject.class.getName();
    }

    @Override
    protected String getBackEndName() {
        return Neo4jBackEnd.BACKEND_NAME;
    }

    @Override
    protected String getDefaultConfigClassName() {
        return Neo4jConfig.class.getName();
    }

    @Override
    protected void generateFilePreamble(String subPackageName, PrintWriter out) {
        super.generateFilePreamble(subPackageName, out);

        newline(out);

        printImport(out, RelationshipBackedSet.class);
        printImport(out, DynamicRelationshipType.class);
        printImport(out, ValueTypeSerializationGenerator.SERIALIZER_CLASS_FULL_NAME);
        printImport(out, Direction.class);
    }

    protected void printImport(PrintWriter out, Class<?> clazz) {
        printImport(out, clazz.getName());
    }

    protected void printImport(PrintWriter out, String clazz) {
        println(out, "import " + clazz + ";");
    }

    @Override
    protected void generateRoleSlot(Role role, PrintWriter out) {
        onNewline(out);
        if (role.getMultiplicityUpper() == 1) {
            printWords(out, "private", getTypeFullName(role.getType()), role.getName() + ";");
            return;
        }
        String type = makeGenericType("RelationshipBackedSet", role.getType().getFullName());
        printWords(out, "private", type, role.getName(), ";");
        newline(out);
    }

    @Override
    protected String getNewRoleStarSlotExpression(Role role) {
        StringBuilder buff = new StringBuilder();

        buff.append("new ");
        buff.append(makeGenericType("RelationshipBackedSet", role.getType().getFullName()));
        buff.append("(this, DynamicRelationshipType.withName(\"");
        buff.append(role.getRelation().getName());
        buff.append("\"), Direction.");
        buff.append(computeDirectionForRole(role));
        buff.append(")");

        return buff.toString();
    }

    @Override
    protected void generateRelationGetterBody(Role role, PrintWriter out) {
        print(out, "return this." + role.getName() + ";");
    }

    @Override
    protected void generateRoleSlotMethodsMultOne(Role role, PrintWriter out) {
        newline(out);

        String typeSlot = "relationship$$" + role.getName();

        print(out, "private static final DynamicRelationshipType ");
        print(out, typeSlot);
        print(out, " = DynamicRelationshipType.withName(\"");
        print(out, role.getRelation().getName());
        println(out, "\");");
        newline(out);

        printWords(out, "public", role.getType().getFullName(), "get" + capitalize(role.getName()) + "()");
        startMethodBody(out);
        print(out, "return getDomainObject(");
        print(out, typeSlot);
        print(out, ", Direction.");
        print(out, computeDirectionForRole(role));
        print(out, ");");
        endMethodBody(out);

        newline(out);

        printWords(out, "public", "void", "set" + capitalize(role.getName()) + "(" + role.getType().getFullName() + " value)");
        startMethodBody(out);
        print(out, "setDomainObject(");
        print(out, typeSlot);
        print(out, ", Direction.");
        print(out, computeDirectionForRole(role));
        print(out, ", value);");
        endMethodBody(out);
    }

    @Override
    protected void generateRelationAddMethodCall(Role role, String otherArg, String indexParam, PrintWriter out) {
        print(out, "this." + role.getName() + ".add(" + otherArg + ");");
    }

    @Override
    protected void generateRelationRemoveMethodCall(Role role, String otherArg, PrintWriter out) {
        print(out, "this." + role.getName() + ".remove(" + otherArg + ");");
    }

    @Override
    protected void generateStaticKeyFunctionForRole(Role role, PrintWriter out) {
        // We don't use key functions
    }

    @Override
    protected void generateSlot(Slot slot, PrintWriter out) {
        // Empty. Values are kept on the node
    }

    private static final Collection<String> CONVERTIBLE_TYPES = Arrays.asList("JsonElement", "DateTime", "Partial", "LocalDate",
            "LocalTime", "Serializable", "Enum");

    @Override
    protected void generateSlotAccessors(Slot slot, PrintWriter out) {
        generateSlotGetter(slot, out);
        generateSlotSetter(slot, out);
    }

    protected void generateSlotGetter(Slot slot, PrintWriter out) {
        newline(out);
        printFinalMethod(out, "public", slot.getTypeName(), "get" + capitalize(slot.getName()));
        startMethodBody(out);

        ValueType vt = slot.getSlotType();

        String serializedType = vt.isEnum() ? "Enum" : ValueTypeSerializationGenerator.getSerializedFormTypeName(vt, true);
        String propertyValuePrefix = "get" + (CONVERTIBLE_TYPES.contains(serializedType) ? serializedType : "") + "PropertyValue";

        String enumArg = vt.isEnum() ? ", " + vt.getFullname() + ".class" : "";

        String propertyGetter = propertyValuePrefix + "(\"" + slot.getName() + "\"" + enumArg + ")";

        print(out, "return ");

        if (vt.isBuiltin() || vt.isEnum()) {
            print(out, propertyGetter);
        } else {
            print(out,
                    ValueTypeSerializationGenerator.SERIALIZER_CLASS_SIMPLE_NAME + "."
                            + ValueTypeSerializationGenerator.DESERIALIZATION_METHOD_PREFIX
                            + ValueTypeSerializationGenerator.makeSafeValueTypeName(slot.getSlotType()) + "(("
                            + ValueTypeSerializationGenerator.getSerializedFormTypeName(slot.getSlotType(), false) + ") "
                            + propertyGetter + ")");
        }

        print(out, ";");

        endMethodBody(out);
    }

    @Override
    protected void generateSlotSetter(Slot slot, PrintWriter out) {
        newline(out);
        printFinalMethod(out, "public", "void", "set" + capitalize(slot.getName()), slot.getTypeName() + " value");
        startMethodBody(out);

        ValueType vt = slot.getSlotType();

        String serializedType = vt.isEnum() ? "Enum" : ValueTypeSerializationGenerator.getSerializedFormTypeName(vt, true);
        String propertyValuePrefix = "set" + (CONVERTIBLE_TYPES.contains(serializedType) ? serializedType : "") + "PropertyValue";

        print(out, propertyValuePrefix + "(\"" + slot.getName() + "\", ");

        if (vt.isBuiltin() || vt.isEnum()) {
            print(out, "value");
        } else {
            print(out,
                    ValueTypeSerializationGenerator.SERIALIZER_CLASS_SIMPLE_NAME + "."
                            + ValueTypeSerializationGenerator.SERIALIZATION_METHOD_PREFIX
                            + ValueTypeSerializationGenerator.makeSafeValueTypeName(slot.getSlotType()) + "(value)");
        }

        print(out, ");");
        endMethodBody(out);

    }

    protected String computeDirectionForRole(Role role) {
        return role.isFirstRole() ? "OUTGOING" : "INCOMING";
    }
}
