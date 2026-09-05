package yoshikihigo.tinypdg.scorpio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.NodePairInfo;

public class Slicing {

	final static private AtomicLong NUMBER_OF_COMPARISON = new AtomicLong(0);

	public static long getNumberOfComparison() {
		return NUMBER_OF_COMPARISON.get();
	}

	/** 比較回数を数え直す。1 回の検出ごとに呼ぶ。 */
	static void resetNumberOfComparison() {
		NUMBER_OF_COMPARISON.set(0);
	}

	final private SortedSet<NodePairInfo> checkedNodepairs;
	final private SortedMap<PDGNode<?>, PDGNode<?>[]> mappingPDGNodeToPDGNodes;
	final private SortedMap<PDGEdge, PDGEdge[]> mappingPDGEdgeToPDGEdges;
	final public String pathA;
	final public String pathB;
	final public PDGNode<?> startNodeA;
	final public PDGNode<?> startNodeB;

	private ClonePairInfo clonepair;

	public Slicing(final String pathA, final String pathB,
			final PDGNode<?> startNodeA, final PDGNode<?> startNodeB,
			final SortedMap<PDGNode<?>, PDGNode<?>[]> mappingPDGNodeToPDGNodes,
			final SortedMap<PDGEdge, PDGEdge[]> mappingPDGEdgeToPDGEdges,
			final SortedSet<NodePairInfo> checkedNodepairs) {
		this.checkedNodepairs = checkedNodepairs;
		this.pathA = pathA;
		this.pathB = pathB;
		this.startNodeA = startNodeA;
		this.startNodeB = startNodeB;
		this.mappingPDGNodeToPDGNodes = mappingPDGNodeToPDGNodes;
		this.mappingPDGEdgeToPDGEdges = mappingPDGEdgeToPDGEdges;
		this.clonepair = null;
	}

	public ClonePairInfo perform() {
		if (null == this.clonepair) {
			final SortedSet<PDGNode<?>> checkedNodesA = new TreeSet<>();
			final SortedSet<PDGNode<?>> checkedNodesB = new TreeSet<>();
			this.clonepair = this.perform(this.startNodeA, this.startNodeB,
					checkedNodesA, checkedNodesB);
		}
		return this.clonepair;
	}

	private ClonePairInfo perform(final PDGNode<?> nodeA,
			final PDGNode<?> nodeB, final SortedSet<PDGNode<?>> checkedNodesA,
			final SortedSet<PDGNode<?>> checkedNodesB) {

		final NodePairInfo nodepair = new NodePairInfo(nodeA, nodeB);
		if (this.checkedNodepairs.contains(nodepair)) {
			return new ClonePairInfo(this.pathA, this.pathB);
		}

		checkedNodesA.add(nodeA);
		checkedNodesB.add(nodeB);

		// 相手の多い辺から見る。逆方向へは辺の始点、順方向へは終点を辿る。
		final PDGEdgeComparator comparator = new PDGEdgeComparator(
				this.mappingPDGEdgeToPDGEdges);
		final List<ClonePairInfo> bClonepairs = this.enlarge(
				sortedBy(comparator, nodeA.getBackwardEdges()),
				sortedBy(comparator, nodeB.getBackwardEdges()),
				edge -> edge.fromNode, checkedNodesA, checkedNodesB);
		final List<ClonePairInfo> fClonepairs = this.enlarge(
				sortedBy(comparator, nodeA.getForwardEdges()),
				sortedBy(comparator, nodeB.getForwardEdges()),
				edge -> edge.toNode, checkedNodesA, checkedNodesB);

		final List<ClonePairInfo> candidates = new ArrayList<>();
		this.makeCandidates(candidates, bClonepairs);
		this.makeCandidates(candidates, fClonepairs);

		ClonePairInfo clonepair = new ClonePairInfo(this.pathA, this.pathB);
		for (final ClonePairInfo candidate : candidates) {
			if (clonepair.size() < candidate.size()) {
				clonepair = candidate;
			}
		}

		this.checkedNodepairs.add(nodepair);
		clonepair.addNodePair(nodepair);
		return clonepair;
	}

	private void makeCandidates(final List<ClonePairInfo> candidates,
			final List<ClonePairInfo> clonepairs) {

		Objects.requireNonNull(candidates, "\"candidates\" is null.");
		Objects.requireNonNull(clonepairs, "\"clonepairs\" is null.");

		for (final ClonePairInfo clonepair : clonepairs) {
			for (final ClonePairInfo candidate : candidates) {
				if (!candidate.conflict(clonepair)) {
					candidate.merge(clonepair);
				}
			}
			final ClonePairInfo newCandidate = new ClonePairInfo(this.pathA,
					this.pathB);
			newCandidate.merge(clonepair);
			candidates.add(newCandidate);
		}
	}

	private static SortedSet<PDGEdge> sortedBy(
			final Comparator<PDGEdge> comparator, final SortedSet<PDGEdge> edges) {
		final SortedSet<PDGEdge> sorted = new TreeSet<>(comparator);
		sorted.addAll(edges);
		return sorted;
	}

	/**
	 * 2 つのノードから、辺の先にある同値なノードの対へクローンペアを広げる。
	 *
	 * <p>{@code next} が辺のどちらの端へ進むかを決める。逆方向の辺なら始点、
	 * 順方向の辺なら終点である。以前は方向ごとに別のメソッドがあり、違いは
	 * fromNode と toNode の差だけだった。
	 */
	private List<ClonePairInfo> enlarge(final SortedSet<PDGEdge> edgesA,
			final SortedSet<PDGEdge> edgesB,
			final Function<PDGEdge, PDGNode<?>> next,
			final Set<PDGNode<?>> checkedNodesA,
			final Set<PDGNode<?>> checkedNodesB) {

		final List<ClonePairInfo> clonepairs = new ArrayList<>();

		EDGEA: for (final PDGEdge edgeA : edgesA) {

			final PDGNode<?> nodeA = next.apply(edgeA);
			if (checkedNodesA.contains(nodeA)
					|| checkedNodesB.contains(nodeA)) {
				continue EDGEA;
			}

			final PDGNode<?>[] equivalentNodesA = this.mappingPDGNodeToPDGNodes
					.get(nodeA);
			if (null == equivalentNodesA) {
				continue EDGEA;
			}

			EDGEB: for (final PDGEdge edgeB : edgesB) {

				final PDGNode<?> nodeB = next.apply(edgeB);
				if (checkedNodesB.contains(nodeB)
						|| checkedNodesA.contains(nodeB)) {
					continue EDGEB;
				}

				final PDGNode<?>[] equivalentNodesB = this.mappingPDGNodeToPDGNodes
						.get(nodeB);
				if (null == equivalentNodesB) {
					continue EDGEB;
				}

				if (edgeA == edgeB) {
					continue EDGEB;
				}

				NUMBER_OF_COMPARISON.incrementAndGet();
				if (equivalentNodesA == equivalentNodesB) {

					if (nodeA == nodeB) {
						continue EDGEB;
					}

					final SortedSet<PDGNode<?>> newCheckedNodesA = new TreeSet<>(
							checkedNodesA);
					final SortedSet<PDGNode<?>> newCheckedNodesB = new TreeSet<>(
							checkedNodesB);
					final ClonePairInfo clonepair = this.perform(nodeA, nodeB,
							newCheckedNodesA, newCheckedNodesB);
					clonepairs.add(clonepair);
				}
			}
		}

		return clonepairs;
	}

	class PDGEdgeComparator implements Comparator<PDGEdge> {

		final private SortedMap<PDGEdge, PDGEdge[]> mappingPDGEdgeToPDFEdge;

		PDGEdgeComparator(
				final SortedMap<PDGEdge, PDGEdge[]> mappingPDGEdgeToPDGEdges) {
			this.mappingPDGEdgeToPDFEdge = mappingPDGEdgeToPDGEdges;
		}

		@Override
		public int compare(final PDGEdge o1, final PDGEdge o2) {

			PDGEdge[] edgesA = this.mappingPDGEdgeToPDFEdge.get(o1);
			PDGEdge[] edgesB = this.mappingPDGEdgeToPDFEdge.get(o2);

			if (null == edgesA) {
				edgesA = new PDGEdge[0];
			}
			if (null == edgesB) {
				edgesB = new PDGEdge[0];
			}

			if (edgesA.length < edgesB.length) {
				return -1;
			} else if (edgesA.length > edgesB.length) {
				return 1;
			} else {
				return o1.compareTo(o2);
			}
		}
	}
}
