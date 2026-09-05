package yoshikihigo.tinypdg.scorpio.data;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.pdg.node.PDGNode;

public class ClonePairInfo implements Comparable<ClonePairInfo> {

	final public String pathA;
	final public String pathB;
	final private SortedSet<NodePairInfo> nodePairs;

	/**
	 * ノード対から導いたもの。左右それぞれのノードの集合とコード片。
	 *
	 * <p>compareTo、size、conflict はこれを使う。以前は呼ばれるたびにノード対
	 * から作り直していて、conflict は 1 回でコード片を 8 回作った。それが
	 * クローンペア同士を総当たりで比べる重複除去から呼ばれていた。ノード対が
	 * 増えたら捨て、次に要るときに作り直す。
	 */
	private Derived derived;

	private record Derived(SortedSet<PDGNode<?>> leftNodes,
			SortedSet<PDGNode<?>> rightNodes, CodeFragmentInfo left,
			CodeFragmentInfo right) {
	}

	public ClonePairInfo(final String pathA, final String pathB) {
		this.pathA = pathA;
		this.pathB = pathB;
		this.nodePairs = new TreeSet<>();
		this.derived = null;
	}

	private Derived derived() {
		if (null == this.derived) {
			final SortedSet<PDGNode<?>> leftNodes = new TreeSet<>();
			final SortedSet<PDGNode<?>> rightNodes = new TreeSet<>();
			final CodeFragmentInfo left = new CodeFragmentInfo();
			final CodeFragmentInfo right = new CodeFragmentInfo();
			for (final NodePairInfo pair : this.nodePairs) {
				leftNodes.add(pair.nodeA);
				rightNodes.add(pair.nodeB);
				left.merge(new CodeFragmentInfo(pair.nodeA));
				right.merge(new CodeFragmentInfo(pair.nodeB));
			}
			this.derived = new Derived(leftNodes, rightNodes, left, right);
		}
		return this.derived;
	}

	public void addNodePair(final NodePairInfo nodePair) {
		Objects.requireNonNull(nodePair, "\"nodePair\" is null.");
		this.nodePairs.add(nodePair);
		this.derived = null;
	}

	public void merge(final ClonePairInfo merged) {
		Objects.requireNonNull(merged, "\"merged\" is null.");
		this.nodePairs.addAll(merged.nodePairs);
		this.derived = null;
	}

	/** 左のコード片。写しを返すので、変えてもこのペアには影響しない。 */
	public CodeFragmentInfo getLeftCodeFragment() {
		return copyOf(this.derived().left());
	}

	/** 右のコード片。写しを返すので、変えてもこのペアには影響しない。 */
	public CodeFragmentInfo getRightCodeFragment() {
		return copyOf(this.derived().right());
	}

	private static CodeFragmentInfo copyOf(final CodeFragmentInfo fragment) {
		final CodeFragmentInfo copy = new CodeFragmentInfo();
		copy.merge(fragment);
		return copy;
	}

	public SortedSet<PDGNode<?>> getLeftNodes() {
		return Collections.unmodifiableSortedSet(this.derived().leftNodes());
	}

	public SortedSet<PDGNode<?>> getRightNodes() {
		return Collections.unmodifiableSortedSet(this.derived().rightNodes());
	}

	@Override
	public int compareTo(final ClonePairInfo clonepair) {

		final Derived mine = this.derived();
		final Derived other = clonepair.derived();

		final int leftOrder = mine.left().compareTo(other.left());
		if (0 != leftOrder) {
			return leftOrder;
		}

		return mine.right().compareTo(other.right());
	}

	public int size() {
		final Derived derived = this.derived();
		return Math.min(derived.left().size(), derived.right().size());
	}

	public boolean conflict(final ClonePairInfo clonepair) {
		Objects.requireNonNull(clonepair, "\"clonepair\" is null.");
		final Derived mine = this.derived();
		final Derived other = clonepair.derived();
		return mine.left().conflict(other.left())
				|| mine.right().conflict(other.right())
				|| mine.left().conflict(other.right())
				|| mine.right().conflict(other.left());
	}

	public SortedSet<NodePairInfo> getNodePairs() {
		final SortedSet<NodePairInfo> nodepairs = new TreeSet<>();
		nodepairs.addAll(this.nodePairs);
		return nodepairs;
	}
}
