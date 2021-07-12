import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GraphTest {

    private static Graph graph;

    @BeforeAll
    public static void setup() {
        OrientGraphFactory factory = new OrientGraphFactory("remote:localhost/ilantest", "root", "rootpwd");
        graph = factory.getNoTx();
    }

    private boolean hasAccess(Graph g, String resourceId, String userId, String permission) {
        System.out.println(String.format("Looking for %s with access %s by %s", resourceId, permission, userId));
        return new PermissionTraverser().hasAccess(g, resourceId, userId, permission).hasNext();
    }

    private void assertAccess(String resourceId, String userId, String permission, boolean expected) {
        Assertions.assertEquals(expected, hasAccess(graph, resourceId, userId, permission), String.format("Failed for %s with access %s by %s", resourceId, permission, userId));
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
        assertAccess("Hack", "X", "W", true);
        assertAccess("Hack", "Ivan Investigator", "W", false);
        assertAccess("Hack", "Ivan Investigator", "X", false);
    }

    @Test
    public void testDirectFolderContainment() {
        assertAccess("Bond", "Ronny Researcher", "R", true);
    }

    @Test
    public void testFolderContainmentViaGroup() {
        assertAccess("Security", "Dave Defender", "R", true);
        assertAccess("Intruder", "Dave Defender", "R", true);
    }

    @Test
    public void testStopAscentInFolderContainmentWhenPermissionsAssigned() {
        // TODO failing since short circuit is not implemented
        assertAccess("Finance", "Ivan Investigator", "R", false);
        assertAccess("Finance", "Sally Security", "R", false);
    }

    @Test
    public void testStopDescentInFolderContainmentWhenPermissionsAssigned() {
        // TODO failing since short circuit is not implemented
        assertAccess("Security", "Ronny Researcher", "R", false);
    }
}
