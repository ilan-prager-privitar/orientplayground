import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ACLExample2Test {

    private static Graph graph;

    @BeforeAll
    public static void setup() {
        // in memory
        graph = TinkerGraph.open();
        new Example2Scenario(() -> graph).createGraph();
    }

    public static void assertAccess(String resourceId, String userId, String permission, boolean expected) {
        Assertions.assertEquals(expected, GraphScenarioTestUtils.hasAccess(graph, resourceId, userId, permission),
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
        assertAccess("Hack", "X", "R", true);

        assertAccess("Hack", "Ivan Investigator", "R", true);
    }

    @Test
    public void testDirectAccessDoesntLeakToContainerAccess() {
        assertAccess("Classified", "X", "W", false);
        assertAccess("Classified", "X", "R", false);
    }

    @Test
    public void testIsolated() {
        assertAccess("Hack", "Ivan Investigator", "W", false);
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

    static class Example2Scenario extends GraphScenario {

        public Example2Scenario(Supplier<Graph> graph) {
            super(graph);
        }

        @Override
        public void createGraph() {
            Vertex x = createUser("X");
            Vertex ivan = createUser("Ivan Investigator");
            Vertex sally = createUser("Sally Security");
            Vertex dave = createUser("Dave Defender");
            Vertex ronny = createUser("Ronny Researcher");
            Vertex owen = createUser("Owen Owner");
            Vertex derek = createUser("Derek Dev");
            Vertex mandy = createUser("Mandy Manager");
            Vertex cally = createUser("Cally Coder");

            Vertex securityGroup = createGroup("Security");
            Vertex forensics = createGroup("Forensics");
            Vertex allDevs = createGroup("All devs");

            Vertex corporate = createFolder("Corporate");
            Vertex finance = createFolder("Finance");
            Vertex security = createFolder("Security");
            Vertex classified = createFolder("Classified");
            Vertex dev = createFolder("Dev");
            Vertex code = createFolder("Code");
            Vertex secrets = createFolder("Secrets");

            Vertex bond = createTerm("Bond");
            Vertex intruder = createTerm("Intruder");
            Vertex hack = createTerm("Hack");
            Vertex encrypt = createTerm("Encrypt");
            Vertex key = createTerm("Key");

            addToSuperGroup(forensics, securityGroup);

            addToGroup(sally, securityGroup);
            addToGroup(dave, securityGroup);
            addToGroup(ivan, forensics);
            addToGroup(derek, allDevs);
            addToGroup(mandy, allDevs);
            addToGroup(cally, allDevs);

            addToFolder(hack, classified);
            addToFolder(classified, security);
            addToFolder(security, corporate);
            addToFolder(intruder, security);
            addToFolder(finance, corporate);
            addToFolder(bond, finance);
            addToFolder(dev, corporate);
            addToFolder(code, dev);
            addToFolder(secrets, dev);
            addToFolder(encrypt, secrets);
            addToFolder(key, secrets);

            makePublic(corporate);
            addOwner(security, owen);
            addHasPermission(ronny, finance, "R");
            addHasPermission(securityGroup, security, "R");
            addHasPermission(forensics, classified, "R");
            addHasPermission(x, hack, "W");
            addHasPermission(allDevs, dev, "R");
            addHasPermission(derek, secrets, "W");
            addHasPermission(mandy, encrypt, "W");
        }
    }

    @Test
    public void printAllAccessibleToAllUsers() {
        graph.traversal().V().hasLabel("User").forEachRemaining((user) -> {
            String userId = user.value("name");
            GraphScenarioTestUtils.printAllAccessibleToUser(graph, userId, "R");
            GraphScenarioTestUtils.printAllAccessibleToUser(graph, userId, "W");
        });
    }
}
