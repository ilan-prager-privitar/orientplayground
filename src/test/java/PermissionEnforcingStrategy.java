import java.util.HashMap;
import java.util.Map;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy.DecorationStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.Mutating;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.OptionsStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.verification.VerificationException;

public final class PermissionEnforcingStrategy extends AbstractTraversalStrategy<DecorationStrategy> implements TraversalStrategy.DecorationStrategy {

    private static final PermissionEnforcingStrategy INSTANCE = new PermissionEnforcingStrategy();

    private PermissionEnforcingStrategy() {
    }

    @Override
    public void apply(final Traversal.Admin<?, ?> traversal) {
//        Map<String, Object> opts = traversal.getStrategies().getStrategy(OptionsStrategy.class).get().getOptions();
        for (final Step step : traversal.getSteps()) {
            System.out.println(step);
        }
    }

    private Map<String, Object> getConfigMap(final Traversal.Admin<?, ?> traversal) {
        Map<String, Object> config = new HashMap<>();
        traversal.getBytecode().getInstructions().spliterator().forEachRemaining(instruction -> {
            if (instruction.getOperator().equals("with")) {
                config.put((String)instruction.getArguments()[0], instruction.getArguments()[1]);
            }
        });
        return config;
    }


    public static PermissionEnforcingStrategy instance() {
        return INSTANCE;
    }
}