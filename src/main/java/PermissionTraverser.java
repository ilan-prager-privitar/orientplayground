import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.or;

import java.util.ArrayList;
import java.util.List;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalOptionParent.Pick;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

public class PermissionTraverser {

    private GraphTraversal<Object, Object> isPublic() {
        // resource is public (if permission requested is read)
        return __.has("public", true);
    }

    private GraphTraversal<Vertex, Vertex> isOwnedByUser(String userId) {
        // resource is owned by the given user
        return __.out("OWNED_BY").has("name", userId);
    }

    private GraphTraversal getMappedPermission(String permission) {
        // actual implementation should be more elegant
        if (permission.equals("R")) {
            List<Traversal<?, ?>> traversals = new ArrayList<>();
            traversals.add(__.has("permission", "R"));
            traversals.add(__.has("permission", "W"));
            traversals.add(__.has("permission", "A"));
            return __.or(traversals.toArray(new Traversal<?, ?>[]{}));
        } else if (permission.equals("W")) {
            List<Traversal<?, ?>> traversals = new ArrayList<>();
            traversals.add(__.has("permission", "W"));
            traversals.add(__.has("permission", "A"));
            return __.or(traversals.toArray(new Traversal<?, ?>[]{}));
        }
        return __.has("permission", permission);
    }

    private GraphTraversal<Vertex, Vertex> permissionedPrincipalsTraversal(String permission) {
        return __.inE("HAS_PERMISSION").where(getMappedPermission(permission)).outV();
    }

    private GraphTraversal<Vertex, Vertex> resourcePermissionedToUserTraversal(String userId, String permission) {
        List<Traversal<?, ?>> traversals = new ArrayList<>();
        if (permission.equals("R")) {
            // only consider public access if the request is for read
            traversals.add(isPublic());
        }
        traversals.add(isOwnedByUser(userId));
        traversals.add(isPermissionedToPrincipal(userId, permission));
        traversals.add(isPermissionedToPrincipalGroupsHierarchy(userId, permission));

        return __.or(traversals.toArray(new Traversal<?, ?>[]{}));
    }

    private GraphTraversal<Vertex, Vertex> isPermissionedToPrincipal(String userId, String permission) {
        // resource is accessible directly by the given user
        return permissionedPrincipalsTraversal(permission).has("name", userId);
    }

    private GraphTraversal<Vertex, Vertex> isPermissionedToPrincipalGroupsHierarchy(String userId, String permission) {
        // resource is owned by the given user
        // TODO add until to optimize
        return permissionedPrincipalsTraversal(permission).repeat(
                // dive down to subgroups, members to see if user is included there
                __.in("MEMBER_OF", "HAS_SUPERGROUP")
        ).emit().has("name", userId);
    }

    private Vertex getUserById(Graph g, String userId) {
        return g.traversal().V().hasLabel("User").has("name", userId).next(); // TODO switch to userId
    }

    private Traversal<?, Vertex> getResourceTraversal(Graph g, String resourceId) {
        return __.has("name", resourceId);  // TODO actual resource id prop by type
    }

    private GraphTraversal<Vertex, Vertex> containerTraversal(TraversalProvider permissionTraversalProvider, String permission, String... edgeLabels) {
        return __.where(or(
                resourceHasPermission(permissionTraversalProvider),
                containerHierarchyHasPermission(permissionTraversalProvider, permission, edgeLabels)
        ));
    }

    private GraphTraversal<Vertex, Vertex> resourceHasPermission(TraversalProvider permissionTraversalProvider) {
        return permissionTraversalProvider.getTraversal();
    }

    private GraphTraversal<Vertex, Vertex> containerHierarchyHasPermission(TraversalProvider permissionTraversalProvider, String permission,
            String... edgeLabels) {
        return __.until(__.inE("HAS_PERMISSION").has("permission", permission))
                .repeat((Traversal)
                        __.out(edgeLabels)
                ).where(permissionTraversalProvider.getTraversal());
    }

    public GraphTraversal<Object, Object> resourceViaPermissionedContainerTraversal(String permission, TraversalProvider permissionTraversalProvider) {
        return __.choose(__.label())
                .option("Folder", containerTraversal(permissionTraversalProvider, permission, "IN_FOLDER"))
                .option("Term", containerTraversal(permissionTraversalProvider, permission, "IN_FOLDER"))
                .option(Pick.none, permissionTraversalProvider.getTraversal());
    }

    // main entry point - single resource
    public GraphTraversal<Vertex, Vertex> hasAccess(Graph g, String resourceId, String userId, String permission) {
        return hasAccess(g, g.traversal().V().where(getResourceTraversal(g, resourceId)), userId, permission);
    }

    // main entry point - resource traversal
    public GraphTraversal<Vertex, Vertex> hasAccess(Graph g, GraphTraversal<Vertex, Vertex> resource, String userId, String permission) {
        return resource.with("userId", userId).with("permission", permission);
    }

    public GraphTraversal<Object, Object> hasAccessTraversal(String userId, String permission) {
        return resourceViaPermissionedContainerTraversal(permission, () -> resourcePermissionedToUserTraversal(userId, permission));
    }

    public interface TraversalProvider {

        GraphTraversal<Vertex, Vertex> getTraversal();
    }
}
