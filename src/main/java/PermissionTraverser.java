import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal.Symbols.repeat;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.__;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.or;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outV;


import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalOptionParent.Pick;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

public class PermissionTraverser {

    private GraphTraversal isPublic() {
        // resource is public (if permission requested is read)
        return __.has("public", true);
    }

    private GraphTraversal isOwnedByUser(Vertex user) {
        // resource is owned by the given user
        return __.out("OWNED_BY").is(user);
    }

    private GraphTraversal permissionedPrincipalsTraversal(String permission) {
        return __.inE("HAS_PERMISSION").has("permission", permission).outV();
    }

    private GraphTraversal resourcePermissionedToUserTraversal(Vertex user, String permission) {
        return __.or(
                isPublic(),
                isOwnedByUser(user),
                isPermissionedToPrincipal(user, permission),
                isPermissionedToPrincipalGroupsHierarchy(user, permission)
        );
    }

    private GraphTraversal containerTraversal(String... edgeLabels) {
        return __.V().repeat(
            __.in(edgeLabels)
        ).emit();
    }

    private GraphTraversal resourcePermissionedContainerTraversal(GraphTraversal resource) {
        return resource.choose(__.label())
                .option("Folder", containerTraversal("IN_FOLDER"))
                .option("Term", containerTraversal("IN_FOLDER"))
                .option(Pick.none, __.identity());
    }

    private GraphTraversal isPermissionedToPrincipal(Vertex user, String permission) {
        // resource is accessible directly by the given user
        return permissionedPrincipalsTraversal(permission).is(user);
    }

    private GraphTraversal isPermissionedToPrincipalGroupsHierarchy(Vertex user, String permission) {
        // resource is owned by the given user
        return permissionedPrincipalsTraversal(permission).repeat(
          __.in("MEMBER_OF", "HAS_SUPERGROUP")
        ).emit().is(user);
    }

    // main entry point - single resource
    public GraphTraversal hasAccess(Graph g, String resourceId, String userId, String permission) {
        // find user id node
        GraphTraversal resource = g.traversal().V().has("name", resourceId); // // TODO actual resource id prop by type
        return hasAccess(g, resource, userId, permission);
    }

    // main entry point - resource traversal
    public GraphTraversal hasAccess(Graph g, GraphTraversal resource, String userId, String permission) {
        // find user id node
        Vertex user = g.traversal().V().hasLabel("User").has("name", userId).next(); // TODO actual userid prop
        return resourcePermissionedContainerTraversal(resource).where(resourcePermissionedToUserTraversal(user, permission));
    }
}
