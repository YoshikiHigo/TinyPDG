package yoshikihigo.tinypdg.scorpio;

import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import yoshikihigo.tinypdg.Parallel;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGControlDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;

/**
 * PDG のノードと辺に、同値性の鍵になる正規化テキストを付ける。
 *
 * <p>ノードの鍵はそのノードの正規化テキスト、辺の鍵は両端のテキストと依存の
 * 種類を並べたものである。同じ鍵のノード同士、辺同士がクローン検出の種になる。
 *
 * <p>以前は正規化テキストの String.hashCode() (int) を鍵にしていた。
 * "Aa($1);" と "BB($1);" のように別のテキストが同じ値になり、別のメソッドを
 * 呼ぶ文同士がクローンとして報告された (issue #24)。テキストそのものを鍵に
 * すれば衝突はない。クラス名は歴史的なもので、計算するのはハッシュではなく
 * 鍵である。
 *
 * <p>以前は HashCalculationThread という名前の Runnable だった。スレッドの
 * 骨組みは Parallel に移り、ここに残るのは計算だけである。
 */
public final class HashCalculation {

	private HashCalculation() {
	}

	/**
	 * 全ての PDG のノードと辺の鍵を、スレッドを分けて計算する。
	 */
	public static void calculate(final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGNode<?>, String>> mappingPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, String>> mappingPDGToPDGEdges,
			final int threads) {

		Objects.requireNonNull(pdgs, "\"pdgs\" is null.");
		Objects.requireNonNull(mappingPDGToPDGNodes, "\"mappingPDGToPDGNodes\" is null.");
		Objects.requireNonNull(mappingPDGToPDGEdges, "\"mappingPDGToPDGEdges\" is null.");

		Parallel.forEach(pdgs.length, threads, index -> calculate(pdgs[index],
				mappingPDGToPDGNodes, mappingPDGToPDGEdges));
	}

	private static void calculate(final PDG pdg,
			final SortedMap<PDG, SortedMap<PDGNode<?>, String>> mappingPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, String>> mappingPDGToPDGEdges) {

		try {

			final SortedMap<PDGNode<?>, String> mappingPDGNodeToKey = new TreeMap<>();
			for (final PDGNode<?> node : pdg.getAllNodes()) {
				mappingPDGNodeToKey.put(node, NormalizedText.normalize(node.core));
			}
			mappingPDGToPDGNodes.put(pdg, mappingPDGNodeToKey);

			final SortedMap<PDGEdge, String> mappingPDGEdgeToKey = new TreeMap<>();
			for (final PDGEdge edge : pdg.getAllEdges()) {
				// 制御依存は真偽も鍵に含める。then と else を入れ替えたものは
				// 別物である (issue #30)。データ依存の変数名は正規化で番号に
				// なるので含めない。
				final String kind = edge instanceof PDGControlDependenceEdge control
						? edge.type + ":" + control.getDependenceString()
						: edge.type.toString();
				final String key = NormalizedText.normalize(edge.fromNode.core) + "-"
						+ kind + "->"
						+ NormalizedText.normalize(edge.toNode.core);
				mappingPDGEdgeToKey.put(edge, key);
			}
			mappingPDGToPDGEdges.put(pdg, mappingPDGEdgeToKey);

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("ERROR: failed to process the method "
					+ pdg.unit.name + " in " + pdg.unit.path);
		}
	}
}
