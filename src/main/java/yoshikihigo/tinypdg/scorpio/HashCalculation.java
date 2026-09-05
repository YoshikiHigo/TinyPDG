package yoshikihigo.tinypdg.scorpio;

import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import yoshikihigo.tinypdg.Parallel;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;

/**
 * PDG のノードと辺に、正規化したテキストから求めたハッシュ値を付ける。
 *
 * <p>ノードのハッシュはそのノードの正規化テキストから、辺のハッシュは
 * 両端のテキストと依存の種類から求める。同じハッシュのノード同士、辺同士が
 * クローン検出の種になる。
 *
 * <p>以前は HashCalculationThread という名前の Runnable だった。スレッドの
 * 骨組みは Parallel に移り、ここに残るのは計算だけである。
 */
public final class HashCalculation {

	private HashCalculation() {
	}

	/**
	 * 全ての PDG のノードと辺のハッシュを、スレッドを分けて計算する。
	 */
	public static void calculate(final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mappingPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges,
			final int threads) {

		Objects.requireNonNull(pdgs, "\"pdgs\" is null.");
		Objects.requireNonNull(mappingPDGToPDGNodes, "\"mappingPDGToPDGNodes\" is null.");
		Objects.requireNonNull(mappingPDGToPDGEdges, "\"mappingPDGToPDGEdges\" is null.");

		Parallel.forEach(pdgs.length, threads, index -> calculate(pdgs[index],
				mappingPDGToPDGNodes, mappingPDGToPDGEdges));
	}

	private static void calculate(final PDG pdg,
			final SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mappingPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges) {

		try {

			final SortedMap<PDGNode<?>, Integer> mappingPDGNodeToHash = new TreeMap<>();
			for (final PDGNode<?> node : pdg.getAllNodes()) {
				final int hash = NormalizedText.normalize(node.core)
						.hashCode();
				mappingPDGNodeToHash.put(node, hash);
			}
			mappingPDGToPDGNodes.put(pdg, mappingPDGNodeToHash);

			final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHash = new TreeMap<>();
			for (final PDGEdge edge : pdg.getAllEdges()) {

				final String fromNodeText = NormalizedText
						.normalize(edge.fromNode.core);
				final String toNodeText = NormalizedText
						.normalize(edge.toNode.core);
				final StringBuilder edgeText = new StringBuilder();
				edgeText.append(fromNodeText);
				edgeText.append("-");
				edgeText.append(edge.type.toString());
				edgeText.append("->");
				edgeText.append(toNodeText);
				final int hash = edgeText.toString().hashCode();

				mappingPDGEdgeToHash.put(edge, hash);
			}
			mappingPDGToPDGEdges.put(pdg, mappingPDGEdgeToHash);

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("ERROR: failed to process the method "
					+ pdg.unit.name + " in " + pdg.unit.path);
		}
	}
}
