package yoshikihigo.tinypdg.scorpio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntFunction;

import yoshikihigo.tinypdg.Parallel;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.NodePairInfo;
import yoshikihigo.tinypdg.scorpio.data.PDGPairInfo;

/**
 * PDG の対と単体を走査してクローンペアを集める。
 *
 * <p>以前は SlicingThread という名前の Runnable だった。スレッドの骨組みは
 * Parallel に移り、ここに残るのは検出だけである。
 */
public final class CloneDetection {

	final private PDGPairInfo[] pdgpairs;
	final private PDG[] pdgs;

	final private SortedMap<PDG, SortedMap<PDGNode<?>, String>> mapPDGToPDGNodes;
	final private SortedMap<PDG, SortedMap<PDGEdge, String>> mapPDGToPDGEdges;
	final private int SIZE_THRESHOLD;

	/** この検出での辺の比較回数。 */
	final private AtomicLong comparisons = new AtomicLong();

	/**
	 * PDG の対と単体を走査してクローンペアを集める。
	 *
	 * <p>スレッドごとに見つけたペアを手元に集め、担当分が尽きたところで
	 * 重複を落としてから合流させる。重複の判定はスレッドの手元にあるペア
	 * 同士で行うので、どのペアが同じスレッドに渡るかで結果が変わりうる。
	 * 以前からそうである。
	 *
	 * @return この検出での辺の比較回数
	 */
	static long detect(final PDGPairInfo[] pdgpairs, final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGNode<?>, String>> mapPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, String>> mapPDGToPDGEdges,
			final SortedSet<ClonePairInfo> clonepairs,
			final int SIZE_THRESHOLD, final int threads) {

		Objects.requireNonNull(clonepairs, "\"clonepairs\" is null.");

		final CloneDetection detection = new CloneDetection(pdgpairs, pdgs,
				mapPDGToPDGNodes, mapPDGToPDGEdges, SIZE_THRESHOLD);

		// 添字は対を先に、単体を後に並べる。
		Parallel.forEach(pdgpairs.length + pdgs.length, threads,
				() -> new TreeSet<ClonePairInfo>(),
				(found, index) -> detection.detect(index, found),
				found -> {
					detection.removeDuplicates(found);
					clonepairs.addAll(found);
				});

		return detection.comparisons.get();
	}

	private CloneDetection(final PDGPairInfo[] pdgpairs, final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGNode<?>, String>> mapPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, String>> mapPDGToPDGEdges,
			final int SIZE_THRESHOLD) {
		Objects.requireNonNull(pdgpairs, "\"pdgpairs\" is null.");
		Objects.requireNonNull(pdgs, "\"pdgs\" is null.");
		Objects.requireNonNull(mapPDGToPDGNodes, "\"mapPDGToPDGNodes\"");
		Objects.requireNonNull(mapPDGToPDGEdges, "\"mapPDGToPDGEdges\" is null.");
		// 表明ではなく例外で止める。表明は既定で無効なので、以前は 0 や負の
		// 値がそのまま通っていた。
		if (SIZE_THRESHOLD < 1) {
			throw new IllegalArgumentException(
					"SIZE_THRESHOLD must be at least 1: " + SIZE_THRESHOLD);
		}
		this.pdgpairs = pdgpairs;
		this.pdgs = pdgs;
		this.mapPDGToPDGNodes = mapPDGToPDGNodes;
		this.mapPDGToPDGEdges = mapPDGToPDGEdges;
		this.SIZE_THRESHOLD = SIZE_THRESHOLD;
	}

	/**
	 * 添字が対の数より小さければその対の間で、そうでなければ残りの添字が
	 * 指す PDG の中でクローンペアを探す。
	 */
	private void detect(final int index,
			final SortedSet<ClonePairInfo> found) {

		if (index < this.pdgpairs.length) {
			final PDG pdgA = this.pdgpairs[index].left;
			final PDG pdgB = this.pdgpairs[index].right;
			try {
				this.detect(pdgA, pdgB, found);
			} catch (Exception e) {
				e.printStackTrace();
				System.err
						.println("ERROR: failed to detect clones between the method "
								+ pdgA.unit.name + " in " + pdgA.unit.path
								+ " and the method " + pdgB.unit.name + " in "
								+ pdgB.unit.path);
			}

		} else {
			final PDG pdg = this.pdgs[index - this.pdgpairs.length];
			try {
				this.detect(pdg, pdg, found);
			} catch (Exception e) {
				e.printStackTrace();
				System.err
						.println("ERROR: failed to detect clones in the method "
								+ pdg.unit.name + " in " + pdg.unit.path);
			}
		}
	}

	/**
	 * 2 つの PDG の間でクローンペアを探す。同じ PDG を 2 回渡すと、その中で
	 * 探す。
	 *
	 * <p>以前は「対の間」と「1 つの中」が別々の 100 行だった。違いは、
	 * 鍵を片方から集めるか両方から集めるか、辺の対を順序付きで見るか
	 * 順序なしで見るか、そして対の間では A の辺と B の辺の組み合わせだけを
	 * 見ること、の 3 点である。
	 */
	private void detect(final PDG pdgA, final PDG pdgB,
			final SortedSet<ClonePairInfo> clonepairs) {

		final boolean single = pdgA == pdgB;

		final List<SortedMap<PDGNode<?>, String>> nodeKeys = single
				? List.of(this.mapPDGToPDGNodes.get(pdgA))
				: List.of(this.mapPDGToPDGNodes.get(pdgA),
						this.mapPDGToPDGNodes.get(pdgB));
		final List<SortedMap<PDGEdge, String>> edgeKeys = single
				? List.of(this.mapPDGToPDGEdges.get(pdgA))
				: List.of(this.mapPDGToPDGEdges.get(pdgA),
						this.mapPDGToPDGEdges.get(pdgB));

		final List<PDGNode<?>[]> nodeGroups = groupByKey(nodeKeys,
				PDGNode<?>[]::new);
		final List<PDGEdge[]> edgeGroups = groupByKey(edgeKeys,
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
							checkedNodepairs, this.comparisons);
					final ClonePairInfo clonepair = slicing.perform();
					if (this.SIZE_THRESHOLD <= clonepair.size()) {
						clonepairs.add(clonepair);
					}
				}
			}
		}
	}

	/**
	 * 要素を鍵 (正規化テキスト) でまとめ、2 個以上あるまとまりだけを返す。
	 * 1 個しかない要素には相手がいないので、クローンの種にならない。
	 *
	 * <p>まとまりは配列 1 個で表す。Slicing は 2 つのノードが同値かどうかを
	 * この配列が同じものかで判定するので、同じまとまりの要素は同じ配列を
	 * 指していなければならない。
	 *
	 * <p>以前は鍵がテキストの String.hashCode() で、衝突した別のテキスト同士
	 * が 1 つのまとまりになっていた (issue #24)。
	 */
	private static <T extends Comparable<? super T>> List<T[]> groupByKey(
			final List<SortedMap<T, String>> elementToKeyMaps,
			final IntFunction<T[]> newArray) {

		final SortedMap<String, List<T>> keyToElements = new TreeMap<>();
		for (final SortedMap<T, String> elementToKey : elementToKeyMaps) {
			for (final Entry<T, String> entry : elementToKey.entrySet()) {
				keyToElements
						.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
						.add(entry.getKey());
			}
		}

		final List<T[]> groups = new ArrayList<>();
		for (final List<T> elements : keyToElements.values()) {
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

	/**
	 * 片方がもう片方の大部分を含むペアがあれば、小さい方を落とす。
	 *
	 * <p>以前は先に並んだ方しか落とさず、後に並んだ方が小さいときは何も
	 * 落とさなかった。どちらが残るかが並び順で決まっていた (issue #27)。
	 */
	private void removeDuplicates(final SortedSet<ClonePairInfo> clonepairs) {
		final ClonePairInfo[] pairs = clonepairs.toArray(new ClonePairInfo[0]);
		for (int i = 0; i < pairs.length; i++) {
			for (int j = i + 1; j < pairs.length; j++) {
				if (this.sameOnOkValue(pairs[i], pairs[j], 0.7f)) {
					clonepairs.remove(pairs[i].size() <= pairs[j].size()
							? pairs[i]
							: pairs[j]);
				}
			}
		}
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

	/** 相手の多いまとまりから見る。同じ大きさなら先頭の辺の順。 */
	private static final class PDGEdgesComparator implements
			Comparator<PDGEdge[]> {

		@Override
		public int compare(final PDGEdge[] o1, final PDGEdge[] o2) {
			final int lengthOrder = Integer.compare(o1.length, o2.length);
			if (0 != lengthOrder) {
				return lengthOrder;
			}
			return o1[0].compareTo(o2[0]);
		}
	}
}
