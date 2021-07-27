import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ACLLoadTest {

    private static Graph graph;

    @BeforeAll
    public static void setup() {
        // in memory
        graph = TinkerGraph.open();
        new LoadScenario(() -> graph).createGraph();
    }

    public static void assertAccess(String resourceId, String userId, String permission, boolean expected) {
        Assertions.assertEquals(expected, GraphScenarioTestUtils.hasAccess(graph, resourceId, userId, permission),
                String.format("Failed for %s with access %s by %s", resourceId, permission, userId));
    }

    @Test
    public void testSomething() {

    }

    static class LoadScenario extends GraphScenario {

        public LoadScenario(Supplier<Graph> graph) {
            super(graph);
        }

        @Override
        public void createGraph() {
//            large groups - e.g. All users, All NA, All EMEA
//            a few levels of nested subgroups - 10?
//            a few levels of permissioned container hierarchies - 10?
//            a reasonable number of ACLs at each level (assuming that customers will manage most ACLs by groups as best practice doesn’t mean that they will) - 100?

            // 200,000 users

            // 1 ALl group (1001) - 200,000 members
            // 10 nested regional groups (each has 40,000 users) - 20,000 members
            // each regional group has 10 nested subgroups representing location - 2,000 members
            // each location has 10 nested groups (e.g. HR, finance, etc.) - 2,000 members
            // 10 division groups (20,000) - GM - 20,000 members
            // each has 20 nested subgroups - VP - 1,000 members
            // each has 10 nested subgroups - director - 250 members
            // each has 10 groups - teams - 50 members
            // 30,000 adhoc groups of 3-30 people (30,000)
            // total: 51,000 groups

            int NUM_TOTAL_USERS = 200000;            // number of users in the enterprise
            int NUM_REGIONS = 10;                   // number of regions
            int NUM_LOCATIONS_PER_REGION = 10;      // number of office locations per regions
            int NUM_GROUPS_PER_LOCATION = 10;       // number of groups for each office

            int NUM_DIVISIONS = 10;                 // number of divisions
            int NUM_VP_GROUPS_PER_DIVISION = 20;    // number of VP groups per division
            int NUM_DIRECTORS_PER_VP = 10;          // number of directors for each office
            int NUM_TEAMS_PER_DIRECTOR = 10;        // number of groups for each director

            int NUM_ADHOC_GROUPS = 30000;
            int NUM_ADHOC_MIN = 3;
            int NUM_ADHOC_MAX = 30;

            // users and all group
            final Vertex allGroup = createGroup("ALL");

            List<Vertex> allUsers = new ArrayList<>(NUM_TOTAL_USERS);
            IntStream.range(0, NUM_TOTAL_USERS).forEach((i) -> {
                Vertex user = createUser("user " + i);
                addToGroup(user, allGroup);
                allUsers.add(user);
            });

            // add regional groups
            addGroups("", allUsers.size(), 0, allUsers, allGroup, Arrays.asList(
                    new GroupLevel("region", NUM_REGIONS),
                    new GroupLevel("location", NUM_LOCATIONS_PER_REGION),
                    new GroupLevel("locationGroup", NUM_GROUPS_PER_LOCATION)
            ), 0);

            // add organizational groups
            addGroups("", allUsers.size(), 0, allUsers, allGroup, Arrays.asList(
                    new GroupLevel("division", NUM_DIVISIONS),
                    new GroupLevel("vpgroup", NUM_VP_GROUPS_PER_DIVISION),
                    new GroupLevel("directorgroup", NUM_DIRECTORS_PER_VP),
                    new GroupLevel("team", NUM_TEAMS_PER_DIRECTOR)
            ), 0);

            // add adhoc groups
            Random rand = new Random();
            IntStream.range(0, NUM_ADHOC_GROUPS).forEach((i) -> {
                Vertex adhocGroup = createGroup("adhoc->" + i);
                int size = NUM_ADHOC_MIN + (i % (NUM_ADHOC_MAX - NUM_ADHOC_MIN + 1));
                List<Vertex> users = rand.ints(size, 0, NUM_TOTAL_USERS).mapToObj(index -> allUsers.get(i)).collect(Collectors.toList());
                addUsersToGroup(users, adhocGroup, 0, users.size());
            });

            System.out.println("Vertices: " + graph.get().traversal().V().count().next());
            System.out.println("Edges: " + graph.get().traversal().E().count().next());
            System.out.println("Groups: " + graph.get().traversal().V().hasLabel("Group").count().next());
            getStats().forEach((key, list) -> {
                System.out.println(key + " -> " + list.stream().mapToInt(Integer::intValue).summaryStatistics());
            });
        }
    }

//    @Test
//    public void printAllAccessibleToAllUsers() {
//        graph.traversal().V().hasLabel("User").forEachRemaining((user) -> {
//            String userId = user.value("name");
//            printAllAccessibleToUser(graph, userId, "R");
//            printAllAccessibleToUser(graph, userId, "W");
//        });
//    }
}
