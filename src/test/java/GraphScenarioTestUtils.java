import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Graph;

public class GraphScenarioTestUtils {
    public static boolean hasAccess(Graph g, String resourceId, String userId, String permission) {
        // System.out.println(String.format("Looking for %s with access %s by %s", resourceId, permission, userId));
        try (GraphTraversal t = new PermissionTraverser().hasAccess(g, resourceId, userId, permission)) {
            return t.hasNext();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    public static Object getAllAccessible(Graph g, String userId, String permission) {
        try (GraphTraversal t = new PermissionTraverser().hasAccess(g, g.traversal().V(), userId, permission).values("name").fold()) {
            return t.next();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    public static Object getAllAccessibleOrderedByType(Graph g, String userId, String permission, String type, int limit) {
        try (GraphTraversal t = new PermissionTraverser().hasAccess(g, g.traversal().V().hasLabel(type).order().by("name"), userId, permission).limit(limit).values("name").fold()) {
            return t.next();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    public static Object getAllOrderedByType(Graph g, String type, int limit) {
        try (GraphTraversal t = g.traversal().V().hasLabel(type).order().by("name").limit(limit).values("name").fold()) {
            return t.next();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    public static void printAllAccessibleToUser(Graph g, String userId, String permission) {
        System.out.format("%-20s %-1s %s\n", userId, permission, getAllAccessible(g, userId, permission));
    }
}
