package yoshikihigo.tinypdg.scorpio;

import java.util.ArrayList;
import java.util.HashSet;
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
			final Set<PDGEdge> predecessorsA = new HashSet<PDGEdge>();
			final Set<PDGEdge> predecessorsB = new HashSet<PDGEdge>();
			this.clonepair = this.perform(this.startEdgeA, this.startEdgeB,
					predecessorsA, predecessorsB);
		}
		return this.clonepair;
	}

	private ClonePairInfo perform(final PDGEdge edgeA, final PDGEdge edgeB,
			final Set<PDGEdge> predecessorsA, final Set<PDGEdge> predecessorsB) {

		final EdgePairInfo edgepair = new EdgePairInfo(edgeA, edgeB);
		if (this.CHECKED_EDGEPAIRS.contains(edgepair)) {
			return new ClonePairInfo(this.pathA, this.pathB);
		}

		predecessorsA.add(edgeA);
		predecessorsB.add(edgeB);

		final SortedSet<PDGEdge> backwardEdgesA = edgeA.fromNode
				.getBackwardEdges();
		final SortedSet<PDGEdge> backwardEdgesB = edgeB.fromNode
				.getBackwardEdges();
		final SortedSet<PDGEdge> forwardEdgesA = edgeA.toNode.getForwardEdges();
		final SortedSet<PDGEdge> forwardEdgesB = edgeB.toNode.getForwardEdges();

		final List<ClonePairInfo> backwardClonepairs = this.enlargeClonePair(
				backwardEdgesA, backwardEdgesB, predecessorsA, predecessorsB);
		final List<ClonePairInfo> forwardClonepairs = this.enlargeClonePair(
				forwardEdgesA, forwardEdgesB, predecessorsA, predecessorsB);

		final List<ClonePairInfo> candidates = new ArrayList<ClonePairInfo>();
		for (final ClonePairInfo clonepair : backwardClonepairs) {
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
		for (final ClonePairInfo clonepair : forwardClonepairs) {
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

	private List<ClonePairInfo> enlargeClonePair(
			final SortedSet<PDGEdge> edgesA, final SortedSet<PDGEdge> edgesB,
			final Set<PDGEdge> predecessorsA, final Set<PDGEdge> predecessorsB) {

		final List<ClonePairInfo> clonepairs = new ArrayList<ClonePairInfo>();

		EDGEA: for (final PDGEdge edgeA : edgesA) {

			if (predecessorsA.contains(edgeA) || predecessorsB.contains(edgeA)) {
				continue EDGEA;
			}

			final List<PDGEdge> equivalentEdgesA = this.PDGEDGES.get(edgeA);
			if (null == equivalentEdgesA) {
				continue EDGEA;
			}

			EDGEB: for (final PDGEdge edgeB : edgesB) {

				if (edgeA == edgeB) {
					continue EDGEB;
				}

				if (predecessorsB.contains(edgeB)
						|| predecessorsA.contains(edgeB)) {
					continue EDGEB;
				}

				final List<PDGEdge> equivalentEdgesB = this.PDGEDGES.get(edgeB);
				if (null == equivalentEdgesB) {
					continue EDGEB;
				}

				NUMBER_OF_COMPARISON.incrementAndGet();
				if (equivalentEdgesA == equivalentEdgesB) {

					if ((0 == edgeA.fromNode.compareTo(edgeB.fromNode))
							|| (0 == edgeA.toNode.compareTo(edgeB.toNode))) {
						continue EDGEB;
					}

					final SortedSet<PDGEdge> newPredicessorsA = new TreeSet<PDGEdge>(
							predecessorsA);
					final SortedSet<PDGEdge> newPredicessorsB = new TreeSet<PDGEdge>(
							predecessorsB);
					final ClonePairInfo successor = this.perform(edgeA, edgeB,
							newPredicessorsA, newPredicessorsB);
					successor.addEdgePair(new EdgePairInfo(edgeA, edgeB));
					clonepairs.add(successor);
				}
			}
		}

		return clonepairs;
	}
}
