package yoshikihigo.tinypdg.scorpio;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.EdgePairInfo;

public class Slicing {

	final static private AtomicLong NUMBER_OF_COMPARISON = new AtomicLong(0);

	public static long getNumberOfComparison() {
		return NUMBER_OF_COMPARISON.get();
	}

	final private SortedSet<EdgePairInfo> CHECKED_EDGEPAIRS;
	final private ConcurrentMap<PDGEdge, List<PDGEdge>> PDGEDGES;
	final public String pathA;
	final public String pathB;
	final public PDGEdge startEdgeA;
	final public PDGEdge startEdgeB;

	private ClonePairInfo clonepair;

	public Slicing(final String pathA, final String pathB,
			final PDGEdge startEdgeA, final PDGEdge startEdgeB,
			final ConcurrentMap<PDGEdge, List<PDGEdge>> PDGEDGES) {
		this.CHECKED_EDGEPAIRS = new TreeSet<EdgePairInfo>();
		this.pathA = pathA;
		this.pathB = pathB;
		this.startEdgeA = startEdgeA;
		this.startEdgeB = startEdgeB;
		this.PDGEDGES = PDGEDGES;
		this.clonepair = null;
	}

	public ClonePairInfo perform() {
		if (null == this.clonepair) {
			final SortedSet<PDGEdge> checkedEdgesA = new TreeSet<PDGEdge>();
			final SortedSet<PDGEdge> checkedEdgesB = new TreeSet<PDGEdge>();
			this.clonepair = this.perform(this.startEdgeA, this.startEdgeB,
					checkedEdgesA, checkedEdgesB);
		}
		return this.clonepair;
	}

	private ClonePairInfo perform(final PDGEdge edgeA, final PDGEdge edgeB,
			final SortedSet<PDGEdge> checkedEdgesA,
			final SortedSet<PDGEdge> checkedEdgesB) {

		final EdgePairInfo edgepair = new EdgePairInfo(edgeA, edgeB);
		if (this.CHECKED_EDGEPAIRS.contains(edgepair)) {
			return new ClonePairInfo(this.pathA, this.pathB);
		}

		checkedEdgesA.add(edgeA);
		checkedEdgesB.add(edgeB);

		final SortedSet<PDGEdge> bEdgesA = edgeA.fromNode.getBackwardEdges();
		final SortedSet<PDGEdge> bEdgesB = edgeB.fromNode.getBackwardEdges();
		final SortedSet<PDGEdge> fEdgesA = edgeA.toNode.getForwardEdges();
		final SortedSet<PDGEdge> fEdgesB = edgeB.toNode.getForwardEdges();

		final List<ClonePairInfo> bClonepairs = this.enlargeClonePair(bEdgesA,
				bEdgesB, checkedEdgesA, checkedEdgesB);
		final List<ClonePairInfo> fClonepairs = this.enlargeClonePair(fEdgesA,
				fEdgesB, checkedEdgesA, checkedEdgesB);

		final List<ClonePairInfo> candidates = new ArrayList<ClonePairInfo>();
		this.makeCandidates(candidates, bClonepairs);
		this.makeCandidates(candidates, fClonepairs);

		ClonePairInfo clonepair = new ClonePairInfo(this.pathA, this.pathB);
		for (final ClonePairInfo candidate : candidates) {
			if (clonepair.size() < candidate.size()) {
				clonepair = candidate;
			}
		}

		this.CHECKED_EDGEPAIRS.add(edgepair);
		clonepair.addEdgePair(edgepair);
		return clonepair;
	}

	private void makeCandidates(final List<ClonePairInfo> candidates,
			final List<ClonePairInfo> clonepairs) {

		assert null != candidates : "\"candidates\" is null.";
		assert null != clonepairs : "\"clonepairs\" is null.";

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

	private List<ClonePairInfo> enlargeClonePair(
			final SortedSet<PDGEdge> edgesA, final SortedSet<PDGEdge> edgesB,
			final Set<PDGEdge> checkedEdgesA, final Set<PDGEdge> checkedEdgesB) {

		final List<ClonePairInfo> clonepairs = new ArrayList<ClonePairInfo>();

		EDGEA: for (final PDGEdge edgeA : edgesA) {

			if (checkedEdgesA.contains(edgeA) || checkedEdgesB.contains(edgeA)) {
				continue EDGEA;
			}

			final List<PDGEdge> equivalentEdgesA = this.PDGEDGES.get(edgeA);
			if (null == equivalentEdgesA) {
				continue EDGEA;
			}

			EDGEB: for (final PDGEdge edgeB : edgesB) {

				if (checkedEdgesB.contains(edgeB)
						|| checkedEdgesA.contains(edgeB)) {
					continue EDGEB;
				}

				final List<PDGEdge> equivalentEdgesB = this.PDGEDGES.get(edgeB);
				if (null == equivalentEdgesB) {
					continue EDGEB;
				}

				if (edgeA == edgeB) {
					continue EDGEB;
				}

				NUMBER_OF_COMPARISON.incrementAndGet();
				if (equivalentEdgesA == equivalentEdgesB) {

					if ((edgeA.fromNode == edgeB.fromNode)
							|| (edgeA.toNode == edgeB.toNode)) {
						continue EDGEB;
					}

					final SortedSet<PDGEdge> newCheckedEdgesA = new TreeSet<PDGEdge>(
							checkedEdgesA);
					final SortedSet<PDGEdge> newCheckedEdgesB = new TreeSet<PDGEdge>(
							checkedEdgesB);
					final ClonePairInfo clonepair = this.perform(edgeA, edgeB,
							newCheckedEdgesA, newCheckedEdgesB);
					clonepairs.add(clonepair);
				}
			}
		}

		return clonepairs;
	}
}
