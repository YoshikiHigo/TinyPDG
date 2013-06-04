package yoshikihigo.tinypdg.scorpio;

import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.EdgePairInfo;

public class SlicingThread implements Runnable {

	final static private AtomicInteger INDEX = new AtomicInteger(0);

	final private ConcurrentMap<PDGEdge, List<PDGEdge>> mapPDGEdgeToPDGEdgeList;
	final private ConcurrentMap<PDGEdge, String> mapPDGEdgeToFilePath;
	final private SortedSet<ClonePairInfo> clonepairs;
	final private SortedSet<EdgePairInfo> edgepairsInClonepairs;
	final private int SIZE_THRESHOLD;

	SlicingThread(
			final ConcurrentMap<PDGEdge, List<PDGEdge>> mapPDGEdgeToPDGEdgeList,
			final ConcurrentMap<PDGEdge, String> mapPDGEdgeToFilePath,
			final SortedSet<ClonePairInfo> clonepairs,
			final SortedSet<EdgePairInfo> edgepairsInClonepairs,
			final int SIZE_THRESHOLD) {
		assert null != mapPDGEdgeToPDGEdgeList : "\"mapPDGEdgeToPDGEdgeList\" is null.";
		assert null != mapPDGEdgeToFilePath : "\"mapPDGEdgeToFilePath\" is null.";
		assert null != clonepairs : "\"clonepairs\" is null.";
		assert null != edgepairsInClonepairs : "\"edgepairsInClonepairs\"";
		assert 0 < SIZE_THRESHOLD : "\"THRESHOLD\" must be greater than 0.";
		this.mapPDGEdgeToPDGEdgeList = mapPDGEdgeToPDGEdgeList;
		this.mapPDGEdgeToFilePath = mapPDGEdgeToFilePath;
		this.clonepairs = clonepairs;
		this.edgepairsInClonepairs = edgepairsInClonepairs;
		this.SIZE_THRESHOLD = SIZE_THRESHOLD;
	}

	@Override
	public void run() {
		final List<PDGEdge>[] lists = this.mapPDGEdgeToPDGEdgeList.values()
				.toArray(new List[0]);
		for (int index = INDEX.getAndIncrement(); index < lists.length; index = INDEX
				.getAndIncrement()) {
			final List<PDGEdge> list = lists[index];
			for (int i = 0; i < list.size(); i++) {
				for (int j = i + 1; j < list.size(); j++) {

					final PDGEdge edgeA = list.get(i);
					final PDGEdge edgeB = list.get(j);

					final EdgePairInfo edgepair = new EdgePairInfo(edgeA, edgeB);
					if (this.edgepairsInClonepairs.contains(edgepair)) {
						continue;
					}

					if (edgeA.connectedWith(edgeB)) {
						continue;
					}

					final String path1 = this.mapPDGEdgeToFilePath.get(edgeA);
					final String path2 = this.mapPDGEdgeToFilePath.get(edgeB);
					final Slicing slicing = new Slicing(path1, path2, edgeA,
							edgeB, this.mapPDGEdgeToPDGEdgeList);
					final ClonePairInfo clonepair = slicing.perform();
					this.edgepairsInClonepairs.addAll(clonepair.getEdgePairs());
					if (this.SIZE_THRESHOLD <= clonepair.size()) {
						this.clonepairs.add(clonepair);
					}
				}
			}
		}
	}
}
