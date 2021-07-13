import java.util.List;
import java.util.stream.Stream;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GraphTest {

    private static Graph graph;

    @BeforeAll
    public static void setup() {
        // in memory
        graph = OrientGraph.open();
        graph.configuration().setProperty("orient-max-poolsize", 500);
        new GraphScenario(graph).createGraph();
    }

    static class GraphScenario {

        private Graph graph;

        public GraphScenario(Graph graph) {
            this.graph = graph;
        }

        private Vertex createUser(String name) {
            return graph.addVertex("User").property("name", name).element();
        }

        private Vertex createGroup(String name) {
            return graph.addVertex("Group").property("name", name).element();
        }

        private Vertex createFolder(String name) {
            return graph.addVertex("Folder").property("name", name).element();
        }

        private Vertex createTerm(String name) {
            return graph.addVertex("Term").property("name", name).element();
        }

        private Edge addToGroup(Vertex user, Vertex group) {
            return user.addEdge("MEMBER_OF", group);
        }

        private Edge addToSuperGroup(Vertex group, Vertex superGroup) {
            return group.addEdge("HAS_SUPERGROUP", superGroup);
        }

        private Edge addToFolder(Vertex object, Vertex folder) {
            return object.addEdge("IN_FOLDER", folder);
        }

        private Edge addOwner(Vertex object, Vertex user) {
            return object.addEdge("OWNED_BY", user);
        }

        private void addHasPermission(Vertex user, Vertex object, String permission) {
            user.addEdge("HAS_PERMISSION", object).property("permission", permission);
        }

        public void makePublic(Vertex object) {
            object.property("public", true);
        }

        public void createGraph() {
            Vertex x = createUser("X");
            Vertex ivan = createUser("Ivan Investigator");
            Vertex sally = createUser("Sally Security");
            Vertex dave = createUser("Dave Defender");
            Vertex ronny = createUser("Ronny Researcher");
            Vertex owen = createUser("Owen Owner");

            Vertex securityGroup = createGroup("Security");
            Vertex forensics = createGroup("Forensics");

            Vertex corporate = createFolder("Corporate");
            Vertex finance = createFolder("Finance");
            Vertex security = createFolder("Security");
            Vertex classified = createFolder("Classified");

            Vertex bond = createTerm("Bond");
            Vertex intruder = createTerm("Intruder");
            Vertex hack = createTerm("Hack");

            addToSuperGroup(forensics, securityGroup);

            addToGroup(sally, securityGroup);
            addToGroup(dave, securityGroup);
            addToGroup(ivan, forensics);

            addToFolder(hack, classified);
            addToFolder(classified, security);
            addToFolder(security, corporate);
            addToFolder(intruder, security);
            addToFolder(finance, corporate);
            addToFolder(bond, finance);

            makePublic(corporate);
            addOwner(security, owen);
            addHasPermission(ronny, finance, "R");
            addHasPermission(securityGroup, security, "R");
            addHasPermission(forensics, classified, "R");
            addHasPermission(x, hack, "W");
        }
    }

    private boolean hasAccess(Graph g, String resourceId, String userId, String permission) {
        System.out.println(String.format("Looking for %s with access %s by %s", resourceId, permission, userId));
        try (GraphTraversal t = new PermissionTraverser().hasAccess(g, resourceId, userId, permission)) {
            return t.hasNext();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    private Object getAllAccessible(Graph g, String userId, String permission) {
        try (GraphTraversal t = new PermissionTraverser().hasAccess(g, g.traversal().V(), userId, permission).values("name").fold()) {
            return t.next();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    private void printAllAccessibleToUser(Graph g, String userId, String permission) {
        System.out.format("%-20s %-1s %s\n", userId, permission, getAllAccessible(g, userId, permission));
    }

    private void assertAccess(String resourceId, String userId, String permission, boolean expected) {
        Assertions.assertEquals(expected, hasAccess(graph, resourceId, userId, permission),
                String.format("Failed for %s with access %s by %s", resourceId, permission, userId));
    }

    @Test
    public void testPublic() {
        assertAccess("Corporate", "Ronny Researcher", "R", true);
        assertAccess("Corporate", "Ivan Investigator", "R", true);
        assertAccess("Corporate", "Owen Owner", "R", true);
    }

    @Test
    public void testUserDirectFolderAccess() {
        assertAccess("Finance", "Ronny Researcher", "R", true);
    }

    @Test
    public void testGroupDirectFolderAccess() {
        assertAccess("Security", "Sally Security", "R", true);
        assertAccess("Security", "Dave Defender", "R", true);
    }

    @Test
    public void testSuperGroupDirectFolderAccess() {
        assertAccess("Security", "Ivan Investigator", "R", true);
    }

    @Test
    public void testOwnerDirectFolderAccess() {
        assertAccess("Security", "Owen Owner", "R", true);
    }

    @Test
    public void testWriterDirectTermAccess() {
        assertAccess("Hack", "Ivan Investigator", "W", false);

        assertAccess("Hack", "X", "W", true);
        assertAccess("Hack", "X", "R", false); // really should be true TODO implement

        assertAccess("Hack", "Ivan Investigator", "R", true);
    }

    @Test
    public void testDirectAccessDoesntLeakToContainerAccess() {
        assertAccess("Classified", "X", "W", false);
        assertAccess("Classified", "X", "R", false);
    }


    @Test
    public void testIsolated() {
        // assertAccess("Hack", "X", "R", true); // true because it's in a folder he can access
    }

    @Test
    public void testDirectFolderContainment() {
        assertAccess("Bond", "Ronny Researcher", "R", true);
        assertAccess("Bond", "Ronny Researcher", "W", false);
    }

    @Test
    public void testFolderContainmentViaGroup() {
        assertAccess("Security", "Dave Defender", "R", true);
        assertAccess("Intruder", "Dave Defender", "R", true);
    }

    @Test
    public void testStopAscentInFolderContainmentWhenPermissionsAssigned() {
        assertAccess("Finance", "Ivan Investigator", "R", false);
        assertAccess("Finance", "Sally Security", "R", false);
    }

    @Test
    public void testStopDescentInFolderContainmentWhenPermissionsAssigned() {
        assertAccess("Security", "Ronny Researcher", "R", false);
        assertAccess("Intruder", "Ronny Researcher", "R", false);
    }

    @Test
    public void printAllAccessibleToAllUsers() {
        graph.traversal().V().hasLabel("User").forEachRemaining((user) -> {
            String userId = user.value("name");
            printAllAccessibleToUser(graph, userId, "R");
            printAllAccessibleToUser(graph, userId, "W");
        });
    }
}
