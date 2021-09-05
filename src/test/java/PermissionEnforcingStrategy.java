import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy.DecorationStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.structure.Graph;

public final class PermissionEnforcingStrategy extends AbstractTraversalStrategy<DecorationStrategy> implements TraversalStrategy.DecorationStrategy {

    private static final PermissionEnforcingStrategy INSTANCE = new PermissionEnforcingStrategy();

    private PermissionEnforcingStrategy() {
    }

    public static PermissionEnforcingStrategy instance() {
        return INSTANCE;
    }

    @Override
    public void apply(final Traversal.Admin<?, ?> traversal) {
        if (!traversal.isRoot() || traversal.getSteps().isEmpty()) {
            return;
        }
        final String MARKER = Graph.Hidden.hide("permissionenforcerstrategy");

        if (traversal.getStartStep().getLabels().contains(MARKER)) {
            return;
        }
        traversal.getStartStep().addLabel(MARKER);

        Map<String, Object> opts = getConfigMap(traversal);
        if (opts.isEmpty()) {
            return;
        }
        System.out.println("INJECTING");
        PermissionTraverser traverser = new PermissionTraverser();
        GraphTraversal subtraversal = traverser.hasAccessTraversal((String) opts.get("userId"), (String) opts.get("permission"));

        TraversalHelper.insertTraversal(traversal.getEndStep(), subtraversal.asAdmin(), traversal);
        System.out.println(traversal.getSteps().stream().map(step -> step.toString()).collect(Collectors.joining("\n")));
    }

    private Map<String, Object> getConfigMap(final Traversal.Admin<?, ?> traversal) {
        Map<String, Object> config = new HashMap<>();
        traversal.getBytecode().getInstructions().spliterator().forEachRemaining(instruction -> {
            if (instruction.getOperator().equals("with")) {
                config.put((String) instruction.getArguments()[0], instruction.getArguments()[1]);
            }
        });
        return config;
    }
}