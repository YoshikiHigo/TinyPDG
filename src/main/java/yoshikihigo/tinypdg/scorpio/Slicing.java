package yoshikihigo.tinypdg.scorpio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import yoshikihigo.tinypdg.pdg.edge.PDGControlDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.NodePairInfo;

public class Slicing {

	/**
	 * 辺の比較回数。1 回の検出で共有するカウンタを CloneDetection が渡す。
	 *
	 * <p>以前は static なカウンタで、同じ JVM で検出を 2 回行うと数が混ざり、
	 * 検出の前に 0 に戻す手順が要った。
	 */
	final private AtomicLong comparisons;

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
			final SortedSet<NodePairInfo> checkedNodepairs,
			final AtomicLong comparisons) {
		this.comparisons = Objects.requireNonNull(comparisons,
				"\"comparisons\" is null.");
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
			this.clonepair = this.perform(this.startNodeA, this.startNodeB);
		}
		return this.clonepair;
	}

	/**
	 * 2 つのノードを起点に、依存辺を辿ってクローンペアを広げる。
	 *
	 * <p>手続きは再帰で書くのが自然である。ノードの対 (a, b) について、辺の
	 * 先にある同値なノードの対それぞれに同じ手続きを施し、返ってきたペアを
	 * 候補にまとめて最大のものを選ぶ。しかしこの再帰は実行依存の連鎖の長さ、
	 * つまりメソッドの文の数だけ深くなり、数千文のメソッドでスタックが尽きた
	 * (issue #16)。スレッドのスタックを大きくして逃げる手もあるが、その大きさ
	 * は JVM への助言に過ぎず、スレッド数分のアドレス空間も食う。
	 *
	 * <p>そこで再帰の 1 回分の状態を {@link Frame} に持たせ、明示的なスタック
	 * で辿る。子の呼び出しになるはずの対が見つかれば Frame を積み、辺を全て
	 * 見終えた Frame は結果を親に渡して降りる。対を訪れる順序、比較回数を
	 * 数える箇所、checkedNodepairs へ加える時点は再帰版と同じなので、検出の
	 * 結果は変わらない。
	 */
	private ClonePairInfo perform(final PDGNode<?> startA,
			final PDGNode<?> startB) {

		final Comparator<PDGEdge> comparator = new PDGEdgeComparator(
				this.mappingPDGEdgeToPDGEdges);
		final Deque<Frame> stack = new ArrayDeque<>();
		final Frame root = new Frame(startA, startB, new TreeSet<>(),
				new TreeSet<>());
		stack.push(root);

		while (!stack.isEmpty()) {
			final Frame frame = stack.peek();
			final Frame child = this.advance(frame, comparator);
			if (null != child) {
				stack.push(child);
				continue;
			}
			stack.pop();
			final Frame parent = stack.peek();
			if (null != parent) {
				parent.receive(frame.result);
			}
		}

		return root.result;
	}

	/**
	 * frame を、次の子の呼び出しか、終わりまで進める。
	 *
	 * @return 子として積む Frame。frame が終わったなら null で、結果は
	 *         frame.result にある
	 */
	private Frame advance(final Frame frame,
			final Comparator<PDGEdge> comparator) {

		if (!frame.started) {
			frame.started = true;
			if (this.checkedNodepairs.contains(frame.nodepair)) {
				frame.result = new ClonePairInfo(this.pathA, this.pathB);
				return null;
			}
			frame.checkedNodesA.add(frame.nodepair.nodeA);
			frame.checkedNodesB.add(frame.nodepair.nodeB);
			frame.sortEdges(comparator);
		}

		// 逆方向の辺 (始点へ辿る) を見終えたら順方向の辺 (終点へ辿る)。
		for (; frame.direction < 2; frame.direction++, frame.indexA = 0, frame.indexB = 0) {
			final Frame child = this.enlarge(frame);
			if (null != child) {
				return child;
			}
		}

		final List<ClonePairInfo> candidates = new ArrayList<>();
		this.makeCandidates(candidates, frame.bClonepairs);
		this.makeCandidates(candidates, frame.fClonepairs);

		ClonePairInfo clonepair = new ClonePairInfo(this.pathA, this.pathB);
		for (final ClonePairInfo candidate : candidates) {
			if (clonepair.size() < candidate.size()) {
				clonepair = candidate;
			}
		}

		this.checkedNodepairs.add(frame.nodepair);
		clonepair.addNodePair(frame.nodepair);
		frame.result = clonepair;
		return null;
	}

	/**
	 * frame の今の方向の辺を、覚えてある位置から見ていき、辺の先に同値な
	 * ノードの対を見つけたら、その対の Frame を返す。返す前に位置を進めて
	 * おくので、子が終わった後は続きから見られる。
	 *
	 * <p>以前は方向ごとに別のメソッドがあり、違いは fromNode と toNode の差
	 * だけだった。
	 *
	 * @return 子として積む Frame。この方向の辺を見終えたなら null
	 */
	private Frame enlarge(final Frame frame) {

		final List<PDGEdge> edgesA = frame.edgesA();
		final List<PDGEdge> edgesB = frame.edgesB();

		for (; frame.indexA < edgesA.size(); frame.indexA++, frame.indexB = 0) {

			final PDGEdge edgeA = edgesA.get(frame.indexA);
			final PDGNode<?> nodeA = frame.next(edgeA);
			if (frame.checkedNodesA.contains(nodeA)
					|| frame.checkedNodesB.contains(nodeA)) {
				continue;
			}

			final PDGNode<?>[] equivalentNodesA = this.mappingPDGNodeToPDGNodes
					.get(nodeA);
			if (null == equivalentNodesA) {
				continue;
			}

			while (frame.indexB < edgesB.size()) {

				final PDGEdge edgeB = edgesB.get(frame.indexB++);
				final PDGNode<?> nodeB = frame.next(edgeB);
				if (frame.checkedNodesB.contains(nodeB)
						|| frame.checkedNodesA.contains(nodeB)) {
					continue;
				}

				final PDGNode<?>[] equivalentNodesB = this.mappingPDGNodeToPDGNodes
						.get(nodeB);
				if (null == equivalentNodesB) {
					continue;
				}

				if (edgeA == edgeB) {
					continue;
				}

				// 依存の種類と、制御依存なら真偽も揃える。以前は先のノードの同値
				// だけを見ていたので、データ依存と制御依存が対応したり、if の then
				// と else を入れ替えたものが同型になったりしていた (issue #30)。
				if (!sameKind(edgeA, edgeB)) {
					continue;
				}

				this.comparisons.incrementAndGet();
				if (equivalentNodesA == equivalentNodesB && nodeA != nodeB) {
					// 再帰版はここで perform(nodeA, nodeB) を呼んでいた。見た
					// 印は呼び出しごとの写しに付けるので、兄弟の探索には及ばない。
					return new Frame(nodeA, nodeB,
							new TreeSet<>(frame.checkedNodesA),
							new TreeSet<>(frame.checkedNodesB));
				}
			}
		}

		return null;
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

	/**
	 * 2 つの辺が同じ種類の依存か。制御依存は真偽まで同じでなければならない。
	 * データ依存の変数名は見ない。正規化で番号に変わり、2 つの断片の間で
	 * 揃える手立てがない。
	 */
	private static boolean sameKind(final PDGEdge edgeA, final PDGEdge edgeB) {
		if (edgeA.type != edgeB.type) {
			return false;
		}
		if (edgeA instanceof PDGControlDependenceEdge controlA
				&& edgeB instanceof PDGControlDependenceEdge controlB) {
			return controlA.trueDependence == controlB.trueDependence;
		}
		return true;
	}

	private static List<PDGEdge> sortedBy(final Comparator<PDGEdge> comparator,
			final SortedSet<PDGEdge> edges) {
		final SortedSet<PDGEdge> sorted = new TreeSet<>(comparator);
		sorted.addAll(edges);
		return new ArrayList<>(sorted);
	}

	/**
	 * 再帰版の perform の 1 回の呼び出しが持っていた状態。
	 */
	private static final class Frame {

		final NodePairInfo nodepair;

		/** この呼び出しまでに見たノード。親の写しに自分の対を加えたもの。 */
		final SortedSet<PDGNode<?>> checkedNodesA;
		final SortedSet<PDGNode<?>> checkedNodesB;

		boolean started;

		/** 0 なら逆方向の辺を始点へ、1 なら順方向の辺を終点へ辿っている。 */
		int direction;
		int indexA;
		int indexB;

		/** 相手の多い辺から見る。 */
		List<PDGEdge> backwardEdgesA;
		List<PDGEdge> backwardEdgesB;
		List<PDGEdge> forwardEdgesA;
		List<PDGEdge> forwardEdgesB;

		/** 逆方向と順方向の子が返したペア。 */
		final List<ClonePairInfo> bClonepairs = new ArrayList<>();
		final List<ClonePairInfo> fClonepairs = new ArrayList<>();

		ClonePairInfo result;

		Frame(final PDGNode<?> nodeA, final PDGNode<?> nodeB,
				final SortedSet<PDGNode<?>> checkedNodesA,
				final SortedSet<PDGNode<?>> checkedNodesB) {
			this.nodepair = new NodePairInfo(nodeA, nodeB);
			this.checkedNodesA = checkedNodesA;
			this.checkedNodesB = checkedNodesB;
		}

		void sortEdges(final Comparator<PDGEdge> comparator) {
			this.backwardEdgesA = sortedBy(comparator,
					this.nodepair.nodeA.getBackwardEdges());
			this.backwardEdgesB = sortedBy(comparator,
					this.nodepair.nodeB.getBackwardEdges());
			this.forwardEdgesA = sortedBy(comparator,
					this.nodepair.nodeA.getForwardEdges());
			this.forwardEdgesB = sortedBy(comparator,
					this.nodepair.nodeB.getForwardEdges());
		}

		List<PDGEdge> edgesA() {
			return 0 == this.direction ? this.backwardEdgesA : this.forwardEdgesA;
		}

		List<PDGEdge> edgesB() {
			return 0 == this.direction ? this.backwardEdgesB : this.forwardEdgesB;
		}

		PDGNode<?> next(final PDGEdge edge) {
			return 0 == this.direction ? edge.fromNode : edge.toNode;
		}

		void receive(final ClonePairInfo clonepair) {
			(0 == this.direction ? this.bClonepairs : this.fClonepairs)
					.add(clonepair);
		}
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
