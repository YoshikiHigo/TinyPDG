package yoshikihigo.tinypdg.scorpio.data;

import java.util.SortedSet;
import java.util.TreeSet;

public class ClonePairInfo {

	final private SortedSet<EdgePairInfo> edgePairs;

	public ClonePairInfo() {
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
}
