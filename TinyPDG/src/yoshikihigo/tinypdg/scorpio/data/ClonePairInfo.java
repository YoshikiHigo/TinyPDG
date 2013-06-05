package yoshikihigo.tinypdg.scorpio.data;

import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.pdg.node.PDGMethodEnterNode;

public class ClonePairInfo implements Comparable<ClonePairInfo> {

	final public String pathA;
	final public String pathB;
	final private SortedSet<EdgePairInfo> edgePairs;

	public ClonePairInfo(final String pathA, final String pathB) {
		this.pathA = pathA;
		this.pathB = pathB;
		this.edgePairs = new TreeSet<EdgePairInfo>();
	}

	public void addEdgePair(final EdgePairInfo edgePair) {
		assert null != edgePair : "\"edgePair\" is null.";
		this.edgePairs.add(edgePair);
	}

	public void merge(final ClonePairInfo merged) {
		assert null != merged : "\"merged\" is null.";
		this.edgePairs.addAll(merged.edgePairs);
	}

	public CodeFragmentInfo getLeftCodeFragment() {
		final CodeFragmentInfo codefragment = new CodeFragmentInfo();
		for (final EdgePairInfo edgepair : this.edgePairs) {
			codefragment.merge(new CodeFragmentInfo(edgepair.edgeA.fromNode));
			codefragment.merge(new CodeFragmentInfo(edgepair.edgeA.toNode));
		}
		return codefragment;
	}

	public CodeFragmentInfo getRightCodeFragment() {
		final CodeFragmentInfo codefragment = new CodeFragmentInfo();
		for (final EdgePairInfo edgepair : this.edgePairs) {
			codefragment.merge(new CodeFragmentInfo(edgepair.edgeB.fromNode));
			codefragment.merge(new CodeFragmentInfo(edgepair.edgeB.toNode));
		}
		return codefragment;
	}

	public int compareTo(final ClonePairInfo clonepair) {

		final int leftOrder = this.getLeftCodeFragment().compareTo(
				clonepair.getLeftCodeFragment());
		if (0 != leftOrder) {
			return leftOrder;
		}

		final int rightOrder = this.getRightCodeFragment().compareTo(
				clonepair.getRightCodeFragment());
		if (0 != rightOrder) {
			return rightOrder;
		}

		return 0;
	}

	public int size() {
		final CodeFragmentInfo left = this.getLeftCodeFragment();
		final CodeFragmentInfo right = this.getRightCodeFragment();
		return Math.min(left.size(), right.size());
	}

	public SortedSet<EdgePairInfo> getEdgePairs() {
		final SortedSet<EdgePairInfo> edgepairs = new TreeSet<EdgePairInfo>();
		edgepairs.addAll(this.edgePairs);
		return edgepairs;
	}

	public SortedSet<NodePairInfo> getNodePairs() {
		final SortedSet<NodePairInfo> nodepairs = new TreeSet<NodePairInfo>();
		for (final EdgePairInfo edgepair : this.edgePairs) {
			if (!(edgepair.edgeA.fromNode instanceof PDGMethodEnterNode)
					&& !(edgepair.edgeB.fromNode instanceof PDGMethodEnterNode)) {
				final NodePairInfo nodepair1 = new NodePairInfo(
						edgepair.edgeA.fromNode, edgepair.edgeB.fromNode);
				nodepairs.add(nodepair1);
			}
			if (!(edgepair.edgeA.toNode instanceof PDGMethodEnterNode)
					&& !(edgepair.edgeB.toNode instanceof PDGMethodEnterNode)) {
				final NodePairInfo nodepair2 = new NodePairInfo(
						edgepair.edgeA.toNode, edgepair.edgeB.toNode);
				nodepairs.add(nodepair2);
			}
		}
		return nodepairs;
	}
}
