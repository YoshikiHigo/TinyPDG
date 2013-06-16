package yoshikihigo.tinypdg.scorpio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
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
	final private SortedSet<ClonePairInfo> clonepairs;
	final private int SIZE_THRESHOLD;

	SlicingThread(final PDGPairInfo[] pdgpairs, final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mapPDGToPDGEdges,
			final SortedSet<ClonePairInfo> clonepairs, final int SIZE_THRESHOLD) {
		assert null != pdgpairs : "\"pdgpairs\" is null.";
		assert null != pdgs : "\"pdgs\" is null.";
		assert null != mapPDGToPDGEdges : "\"mapPDGToPDGEdges\" is null.";
		assert null != clonepairs : "\"clonepairs\" is null.";
		assert 0 < SIZE_THRESHOLD : "\"THRESHOLD\" must be greater than 0.";
		this.pdgpairs = pdgpairs;
		this.pdgs = pdgs;
		this.mapPDGToPDGEdges = mapPDGToPDGEdges;
		this.clonepairs = clonepairs;
		this.SIZE_THRESHOLD = SIZE_THRESHOLD;
	}

	@Override
	public void run() {

		final SortedSet<ClonePairInfo> clonepairs = new TreeSet<ClonePairInfo>();

		for (int index = PAIRINDEX.getAndIncrement(); index < this.pdgpairs.length; index = PAIRINDEX
				.getAndIncrement()) {

			final PDG pdgA = this.pdgpairs[index].left;
			final PDG pdgB = this.pdgpairs[index].right;
			final String pathA = pdgA.unit.path;
			final String pathB = pdgB.unit.path;

			final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHashA = this.mapPDGToPDGEdges
					.get(pdgA);
			final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHashB = this.mapPDGToPDGEdges
					.get(pdgB);

			final SortedSet<PDGEdge> edgesA = pdgA.getAllEdges();
			final SortedSet<PDGEdge> edgesB = pdgB.getAllEdges();

			final SortedMap<Integer, List<PDGEdge>> mappingHashToPDGEdges = new TreeMap<Integer, List<PDGEdge>>();
			this.registerEdges(mappingHashToPDGEdges, mappingPDGEdgeToHashA);
			this.registerEdges(mappingHashToPDGEdges, mappingPDGEdgeToHashB);

			final SortedMap<PDGEdge, PDGEdge[]> mappingPDGEdgeToPDGEdges = new TreeMap<PDGEdge, PDGEdge[]>();
			for (final List<PDGEdge> list : mappingHashToPDGEdges.values()) {
				if (1 < list.size()) {
					final PDGEdge[] edges = list.toArray(new PDGEdge[0]);
					for (final PDGEdge edge : edges) {
						mappingPDGEdgeToPDGEdges.put(edge, edges);
					}
				}
			}

			final SortedSet<PDGEdge[]> sortedPDGEdges = new TreeSet<PDGEdge[]>(
					new PDGEdgesComparator());
			for (final List<PDGEdge> list : mappingHashToPDGEdges.values()) {
				if (1 < list.size()) {
					final PDGEdge[] edges = list.toArray(new PDGEdge[0]);
					sortedPDGEdges.add(edges);
				}
			}

			final SortedSet<EdgePairInfo> checkedEdgepairs = new TreeSet<EdgePairInfo>();
			for (final PDGEdge[] edges : sortedPDGEdges) {
				for (int x = 0; x < edges.length; x++) {
					for (int y = 0; y < edges.length; y++) {

						if (x == y) {
							continue;
						}

						final PDGEdge edgeA = edges[x];
						final PDGEdge edgeB = edges[y];

						if (!(edgesA.contains(edgeA) && edgesB.contains(edgeB))) {
							continue;
						}

						final EdgePairInfo edgepair = new EdgePairInfo(edgeA,
								edgeB);
						if (checkedEdgepairs.contains(edgepair)) {
							continue;
						}

						if (edgeA.connectedWith(edgeB)) {
							continue;
						}

						final Slicing slicing = new Slicing(pathA, pathB,
								edgeA, edgeB, mappingPDGEdgeToPDGEdges,
								checkedEdgepairs);
						final ClonePairInfo clonepair = slicing.perform();
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
			final String path = pdg.unit.path;

			final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHash = this.mapPDGToPDGEdges
					.get(pdg);
			final SortedMap<Integer, List<PDGEdge>> mappingHashToPDGEdges = new TreeMap<Integer, List<PDGEdge>>();
			this.registerEdges(mappingHashToPDGEdges, mappingPDGEdgeToHash);

			final SortedMap<PDGEdge, PDGEdge[]> mappingPDGEdgeToPDGEdges = new TreeMap<PDGEdge, PDGEdge[]>();
			for (final List<PDGEdge> list : mappingHashToPDGEdges.values()) {
				if (1 < list.size()) {
					final PDGEdge[] edges = list.toArray(new PDGEdge[0]);
					for (final PDGEdge edge : edges) {
						mappingPDGEdgeToPDGEdges.put(edge, edges);
					}
				}
			}

			final SortedSet<PDGEdge[]> sortedPDGEdges = new TreeSet<PDGEdge[]>(
					new PDGEdgesComparator());
			for (final List<PDGEdge> list : mappingHashToPDGEdges.values()) {
				if (1 < list.size()) {
					final PDGEdge[] edges = list.toArray(new PDGEdge[0]);
					sortedPDGEdges.add(edges);
				}
			}

			final SortedSet<EdgePairInfo> checkedEdgepairs = new TreeSet<EdgePairInfo>();
			for (final PDGEdge[] edges : sortedPDGEdges) {
				for (int x = 0; x < edges.length; x++) {
					for (int y = x + 1; y < edges.length; y++) {

						final PDGEdge edgeA = edges[x];
						final PDGEdge edgeB = edges[y];

						final EdgePairInfo edgepair = new EdgePairInfo(edgeA,
								edgeB);
						if (checkedEdgepairs.contains(edgepair)) {
							continue;
						}

						if (edgeA.connectedWith(edgeB)) {
							continue;
						}

						final Slicing slicing = new Slicing(path, path, edgeA,
								edgeB, mappingPDGEdgeToPDGEdges,
								checkedEdgepairs);
						final ClonePairInfo clonepair = slicing.perform();
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
					if (this.sameOnOkValue(pairs[i], pairs[j], 0.7f)) {
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

	private boolean sameOnGoodValue(final ClonePairInfo pair1,
			final ClonePairInfo pair2, final float threshold) {

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

	private boolean sameOnOkValue(final ClonePairInfo pair1,
			final ClonePairInfo pair2, final float threshold) {

		final SortedSet<PDGEdge> edges1A = pair1.getLeftEdges();
		final SortedSet<PDGEdge> edges2A = pair2.getLeftEdges();
		final SortedSet<PDGEdge> intersectionA = new TreeSet<PDGEdge>();
		intersectionA.addAll(edges1A);
		intersectionA.retainAll(edges2A);

		final SortedSet<PDGEdge> edges1B = pair1.getRightEdges();
		final SortedSet<PDGEdge> edges2B = pair2.getRightEdges();
		final SortedSet<PDGEdge> intersectionB = new TreeSet<PDGEdge>();
		intersectionB.addAll(edges1B);
		intersectionB.retainAll(edges2B);

		final float ok = Math.min(Math.max((float) intersectionA.size()
				/ (float) edges1A.size(), (float) intersectionA.size()
				/ (float) edges2A.size()), Math.max(
				(float) intersectionB.size() / (float) edges1B.size(),
				(float) intersectionB.size() / (float) edges2B.size()));

		return threshold <= ok;
	}

	class PDGEdgesComparator implements Comparator<PDGEdge[]> {

		@Override
		public int compare(final PDGEdge[] o1, final PDGEdge[] o2) {

			if (o1.length < o2.length) {
				return -1;
			} else if (o1.length > o2.length) {
				return 1;
			} else {
				return o1[0].compareTo(o2[0]);
			}
		}

	}
}
