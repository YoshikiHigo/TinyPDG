package yoshikihigo.tinypdg.scorpio;

import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;

public class HashCalculationThread implements Runnable {

	/**
	 * 取り出し位置。1 回の計算ごとに用意する。
	 *
	 * <p>以前はこれが static だった。1 回の実行では各スレッドに次の PDG を
	 * 配る役目を果たすが、JVM 内の全ての計算で共有され巻き戻らないので、
	 * 2 回目は数え終わった位置から始まり、ハッシュが 1 つも計算されない
	 * まま正常終了していた。
	 */
	final private AtomicInteger next;

	final private PDG[] pdgs;
	final private SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mappingPDGToPDGNodes;
	final private SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges;

	/**
	 * 全ての PDG のノードと辺のハッシュを、スレッドを分けて計算する。
	 */
	public static void calculate(final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mappingPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges,
			final int threads) {

		final AtomicInteger next = new AtomicInteger(0);
		try (final ExecutorService pool = Executors
				.newFixedThreadPool(threads)) {
			for (int i = 0; i < threads; i++) {
				pool.execute(new HashCalculationThread(next, pdgs,
						mappingPDGToPDGNodes, mappingPDGToPDGEdges));
			}
		}
	}

	private HashCalculationThread(final AtomicInteger next,
			final PDG[] pdgs,
			final SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mappingPDGToPDGNodes,
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges) {

		Objects.requireNonNull(pdgs, "\"pdgs\" is null.");
		Objects.requireNonNull(mappingPDGToPDGNodes, "\"mappingPDGToPDGNodes\" is null.");
		Objects.requireNonNull(mappingPDGToPDGEdges, "\"mappingPDGToPDGEdges\" is null.");

		this.next = next;
		this.pdgs = pdgs;
		this.mappingPDGToPDGNodes = mappingPDGToPDGNodes;
		this.mappingPDGToPDGEdges = mappingPDGToPDGEdges;
	}

	@Override
	public void run() {

		for (int index = this.next.getAndIncrement(); index < this.pdgs.length; index = this.next
				.getAndIncrement()) {

			final PDG pdg = this.pdgs[index];

			try {

				final SortedMap<PDGNode<?>, Integer> mappingPDGNodeToHash = new TreeMap<>();
				for (final PDGNode<?> node : pdg.getAllNodes()) {

					final int hash = NormalizedText.normalize(node.core)
							.hashCode();
					mappingPDGNodeToHash.put(node, hash);
				}
				this.mappingPDGToPDGNodes.put(pdg, mappingPDGNodeToHash);

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
				this.mappingPDGToPDGEdges.put(pdg, mappingPDGEdgeToHash);

			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("ERROR: failed to process the method "
						+ pdg.unit.name + " in " + pdg.unit.path);
			}
		}
	}
}
