package yoshikihigo.tinypdg.scorpio.io;

import java.util.SortedSet;

import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;

/**
 * 検出されたクローンペアを何らかの形式で書き出すものの基底。
 *
 * <p>以前は Writer という名前だった。graphviz のコマンドライン入口も
 * Writer という名前で、import を見ないとどちらか分からなかった。
 */
abstract public class ClonePairWriter {

	final protected String path;
	final protected SortedSet<ClonePairInfo> clonepairs;

	protected ClonePairWriter(final String path,
			final SortedSet<ClonePairInfo> clonepairs) {
		this.path = path;
		this.clonepairs = clonepairs;
	}

	abstract public void write();
}
