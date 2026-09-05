package yoshikihigo.tinypdg.scorpio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.NodePairInfo;
import yoshikihigo.tinypdg.scorpio.data.PDGPairInfo;

public class SlicingThread implements Runnable {

	/**
	 * 取り出し位置。1 回の検出ごとに用意する。
	 *
	 * <p>以前はこれらが static だった。JVM 内の全ての検出で共有され
	 * 巻き戻らないので、2 回目は数え終わった位置から始まり、クローンが
	 * 1 件も見つからないまま正常終了していた。
	 */
	final private AtomicInteger nextPair;
	final private AtomicInteger nextSingle;

	final private PDGPairInfo[] pdgpairs;
	final private PDG[] pdgs;

	final private SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mapPDGToPDGNodes;
	final private SortedMap<PDG, SortedMap<PDGEdge, Integer>> mapPDGToPDGEdges;
	final private SortedSet<ClonePairInfo> clonepairs;
	final private int SIZE_THRESHOLD;

	/**
	 * PDG の対と単体を走査してクローンペアを集める。
	 */
	static void detect(final PDGPairInfo[] pdgpairs, final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mapPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mapPDGToPDGEdges,
			final SortedSet<ClonePairInfo> clonepairs,
			final int SIZE_THRESHOLD, final int threads) {

		// 比較回数は 1 回の検出についての数である。
		Slicing.resetNumberOfComparison();

		final AtomicInteger nextPair = new AtomicInteger(0);
		final AtomicInteger nextSingle = new AtomicInteger(0);
		try (final ExecutorService pool = Executors
				.newFixedThreadPool(threads)) {
			for (int i = 0; i < threads; i++) {
				pool.execute(new SlicingThread(nextPair, nextSingle, pdgpairs,
						pdgs, mapPDGToPDGNodes, mapPDGToPDGEdges, clonepairs,
						SIZE_THRESHOLD));
			}
		}
	}

	private SlicingThread(final AtomicInteger nextPair,
			final AtomicInteger nextSingle,
			final PDGPairInfo[] pdgpairs,
			final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mapPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mapPDGToPDGEdges,
			final SortedSet<ClonePairInfo> clonepairs, final int SIZE_THRESHOLD) {
		Objects.requireNonNull(pdgpairs, "\"pdgpairs\" is null.");
		Objects.requireNonNull(pdgs, "\"pdgs\" is null.");
		Objects.requireNonNull(mapPDGToPDGNodes, "\"mapPDGToPDGNodes\"");
		Objects.requireNonNull(mapPDGToPDGEdges, "\"mapPDGToPDGEdges\" is null.");
		Objects.requireNonNull(clonepairs, "\"clonepairs\" is null.");
		assert 0 < SIZE_THRESHOLD : "\"THRESHOLD\" must be greater than 0.";
		this.nextPair = nextPair;
		this.nextSingle = nextSingle;
		this.pdgpairs = pdgpairs;
		this.pdgs = pdgs;
		this.mapPDGToPDGNodes = mapPDGToPDGNodes;
		this.mapPDGToPDGEdges = mapPDGToPDGEdges;
		this.clonepairs = clonepairs;
		this.SIZE_THRESHOLD = SIZE_THRESHOLD;
	}

	@Override
	public void run() {

		final SortedSet<ClonePairInfo> clonepairs = new TreeSet<>();

		for (int index = this.nextPair.getAndIncrement(); index < this.pdgpairs.length; index = this.nextPair
				.getAndIncrement()) {

			final PDG pdgA = this.pdgpairs[index].left;
			final PDG pdgB = this.pdgpairs[index].right;

			try {
				this.detect(pdgA, pdgB, clonepairs);
			} catch (Exception e) {
				e.printStackTrace();
				System.err
						.println("ERROR: failed to detect clones between the method "
								+ pdgA.unit.name + " in " + pdgA.unit.path
								+ " and the method " + pdgB.unit.name + " in "
								+ pdgB.unit.path);
			}
		}

		for (int index = this.nextSingle.getAndIncrement(); index < this.pdgs.length; index = this.nextSingle
				.getAndIncrement()) {

			final PDG pdg = this.pdgs[index];

			try {
				this.detect(pdg, pdg, clonepairs);
			} catch (Exception e) {
				e.printStackTrace();
				System.err
						.println("ERROR: failed to detect clones in the method "
								+ pdg.unit.name + " in " + pdg.unit.path);
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

	/**
	 * 2 つの PDG の間でクローンペアを探す。同じ PDG を 2 回渡すと、その中で
	 * 探す。
	 *
	 * <p>以前は「対の間」と「1 つの中」が別々の 100 行だった。違いは、
	 * ハッシュを片方から集めるか両方から集めるか、辺の対を順序付きで見るか
	 * 順序なしで見るか、そして対の間では A の辺と B の辺の組み合わせだけを
	 * 見ること、の 3 点である。
	 */
	private void detect(final PDG pdgA, final PDG pdgB,
			final SortedSet<ClonePairInfo> clonepairs) {

		final boolean single = pdgA == pdgB;

		final List<SortedMap<PDGNode<?>, Integer>> nodeHashes = single
				? List.of(this.mapPDGToPDGNodes.get(pdgA))
				: List.of(this.mapPDGToPDGNodes.get(pdgA),
						this.mapPDGToPDGNodes.get(pdgB));
		final List<SortedMap<PDGEdge, Integer>> edgeHashes = single
				? List.of(this.mapPDGToPDGEdges.get(pdgA))
				: List.of(this.mapPDGToPDGEdges.get(pdgA),
						this.mapPDGToPDGEdges.get(pdgB));

		final List<PDGNode<?>[]> nodeGroups = groupByHash(nodeHashes,
				PDGNode<?>[]::new);
		final List<PDGEdge[]> edgeGroups = groupByHash(edgeHashes,
				PDGEdge[]::new);
		final SortedMap<PDGNode<?>, PDGNode<?>[]> mappingPDGNodeToPDGNodes = indexByMember(
				nodeGroups);
		final SortedMap<PDGEdge, PDGEdge[]> mappingPDGEdgeToPDGEdges = indexByMember(
				edgeGroups);

		final SortedSet<PDGEdge[]> sortedPDGEdges = new TreeSet<>(
				new PDGEdgesComparator());
		sortedPDGEdges.addAll(edgeGroups);

		final SortedSet<PDGEdge> edgesA = pdgA.getAllEdges();
		final SortedSet<PDGEdge> edgesB = pdgB.getAllEdges();

		final SortedSet<NodePairInfo> checkedNodepairs = new TreeSet<>();
		for (final PDGEdge[] edges : sortedPDGEdges) {
			for (int x = 0; x < edges.length; x++) {
				// 1 つの中では順序なしの組を、対の間では順序付きの組を見る。
				for (int y = single ? x + 1 : 0; y < edges.length; y++) {

					if (x == y) {
						continue;
					}

					final PDGEdge edgeA = edges[x];
					final PDGEdge edgeB = edges[y];

					// 対の間では、A の辺と B の辺の組み合わせだけを見る。
					if (!single && !(edgesA.contains(edgeA) && edgesB
							.contains(edgeB))) {
						continue;
					}

					final NodePairInfo nodepair = new NodePairInfo(
							edgeA.fromNode, edgeB.fromNode);
					if (checkedNodepairs.contains(nodepair)) {
						continue;
					}

					if (edgeA.connectedWith(edgeB)) {
						continue;
					}

					final Slicing slicing = new Slicing(pdgA.unit.path,
							pdgB.unit.path, edgeA.fromNode, edgeB.fromNode,
							mappingPDGNodeToPDGNodes, mappingPDGEdgeToPDGEdges,
							checkedNodepairs);
					final ClonePairInfo clonepair = slicing.perform();
					if (this.SIZE_THRESHOLD <= clonepair.size()) {
						clonepairs.add(clonepair);
					}
				}
			}
		}
	}

	/**
	 * 要素をハッシュ値でまとめ、2 個以上あるまとまりだけを返す。1 個しか
	 * ない要素には相手がいないので、クローンの種にならない。
	 *
	 * <p>まとまりは配列 1 個で表す。Slicing は 2 つのノードが同値かどうかを
	 * この配列が同じものかで判定するので、同じまとまりの要素は同じ配列を
	 * 指していなければならない。
	 */
	private static <T extends Comparable<? super T>> List<T[]> groupByHash(
			final List<SortedMap<T, Integer>> elementToHashMaps,
			final IntFunction<T[]> newArray) {

		final SortedMap<Integer, List<T>> hashToElements = new TreeMap<>();
		for (final SortedMap<T, Integer> elementToHash : elementToHashMaps) {
			for (final Entry<T, Integer> entry : elementToHash.entrySet()) {
				hashToElements
						.computeIfAbsent(entry.getValue(), h -> new ArrayList<>())
						.add(entry.getKey());
			}
		}

		final List<T[]> groups = new ArrayList<>();
		for (final List<T> elements : hashToElements.values()) {
			if (1 < elements.size()) {
				groups.add(elements.toArray(newArray.apply(0)));
			}
		}
		return groups;
	}

	/** 各要素から、それが属するまとまりを引けるようにする。 */
	private static <T extends Comparable<? super T>> SortedMap<T, T[]> indexByMember(
			final List<T[]> groups) {

		final SortedMap<T, T[]> index = new TreeMap<>();
		for (final T[] group : groups) {
			for (final T member : group) {
				index.put(member, group);
			}
		}
		return index;
	}

	private boolean sameOnGoodValue(final ClonePairInfo pair1,
			final ClonePairInfo pair2, final float threshold) {

		final SortedSet<PDGNode<?>> nodes1A = pair1.getLeftNodes();
		final SortedSet<PDGNode<?>> nodes2A = pair2.getLeftNodes();
		final SortedSet<PDGNode<?>> intersectionA = new TreeSet<>();
		intersectionA.addAll(nodes1A);
		intersectionA.retainAll(nodes2A);
		final SortedSet<PDGNode<?>> unionA = new TreeSet<>();
		unionA.addAll(nodes1A);
		unionA.addAll(nodes2A);

		final SortedSet<PDGNode<?>> nodes1B = pair1.getRightNodes();
		final SortedSet<PDGNode<?>> nodes2B = pair2.getRightNodes();
		final SortedSet<PDGNode<?>> intersectionB = new TreeSet<>();
		intersectionB.addAll(nodes1B);
		intersectionB.retainAll(nodes2B);
		final SortedSet<PDGNode<?>> unionB = new TreeSet<>();
		unionB.addAll(nodes1B);
		unionB.addAll(nodes2B);

		final float good = Math.min((float) intersectionA.size()
				/ (float) unionA.size(), (float) intersectionB.size()
				/ (float) unionB.size());

		return threshold <= good;
	}

	private boolean sameOnOkValue(final ClonePairInfo pair1,
			final ClonePairInfo pair2, final float threshold) {

		final SortedSet<PDGNode<?>> edges1A = pair1.getLeftNodes();
		final SortedSet<PDGNode<?>> edges2A = pair2.getLeftNodes();
		final SortedSet<PDGNode<?>> intersectionA = new TreeSet<>();
		intersectionA.addAll(edges1A);
		intersectionA.retainAll(edges2A);

		final SortedSet<PDGNode<?>> edges1B = pair1.getRightNodes();
		final SortedSet<PDGNode<?>> edges2B = pair2.getRightNodes();
		final SortedSet<PDGNode<?>> intersectionB = new TreeSet<>();
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
