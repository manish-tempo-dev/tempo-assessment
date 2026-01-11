import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// The task:
// 1. Read and understand the Hierarchy data structure described in this file.
// 2. Implement filter() method.
// 3. Implement more test cases.
//
// The task should take 30-90 minutes.
//
// When assessing the submission, we will pay attention to:
// - correctness, efficiency, and clarity of the code;
// - the test cases.

/**
 * A {@code Hierarchy} stores an arbitrary <i>forest</i> (an ordered collection of ordered trees)
 * as an array of node IDs in the order of DFS traversal, combined with a parallel array of node depths.
 *
 * <p>Parent-child relationships are identified by the position in the array and the associated depth.
 * Each tree root has depth 0, its children have depth 1 and follow it in the array, their children have depth 2 and follow them, etc.
 *
 * <p>Example:
 * <pre>
 * nodeIds: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
 * depths:  0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2
 * </pre>
 *
 * <p>the forest can be visualized as follows:
 * <pre>
 * 1
 * - 2
 * - - 3
 * - - - 4
 * - 5
 * 6
 * - 7
 * 8
 * - 9
 * - 10
 * - - 11
 * </pre>
 * 1 is a parent of 2 and 5, 2 is a parent of 3, etc. Note that depth is equal to the number of hyphens for each node.
 *
 * <p>Invariants on the depths array:
 * <ul>
 *   <li>Depth of the first element is 0.</li>
 *   <li>If the depth of a node is {@code D}, the depth of the next node in the array can be:
 *     <ul>
 *       <li>{@code D + 1} if the next node is a child of this node;</li>
 *       <li>{@code D} if the next node is a sibling of this node;</li>
 *       <li>{@code d < D} - in this case the next node is not related to this node.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
interface Hierarchy {
    /** The number of nodes in the hierarchy. */
    int size();

    /**
     * Returns the unique ID of the node identified by the hierarchy index. The depth for this node will be {@code depth(index)}.
     * @param index must be non-negative and less than {@link #size()}
     */
    int nodeId(int index);

    /**
     * Returns the depth of the node identified by the hierarchy index. The unique ID for this node will be {@code nodeId(index)}.
     * @param index must be non-negative and less than {@link #size()}
     */
    int depth(int index);

    default String formatString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(nodeId(i)).append(":").append(depth(i));
        }
        sb.append("]");
        return sb.toString();
    }
}

/**
 * A node is present in the filtered hierarchy iff its node ID passes the predicate and all of its ancestors pass it as well.
 */
class HierarchyFilter {

    /**
     * Assumptions:
     * 1) Input hierarchy respects the depth invariants described in the Javadoc.
     * 2) The filtered hierarchy preserves the original node order and the original depth values
     *    for included nodes (since included nodes necessarily include all ancestors).
     * 3) If hierarchy.size() == 0, return an empty hierarchy.
     *
     * Time: O(n)
     * Space: O(maxDepth) auxiliary, plus output arrays.
     */
    public static Hierarchy filter(Hierarchy hierarchy, java.util.function.IntPredicate nodeIdPredicate) {
        int n = hierarchy.size();
        if (n == 0) return new ArrayBasedHierarchy(new int[0], new int[0]);

        // includedAtDepth[d] == whether the most recent node we've seen at depth d is included.
        // We grow this dynamically as we discover larger depths.
        boolean[] includedAtDepth = new boolean[8];
        int currentStackSize = 0; // number of valid entries in includedAtDepth, i.e., maxDepthSeen+1

        // First pass: count included nodes.
        int kept = 0;
        for (int i = 0; i < n; i++) {
            int d = hierarchy.depth(i);
            if (d < 0) throw new IllegalArgumentException("Negative depth at index " + i);

            if (d >= includedAtDepth.length) {
                includedAtDepth = grow(includedAtDepth, d + 1);
            }
            // Pop to depth d (i.e., forget deeper ancestors when we move to a shallower node)
            currentStackSize = Math.min(currentStackSize, d + 1);

            boolean parentIncluded = (d == 0) ? true : includedAtDepth[d - 1];
            boolean include = parentIncluded && nodeIdPredicate.test(hierarchy.nodeId(i));

            includedAtDepth[d] = include;
            if (d + 1 > currentStackSize) currentStackSize = d + 1;

            if (include) kept++;
        }

        if (kept == 0) return new ArrayBasedHierarchy(new int[0], new int[0]);

        // Second pass: fill output arrays.
        int[] outIds = new int[kept];
        int[] outDepths = new int[kept];

        includedAtDepth = new boolean[Math.max(8, includedAtDepth.length)];
        currentStackSize = 0;

        int w = 0;
        for (int i = 0; i < n; i++) {
            int id = hierarchy.nodeId(i);
            int d = hierarchy.depth(i);

            if (d >= includedAtDepth.length) {
                includedAtDepth = grow(includedAtDepth, d + 1);
            }
            currentStackSize = Math.min(currentStackSize, d + 1);

            boolean parentIncluded = (d == 0) ? true : includedAtDepth[d - 1];
            boolean include = parentIncluded && nodeIdPredicate.test(id);

            includedAtDepth[d] = include;
            if (d + 1 > currentStackSize) currentStackSize = d + 1;

            if (include) {
                outIds[w] = id;
                outDepths[w] = d;
                w++;
            }
        }

        return new ArrayBasedHierarchy(outIds, outDepths);
    }

    private static boolean[] grow(boolean[] arr, int minLen) {
        int newLen = arr.length;
        while (newLen < minLen) newLen = newLen * 2;
        boolean[] n = new boolean[newLen];
        System.arraycopy(arr, 0, n, 0, arr.length);
        return n;
    }
}

class ArrayBasedHierarchy implements Hierarchy {
    private final int[] nodeIds;
    private final int[] depths;

    public ArrayBasedHierarchy(int[] nodeIds, int[] depths) {
        this.nodeIds = nodeIds;
        this.depths = depths;
    }

    @Override
    public int size() {
        return depths.length;
    }

    @Override
    public int nodeId(int index) {
        return nodeIds[index];
    }

    @Override
    public int depth(int index) {
        return depths[index];
    }
}

class FilterTest {

    private static void assertHierarchyEquals(Hierarchy expected, Hierarchy actual) {
        assertEquals(expected.formatString(), actual.formatString());
    }

    @Test
    void testFilter() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
            new int[]{0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2}
        );
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> nodeId % 3 != 0);
        Hierarchy filteredExpected = new ArrayBasedHierarchy(
            new int[]{1, 2, 5, 8, 10, 11},
            new int[]{0, 1, 1, 0, 1, 2}
        );
        assertEquals(filteredExpected.formatString(), filteredActual.formatString());
    }

    @Test
    void testFilter_allPass_returnsSame() {
        Hierarchy h = new ArrayBasedHierarchy(
            new int[]{10, 20, 30, 40},
            new int[]{0, 1, 1, 0}
        );

        Hierarchy actual = HierarchyFilter.filter(h, id -> true);

        assertHierarchyEquals(h, actual);
    }

    @Test
    void testFilter_emptyHierarchy_returnsEmpty() {
        Hierarchy h = new ArrayBasedHierarchy(new int[0], new int[0]);

        Hierarchy actual = HierarchyFilter.filter(h, id -> true);

        assertHierarchyEquals(new ArrayBasedHierarchy(new int[0], new int[0]), actual);
    }

    @Test
    void testFilter_rootRejected_excludesWholeTree_butKeepsOtherTrees() {
        // Forest:
        // 1
        // - 2
        // 3
        // - 4
        Hierarchy h = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4},
            new int[]{0, 1, 0, 1}
        );

        // reject root 1, accept everything else by predicate
        Hierarchy actual = HierarchyFilter.filter(h, id -> id != 1);

        // 2 must be excluded because its ancestor (1) is excluded. Tree rooted at 3 remains.
        Hierarchy expected = new ArrayBasedHierarchy(
            new int[]{3, 4},
            new int[]{0, 1}
        );

        assertHierarchyEquals(expected, actual);
    }

    @Test
    void testFilter_parentRejected_excludesDescendants_evenIfTheyPassPredicate() {
        // Tree:
        // 1
        // - 2
        // - - 3
        // - 4
        Hierarchy h = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4},
            new int[]{0, 1, 2, 1}
        );

        // Reject node 2 only.
        Hierarchy actual = HierarchyFilter.filter(h, id -> id != 2);

        // 3 is excluded because ancestor 2 is excluded; 4 is sibling of 2 so it's fine.
        Hierarchy expected = new ArrayBasedHierarchy(
            new int[]{1, 4},
            new int[]{0, 1}
        );

        assertHierarchyEquals(expected, actual);
    }

    @Test
    void testFilter_siblingRejection_doesNotAffectOtherSiblingsOrTheirSubtrees() {
        // Tree:
        // 1
        // - 2
        // - - 3
        // - 4
        // - - 5
        Hierarchy h = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5},
            new int[]{0, 1, 2, 1, 2}
        );

        // Reject node 2; should exclude 2 and 3, but keep 4 and 5.
        Hierarchy actual = HierarchyFilter.filter(h, id -> id != 2);

        Hierarchy expected = new ArrayBasedHierarchy(
            new int[]{1, 4, 5},
            new int[]{0, 1, 2}
        );

        assertHierarchyEquals(expected, actual);
    }

    @Test
    void testFilter_allRootsRejected_returnsEmpty() {
        Hierarchy h = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5},
            new int[]{0, 1, 0, 1, 2}
        );

        // Reject all roots (depth 0 nodes): 1 and 3
        Hierarchy actual = HierarchyFilter.filter(h, id -> id != 1 && id != 3);

        assertHierarchyEquals(new ArrayBasedHierarchy(new int[0], new int[0]), actual);
    }

    @Test
    void testFilter_handlesDepthPopAcrossTrees_correctlyResetsAtDepth0() {
        // Forest:
        // 1
        // - 2
        // - - 3
        // 4
        // - 5
        // 6
        Hierarchy h = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5, 6},
            new int[]{0, 1, 2, 0, 1, 0}
        );

        // Reject node 2, but accept everything else.
        Hierarchy actual = HierarchyFilter.filter(h, id -> id != 2);

        // 1 stays, 2 and 3 removed, 4/5/6 unaffected.
        Hierarchy expected = new ArrayBasedHierarchy(
            new int[]{1, 4, 5, 6},
            new int[]{0, 0, 1, 0}
        );

        assertHierarchyEquals(expected, actual);
    }

    @Test
    void testFilter_negativeDepth_throws() {
        Hierarchy bad = new ArrayBasedHierarchy(
            new int[]{1},
            new int[]{-1}
        );

        assertThrows(IllegalArgumentException.class, () -> HierarchyFilter.filter(bad, id -> true));
    }

}