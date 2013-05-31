package yoshikihigo.tinypdg.scorpio;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;

import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.EdgePairInfo;

public class Slicing {

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
		CHECKED_EDGEPAIRS = new TreeSet<EdgePairInfo>();
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
		if (CHECKED_EDGEPAIRS.contains(edgepair)) {
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

		final ClonePairInfo predicessor = this.enlargeClonePair(backwardEdgesA,
				backwardEdgesB, predecessorsA, predecessorsB);
		final ClonePairInfo successor = this.enlargeClonePair(forwardEdgesA,
				forwardEdgesB, predecessorsA, predecessorsB);
		final ClonePairInfo clonepair = new ClonePairInfo(this.pathA,
				this.pathB);
		clonepair.merge(predicessor);
		clonepair.merge(successor);

		CHECKED_EDGEPAIRS.add(edgepair);
		clonepair.addEdgePair(edgepair);
		return clonepair;
	}

	private ClonePairInfo enlargeClonePair(final SortedSet<PDGEdge> edgesA,
			final SortedSet<PDGEdge> edgesB, final Set<PDGEdge> predecessorsA,
			final Set<PDGEdge> predecessorsB) {

		final ClonePairInfo clonepair = new ClonePairInfo(this.pathA,
				this.pathB);

		EDGEA: for (final PDGEdge edgeA : edgesA) {

			if (predecessorsA.contains(edgeA) || predecessorsB.contains(edgeA)) {
				continue EDGEA;
			}

			final List<PDGEdge> equivalentEdgesA = PDGEDGES.get(edgeA);
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

				final List<PDGEdge> equivalentEdgesB = PDGEDGES.get(edgeB);
				if (null == equivalentEdgesB) {
					continue EDGEB;
				}

				if (equivalentEdgesA == equivalentEdgesB) {

					if ((0 == edgeA.fromNode.compareTo(edgeB.fromNode))
							|| (0 == edgeA.toNode.compareTo(edgeB.toNode))) {
						continue EDGEB;
					}

					clonepair.addEdgePair(new EdgePairInfo(edgeA, edgeB));

					final SortedSet<PDGEdge> newPredicessorsA = new TreeSet<PDGEdge>(
							predecessorsA);
					final SortedSet<PDGEdge> newPredicessorsB = new TreeSet<PDGEdge>(
							predecessorsB);
					final ClonePairInfo successor = this.perform(edgeA, edgeB,
							newPredicessorsA, newPredicessorsB);
					clonepair.merge(successor);
				}
			}
		}

		return clonepair;
	}
}
