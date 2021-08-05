import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

public abstract class GraphScenario {

    protected Supplier<Graph> graph;

    public GraphScenario(Supplier<Graph> graph) {
        this.graph = graph;
    }

    protected Vertex createUser(String name) {
        return graph.get().addVertex("User").property("name", name).element();
    }

    protected Vertex getByName(String type, String name) {
        return graph.get().traversal().V().hasLabel(type).property("name", name).next();
    }

    protected List<Vertex> getAllByType(String type) {
        return graph.get().traversal().V().hasLabel(type).fold().next();
    }

    protected Vertex createGroup(String name) {
        return graph.get().addVertex("Group").property("name", name).element();
    }

    protected Vertex createFolder(String name) {
        return graph.get().addVertex("Folder").property("name", name).element();
    }

    protected Vertex createTerm(String name) {
        return graph.get().addVertex("Term").property("name", name).element();
    }

    protected Edge addToGroup(Vertex user, Vertex group) {
        return user.addEdge("MEMBER_OF", group);
    }

    protected void removeFromGroup(Vertex user, Vertex group) {
        user.edges(Direction.OUT, "MEMBER_OF").forEachRemaining(edge -> {
            if (edge.inVertex().value("name").equals(group.value("name"))) {
                edge.remove();
            }
        });
    }

    protected Edge addToSuperGroup(Vertex group, Vertex superGroup) {
        return group.addEdge("HAS_SUPERGROUP", superGroup);
    }

    protected Edge addToFolder(Vertex object, Vertex folder) {
        return object.addEdge("IN_FOLDER", folder);
    }

    protected Edge addOwner(Vertex object, Vertex user) {
        return object.addEdge("OWNED_BY", user);
    }

    protected void addHasPermission(Vertex user, Vertex object, String permission) {
        user.addEdge("HAS_PERMISSION", object).property("permission", permission);
    }

    protected void makePublic(Vertex object) {
        object.property("public", true);
    }

    public abstract void createGraph();

    private Map<String, List<Integer>> groupStats = new HashMap<>();

    public Map<String, List<Integer>> getGroupStats() {
        return groupStats;
    }

    protected void addUsersToGroup(List<Vertex> allUsers, Vertex group, int start, int groupSize) {
        String name = group.value("name");
        String type = name.substring(0, name.indexOf("-"));
        if (!groupStats.containsKey(type)) {
            groupStats.put(type, new ArrayList<>());
        }
        groupStats.get(type).add(groupSize);
        int end = start + groupSize;
        for (int i = start; i < end; i++) {
            addToGroup(allUsers.get(i), group);
        }
    }

    private List group2Numusers = new ArrayList();

    protected void addGroups(String parentPrefix, int parentSize, int startIndex, List<Vertex> allUsers, Vertex supergroup, List<GroupLevel> groupLevels,
            int levelIndex) {
        if (levelIndex > groupLevels.size() - 1) {
            return;
        }
        GroupLevel level = groupLevels.get(levelIndex);
        IntStream.range(0, level.numPerLevel).forEach(i -> {
            String name = level.prefix + "->" + parentPrefix + "->" + i;
            Vertex group = createGroup(name);
            addToSuperGroup(group, supergroup);
            int numUsersInGroup = parentSize / level.numPerLevel;
            int startIndexForSubgroup = startIndex + (numUsersInGroup * i);
            group2Numusers.add(new Object[] { allUsers, group, startIndexForSubgroup, numUsersInGroup});
            addUsersToGroup(allUsers, group, startIndexForSubgroup, numUsersInGroup);
            addGroups(parentPrefix + "->" + i, numUsersInGroup, startIndexForSubgroup, allUsers, group, groupLevels, levelIndex + 1);
        });
    }

    protected void addCategoriesAndTerms(Vertex parentCategory, List<CategoryLevel> categoryLevels, int levelIndex) {
        if (levelIndex > categoryLevels.size() - 1) {
            return;
        }
        String parentName = parentCategory.value("name");
        CategoryLevel level = categoryLevels.get(levelIndex);
        IntStream.range(0, level.numChildCategories).forEach(i -> {
            String name = parentName + "_" + i;
            Vertex category = createFolder(name);
            // System.out.println("CATG: " + name);
            addToFolder(category, parentCategory);
            IntStream.range(0, level.numTermsPerLevel).forEach(j -> {
                String termName = name + "_" + j;
                Vertex term = createTerm(termName);
                addToFolder(term, category);
            });
            addCategoriesAndTerms(category, categoryLevels, levelIndex + 1);
        });
    }

    static class GroupLevel {

        String prefix;
        int numPerLevel;

        public GroupLevel(String prefix, int numPerLevel) {
            this.prefix = prefix;
            this.numPerLevel = numPerLevel;
        }
    }

    static class CategoryLevel {

        int numTermsPerLevel;
        int numChildCategories;

        public CategoryLevel(int numChildCategories, int numTermsPerLevel) {
            this.numChildCategories = numChildCategories;
            this.numTermsPerLevel = numTermsPerLevel;
        }
    }
}
