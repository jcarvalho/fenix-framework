package pt.ist.fenixframework.backend.neo4j;

import static pt.ist.fenixframework.backend.neo4j.Neo4jBackEnd.relationshipToObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

public final class RelationshipBackedSet<D extends Neo4jDomainObject> implements Set<D> {

    private final Neo4jDomainObject node;
    private final RelationshipType type;
    private final Direction direction;

    public RelationshipBackedSet(Neo4jDomainObject node, RelationshipType type, Direction direction) {
        this.node = node;
        this.type = type;
        this.direction = direction;
    }

    private Iterable<Relationship> underlying() {
        return node.get$node().getRelationships(type, direction);
    }

    private final class TransformingIterator implements Iterator<D> {

        private final Iterator<Relationship> underlying;

        public TransformingIterator(Iterator<Relationship> underlying) {
            this.underlying = underlying;
        }

        @Override
        public boolean hasNext() {
            return underlying.hasNext();
        }

        @Override
        public D next() {
            return relationshipToObject(node.get$node(), underlying.next());
        }

        @Override
        public void remove() {
            underlying.remove();
        }

    }

    // Actual Set Methods

    @Override
    public int size() {
        Iterator<Relationship> iterator = underlying().iterator();
        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        return !underlying().iterator().hasNext();
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof Neo4jDomainObject)) {
            return false;
        }
        Node node = ((Neo4jDomainObject) o).get$node();
        for (Relationship rel : underlying()) {
            if (rel.getOtherNode(this.node.get$node()).equals(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<D> iterator() {
        return new TransformingIterator(underlying().iterator());
    }

    @Override
    public boolean add(D e) {
        if (contains(e)) {
            return false;
        }
        if (direction == Direction.INCOMING) {
            e.get$node().createRelationshipTo(node.get$node(), type);
        } else {
            node.get$node().createRelationshipTo(e.get$node(), type);
        }
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof Neo4jDomainObject)) {
            return false;
        }
        Node node = ((Neo4jDomainObject) o).get$node();
        for (Relationship rel : underlying()) {
            if (rel.getOtherNode(this.node.get$node()).equals(node)) {
                rel.delete();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object e : c) {
            if (!contains(e)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends D> c) {
        boolean modified = false;
        for (D e : c) {
            if (add(e)) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean modified = false;
        for (Relationship rel : underlying()) {
            if (!c.contains(relationshipToObject(node.get$node(), rel))) {
                rel.delete();
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Relationship rel : underlying()) {
            if (c.contains(relationshipToObject(node.get$node(), rel))) {
                rel.delete();
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public void clear() {
        for (Relationship rel : underlying()) {
            rel.delete();
        }
    }

    @Override
    public Object[] toArray() {
        List<D> list = new ArrayList<D>();
        Iterator<D> elements = iterator();
        while (elements.hasNext()) {
            list.add(elements.next());
        }
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<D> list = new ArrayList<D>();
        Iterator<D> elements = iterator();
        while (elements.hasNext()) {
            list.add(elements.next());
        }
        return list.toArray(a);
    }

}
