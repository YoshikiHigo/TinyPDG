package yoshikihigo.tinypdg.scorpio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.EdgePairInfo;
import yoshikihigo.tinypdg.scorpio.data.PDGPairInfo;

public class SlicingThread implements Runnable {

	final static private AtomicInteger PAIRINDEX = new AtomicInteger(0);
	final static private AtomicInteger SINGLEINDEX = new AtomicInteger(0);

	final private PDGPairInfo[] pdgpairs;
	final private PDG[] pdgs;

	final private SortedMap<PDG, SortedMap<PDGEdge, Integer>> mapPDGToPDGEdges;
	final private SortedMap<PDGEdge, String> mapPDGEdgeToFilePath;
	final private SortedSet<ClonePairInfo> clonepairs;
	final private int SIZE_THRESHOLD;

	SlicingThread(final PDGPairInfo[] pdgpairs, final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mapPDGToPDGEdges,
			final SortedMap<PDGEdge, String> mapPDGEdgeToFilePath,
			final SortedSet<ClonePairInfo> clonepairs, final int SIZE_THRESHOLD) {
		assert null != pdgpairs : "\"pdgpairs\" is null.";
		assert null != pdgs : "\"pdgs\" is null.";
		assert null != mapPDGToPDGEdges : "\"mapPDGToPDGEdges\" is null.";
		assert null != mapPDGEdgeToFilePath : "\"mapPDGEdgeToFilePath\" is null.";
		assert null != clonepairs : "\"clonepairs\" is null.";
		assert 0 < SIZE_THRESHOLD : "\"THRESHOLD\" must be greater than 0.";
		this.pdgpairs = pdgpairs;
		this.pdgs = pdgs;
		this.mapPDGToPDGEdges = mapPDGToPDGEdges;
		this.mapPDGEdgeToFilePath = mapPDGEdgeToFilePath;
		this.clonepairs = clonepairs;
		this.SIZE_THRESHOLD = SIZE_THRESHOLD;
	}

	@Override
	public void run() {

		final SortedSet<ClonePairInfo> clonepairs = new TreeSet<ClonePairInfo>();

		for (int index = PAIRINDEX.getAndIncrement(); index < this.pdgpairs.length; index = PAIRINDEX
				.getAndIncrement()) {

			final PDG leftPDG = this.pdgpairs[index].left;
			final PDG rightPDG = this.pdgpairs[index].right;

			final SortedMap<PDGEdge, Integer> leftMappingPDGEdgeToHash = this.mapPDGToPDGEdges
					.get(leftPDG);
			final SortedMap<PDGEdge, Integer> rightMappingPDGEdgeToHash = this.mapPDGToPDGEdges
					.get(rightPDG);

			final SortedSet<PDGEdge> leftPDGEdges = leftPDG.getAllEdges();
			final SortedSet<PDGEdge> rightPDGEdges = rightPDG.getAllEdges();

			final SortedMap<Integer, List<PDGEdge>> mappingHashToPDGEdges = new TreeMap<Integer, List<PDGEdge>>();
			this.registerEdges(mappingHashToPDGEdges, leftMappingPDGEdgeToHash);
			this.registerEdges(mappingHashToPDGEdges, rightMappingPDGEdgeToHash);

			final ConcurrentMap<PDGEdge, List<PDGEdge>> mappingPDGEdgeToPDGEdges = new ConcurrentHashMap<PDGEdge, List<PDGEdge>>();
			for (final List<PDGEdge> list : mappingHashToPDGEdges.values()) {
				if (1 < list.size()) {
					for (final PDGEdge edge : list) {
						mappingPDGEdgeToPDGEdges.put(edge, list);
					}
				}
			}

			final SortedSet<EdgePairInfo> edgepairsInClonepairs = new TreeSet<EdgePairInfo>();
			for (final List<PDGEdge> edges : mappingHashToPDGEdges.values()) {
				for (int x = 0; x < edges.size(); x++) {
					for (int y = x + 1; y < edges.size(); y++) {

						final PDGEdge edgeA = edges.get(x);
						final PDGEdge edgeB = edges.get(y);

						if ((leftPDGEdges.contains(edgeA) && leftPDGEdges
								.contains(edgeB))
								|| (rightPDGEdges.contains(edgeA) && rightPDGEdges
										.contains(edgeB))) {
							continue;
						}

						final EdgePairInfo edgepair = new EdgePairInfo(edgeA,
								edgeB);
						if (edgepairsInClonepairs.contains(edgepair)) {
							continue;
						}

						if (edgeA.connectedWith(edgeB)) {
							continue;
						}

						final String path1 = this.mapPDGEdgeToFilePath
								.get(edgeA);
						final String path2 = this.mapPDGEdgeToFilePath
								.get(edgeB);
						final Slicing slicing = new Slicing(path1, path2,
								edgeA, edgeB, mappingPDGEdgeToPDGEdges);
						final ClonePairInfo clonepair = slicing.perform();
						edgepairsInClonepairs.addAll(clonepair.getEdgePairs());
						if (this.SIZE_THRESHOLD <= clonepair.size()) {
							clonepairs.add(clonepair);
						}
					}
				}
			}
		}

		for (int index = SINGLEINDEX.getAndIncrement(); index < this.pdgs.length; index = SINGLEINDEX
				.getAndIncrement()) {

			final PDG pdg = this.pdgs[index];
			final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHash = this.mapPDGToPDGEdges
					.get(pdg);
			final SortedMap<Integer, List<PDGEdge>> mappingHashToPDGEdges = new TreeMap<Integer, List<PDGEdge>>();
			this.registerEdges(mappingHashToPDGEdges, mappingPDGEdgeToHash);

			final ConcurrentMap<PDGEdge, List<PDGEdge>> mappingPDGEdgeToPDGEdges = new ConcurrentHashMap<PDGEdge, List<PDGEdge>>();
			for (final List<PDGEdge> list : mappingHashToPDGEdges.values()) {
				if (1 < list.size()) {
					for (final PDGEdge edge : list) {
						mappingPDGEdgeToPDGEdges.put(edge, list);
					}
				}
			}

			final SortedSet<EdgePairInfo> edgepairsInClonepairs = new TreeSet<EdgePairInfo>();
			for (final List<PDGEdge> edges : mappingHashToPDGEdges.values()) {
				for (int x = 0; x < edges.size(); x++) {
					for (int y = x + 1; y < edges.size(); y++) {

						final PDGEdge edgeA = edges.get(x);
						final PDGEdge edgeB = edges.get(y);

						final EdgePairInfo edgepair = new EdgePairInfo(edgeA,
								edgeB);
						if (edgepairsInClonepairs.contains(edgepair)) {
							continue;
						}

						if (edgeA.connectedWith(edgeB)) {
							continue;
						}

						final String path1 = this.mapPDGEdgeToFilePath
								.get(edgeA);
						final String path2 = this.mapPDGEdgeToFilePath
								.get(edgeB);
						final Slicing slicing = new Slicing(path1, path2,
								edgeA, edgeB, mappingPDGEdgeToPDGEdges);
						final ClonePairInfo clonepair = slicing.perform();
						edgepairsInClonepairs.addAll(clonepair.getEdgePairs());
						if (this.SIZE_THRESHOLD <= clonepair.size()) {
							clonepairs.add(clonepair);
						}
					}
				}
			}
		}

		{
			final ClonePairInfo[] pairs = clonepairs
					.toArray(new ClonePairInfo[0]);
			for (int i = 0; i < pairs.length; i++) {
				for (int j = i + 1; j < pairs.length; j++) {
					if (this.same(pairs[i], pairs[j], 0.7f)) {
						if (pairs[i].size() <= pairs[j].size()) {
							clonepairs.remove(pairs[i]);
						}
					}
				}
			}
		}

		this.clonepairs.addAll(clonepairs);

	}

	private void registerEdges(
			final SortedMap<Integer, List<PDGEdge>> mappingHashToPDGEdges,
			final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHash) {

		assert null != mappingHashToPDGEdges : "\"mappingHashToPDGEdges\" is null.";
		assert null != mappingPDGEdgeToHash : "\"mappingPDGEdgeToHash\" is null.";

		for (final Entry<PDGEdge, Integer> entry : mappingPDGEdgeToHash
				.entrySet()) {
			final Integer hash = entry.getValue();
			final PDGEdge edge = entry.getKey();
			List<PDGEdge> edges = mappingHashToPDGEdges.get(hash);
			if (null == edges) {
				edges = new ArrayList<PDGEdge>();
				mappingHashToPDGEdges.put(hash, edges);
			}
			edges.add(edge);
		}
	}

	private boolean same(final ClonePairInfo pair1, final ClonePairInfo pair2,
			final float threshold) {

		final SortedSet<PDGEdge> edges1A = pair1.getLeftEdges();
		final SortedSet<PDGEdge> edges2A = pair2.getLeftEdges();
		final SortedSet<PDGEdge> intersectionA = new TreeSet<PDGEdge>();
		intersectionA.addAll(edges1A);
		intersectionA.retainAll(edges2A);
		final SortedSet<PDGEdge> unionA = new TreeSet<PDGEdge>();
		unionA.addAll(edges1A);
		unionA.addAll(edges2A);

		final SortedSet<PDGEdge> edges1B = pair1.getRightEdges();
		final SortedSet<PDGEdge> edges2B = pair2.getRightEdges();
		final SortedSet<PDGEdge> intersectionB = new TreeSet<PDGEdge>();
		intersectionB.addAll(edges1B);
		intersectionB.retainAll(edges2B);
		final SortedSet<PDGEdge> unionB = new TreeSet<PDGEdge>();
		unionB.addAll(edges1B);
		unionB.addAll(edges2B);

		final float good = Math.min((float) intersectionA.size()
				/ (float) unionA.size(), (float) intersectionB.size()
				/ (float) unionB.size());

		return threshold <= good;
	}
}
