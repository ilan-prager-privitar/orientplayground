import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
// import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ACLLoadTest {

    private static Graph graph;

    @BeforeAll
    public static void setup() {
//        // in memory
//        graph = TinkerGraph.open();
        String DB_NAME = "loaddb";
        OrientDB orientDB = new OrientDB("remote:localhost","user","passw0rd", OrientDBConfig.defaultConfig());
        boolean recreate = false;
        if (recreate) {
            if (orientDB.exists(DB_NAME)) {
                System.out.println("Dropping db " + DB_NAME);
                orientDB.drop(DB_NAME);
            }
            orientDB.create(DB_NAME, ODatabaseType.PLOCAL);
        }
        //graph = new OrientGraphFactory("remote:localhost/" + DB_NAME,"root","rootpwd").getNoTx();
        graph = new OrientGraphFactory("remote:localhost/" + DB_NAME,"user","passw0rd").getTx();
//        graph = OrientGraph.open("remote:localhost/" + DB_NAME,"root","rootpwd");
    }

    public static void assertAccess(String resourceId, String userId, String permission, boolean expected) {
        Assertions.assertEquals(expected, GraphScenarioTestUtils.hasAccess(graph, resourceId, userId, permission),
                String.format("Failed for %s with access %s by %s", resourceId, permission, userId));
    }

    @Test
    public void loadGraph() {
//        new LoadScenario(() -> graph).createGraph();
    }

    @Test
    public void queryGraph() {
        new LoadScenario(() -> graph).queryGraph();
    }

    static class LoadScenario extends GraphScenario {

        public LoadScenario(Supplier<Graph> graph) {
            super(graph);
        }

        @Override
        public void createGraph() {
            // large groups - e.g. All users, All NA, All EMEA
            // a few levels of nested subgroups
            // a few levels of permissioned container hierarchies
            // a reasonable number of ACLs at each level (assuming that customers will manage most ACLs by groups as best practice doesn’t mean that they will) - 100?

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

            // users and all group (all group will start out only containing subgroups so that we can exercise subgroup traversal)
            // later, we'll add all users directly into the ALL group
            final Vertex allGroup = createGroup("all-0");

            System.out.println("Creating users");
            List<Vertex> allUsers = new ArrayList<>(NUM_TOTAL_USERS);
            IntStream.range(0, NUM_TOTAL_USERS).forEach((i) -> {
                Vertex user = createUser("user " + i);
                allUsers.add(user);
            });
            System.out.println("About to commut Users");
            System.out.println("Users created");

            System.out.println("Creating regional groups");
            // add regional groups
            addGroups("", allUsers.size(), 0, allUsers, allGroup, Arrays.asList(
                    new GroupLevel("region", NUM_REGIONS),
                    new GroupLevel("location", NUM_LOCATIONS_PER_REGION),
                    new GroupLevel("locationGroup", NUM_GROUPS_PER_LOCATION)
            ), 0);
            System.out.println("Regional groups created");

            System.out.println("Creating organizational groups");
            // add organizational groups
            addGroups("", allUsers.size(), 0, allUsers, allGroup, Arrays.asList(
                    new GroupLevel("division", NUM_DIVISIONS),
                    new GroupLevel("vpgroup", NUM_VP_GROUPS_PER_DIVISION),
                    new GroupLevel("directorgroup", NUM_DIRECTORS_PER_VP),
                    new GroupLevel("team", NUM_TEAMS_PER_DIRECTOR)
            ), 0);
            System.out.println("Organizational groups created");

            System.out.println("Creating adhoc groups");
            // add adhoc groups
            Random rand = new Random();
            IntStream.range(0, NUM_ADHOC_GROUPS).forEach((i) -> {
                Vertex adhocGroup = createGroup("adhoc->" + i);
                int size = NUM_ADHOC_MIN + (i % (NUM_ADHOC_MAX - NUM_ADHOC_MIN + 1));
                List<Vertex> users = rand.ints(size, 0, NUM_TOTAL_USERS).mapToObj(index -> allUsers.get(index)).collect(Collectors.toList());
                addUsersToGroup(users, adhocGroup, 0, users.size());
            });
            System.out.println("Adhoc groups created");

            // create glossary hierarchy, with 2 top level categories, wide and deep
            // wide case:
            // 6 categories, 5 levels deep
            // term distribution: bulk up categories at 3rd level with 300 terms each, then distribute 10 to every other category
            // deep case:
            // 1 category 10 levels deep
            // term distribution: bulk up 3 categories at 3rd level with 300 terms each, then distribute 5 to leaf categories
            System.out.println("Creating wide term hierarchy");
            Vertex wideRoot = createFolder("wide");
            addCategoriesAndTerms(wideRoot, Arrays.asList(
                new CategoryLevel(6, 10),
                new CategoryLevel(10, 10),
                new CategoryLevel(6, 300),
                new CategoryLevel(7, 10),
                new CategoryLevel(2, 10)
            ), 0);
            System.out.println("Created wide term hierarchy");

            System.out.println("Creating deep term hierarchy");
            Vertex deepRoot = createFolder("deep");
            addCategoriesAndTerms(deepRoot, Arrays.asList(
                    new CategoryLevel(1, 0),
                    new CategoryLevel(3, 0),
                    new CategoryLevel(3, 300),
                    new CategoryLevel(2, 0),
                    new CategoryLevel(2, 0),
                    new CategoryLevel(2, 0),
                    new CategoryLevel(3, 0),
                    new CategoryLevel(5, 0),
                    new CategoryLevel(2, 0),
                    new CategoryLevel(3, 5)
            ), 0);
            System.out.println("Created deep term hierarchy");

            System.out.println("Adding permissions");
            // deep case: at top level, assign 100 ACLs, including top level organizational group
            // test access for team group member to leaf term 10 levels down
            IntStream.range(0, 100).forEach((i) -> {
                addHasPermission(allUsers.get(i), deepRoot, "R");
            });
            // we stress the system by ensuring that we need to traverse up the group structure in order to get to a group that has access
            addHasPermission(allGroup, deepRoot, "R");

            System.out.println("Permissions added");

            Vertex userInTeam = createUser("user " + NUM_TOTAL_USERS + 1);
            Vertex teamGroup = graph.get().traversal().V().hasLabel("Group").has("name", "team->->1->0->0->0").next();
            addUsersToGroup(allUsers, allGroup, 0, allUsers.size());
            addToGroup(userInTeam, teamGroup);
        }

        public void queryGraph() {
            Vertex userInTeam = getByName("User", "user " + 200001);
            Vertex allGroup = getByName("Group", "all-0");
            List<Vertex> allUsers = getAllByType("User");
            String userInDeepNestedGroup = userInTeam.value("name");
            String directUser = allUsers.get(0).value("name");
            removeFromGroup(userInTeam, allGroup);

            System.out.println("Testing direct user and nested container hierarchy");
            System.out.println("-------------------------------------------------------------------");
            timeAccess(directUser);

            System.out.println("\nTesting deep access to nested groups and nested container hierarchy");
            System.out.println("-------------------------------------------------------------------");
            timeAccess(userInDeepNestedGroup);

            System.out.println("\nTesting shallow access to big group and nested container hierarchy");
            System.out.println("-------------------------------------------------------------------");
            addToGroup(userInTeam, allGroup);
            timeAccess(userInDeepNestedGroup);

            System.out.println("\nTesting bulk access");
            System.out.println("-------------------------------------------------------------------");
            timeBulkAccess(userInDeepNestedGroup);

            System.out.println("\nGeneral graph stats");
            System.out.println("-------------------------------------------------------------------");

            System.out.println("Vertices: " + graph.get().traversal().V().count().next());
            System.out.println("Edges: " + graph.get().traversal().E().count().next());
            System.out.println("Groups: " + graph.get().traversal().V().hasLabel("Group").count().next());
            System.out.println("Folders: " + graph.get().traversal().V().hasLabel("Folder").count().next());
            System.out.println("Terms: " + graph.get().traversal().V().hasLabel("Term").count().next());

            getGroupStats().forEach((key, list) -> {
                System.out.println(key + " -> " + list.stream().mapToInt(Integer::intValue).summaryStatistics());
            });
        }
    }

    private static void run(String label, Map<String, List<Double>> stats, Consumer<Void> func) {
        List<Double> testStats = stats.computeIfAbsent(label, (String) -> new ArrayList<Double>());
        long start = System.currentTimeMillis();
        func.accept(null);
        double millis = (System.currentTimeMillis() - start);
        testStats.add(millis);
    }

    private static void timeAccess(String userName) {
        int iterations = 20;
        Map<String, List<Double>> stats = new HashMap<>();
        IntStream.range(0, iterations).forEach(iteration -> {
            run("Access to deep root node", stats, (x) -> assertAccess("deep", userName, "R", true) );
            run("Access to deep leaf node", stats, (x) -> assertAccess("deep_0_0_0_0_1_0_0_2_1_1_3", userName, "R", true) );
            run("Access for write to deep root node", stats, (x) -> assertAccess("deep", userName, "W", false) );
            run("Access for write to deep leaf node", stats, (x) -> assertAccess("deep_0_0_0_0_1_0_0_2_1_1_3", userName, "W", false) );
        });
        stats.forEach((key, value) -> {
            System.out.println(key + " -> " + value.stream().mapToDouble(Double::doubleValue).summaryStatistics());
        });
    }

    private static void timeBulkAccess(String userInDeepNestedGroup) {
        int iterations = 20;
        Map<String, List<Double>> stats = new HashMap<>();
        IntStream.range(0, iterations).forEach(iteration -> {
            run("Ordered terms limit 200", stats, (x) -> GraphScenarioTestUtils.getAllOrderedByType(graph, "Term", 200));
            run("Accessible Ordered terms limit 200", stats, (x) ->GraphScenarioTestUtils.getAllAccessibleOrderedByType(graph, userInDeepNestedGroup, "R", "Term", 200));
        });
        stats.forEach((key, value) -> {
            System.out.println(key + " -> " + value.stream().mapToDouble(Double::doubleValue).summaryStatistics());
        });
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
