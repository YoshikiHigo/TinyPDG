package yoshikihigo.tinypdg.scorpio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import yoshikihigo.tinypdg.ast.JavaAstFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.PDGGeneration;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.PDGPairInfo;

/**
 * クローンペアの拡大が依存の種類と枝を揃えることを確かめる。
 */
class CloneDetectionTest {

	private static final File SAMPLE = Path.of(System.getProperty("user.dir"))
			.resolve("src/test/resources/samples/lang53_branchorder").toFile();

	/** 名前を挙げたメソッドの間でクローンを探す。 */
	private static SortedSet<ClonePairInfo> detect(final String... methodNames) {

		final Set<String> wanted = Set.of(methodNames);
		final List<MethodInfo> methods = JavaAstFactory
				.collectMethods(SAMPLE, JavaAstFactory.DEFAULT_JAVA_VERSION)
				.stream().filter(m -> wanted.contains(m.name)).toList();

		final PDG[] pdgs = PDGGeneration.buildInParallel(methods,
				// 実行依存は隣り合う文を全て繋ぐので、枝の向きの違いが見える
				// ように制御依存とデータ依存だけで作る。
				new PDGGeneration.Options(new PDG.Dependences(true, true, false), 1, 1))
				.toArray(new PDG[0]);

		final SortedMap<PDG, SortedMap<PDGNode<?>, String>> nodes = Collections
				.synchronizedSortedMap(new TreeMap<>());
		final SortedMap<PDG, SortedMap<PDGEdge, String>> edges = Collections
				.synchronizedSortedMap(new TreeMap<>());
		HashCalculation.calculate(pdgs, nodes, edges, 1);

		final List<PDGPairInfo> pairs = new ArrayList<>();
		for (int i = 0; i < pdgs.length; i++) {
			for (int j = i + 1; j < pdgs.length; j++) {
				pairs.add(new PDGPairInfo(pdgs[i], pdgs[j]));
			}
		}
		final SortedSet<ClonePairInfo> clonepairs = new TreeSet<>();
		CloneDetection.detect(pairs.toArray(new PDGPairInfo[0]), new PDG[0],
				nodes, edges, clonepairs, 2, 1);
		return clonepairs;
	}

	/** いずれかのペアの左側が、挙げたテキストで始まるノードを全て含むか。 */
	private static boolean anyPairContains(final SortedSet<ClonePairInfo> clonepairs,
			final String... prefixes) {
		for (final ClonePairInfo clonepair : clonepairs) {
			final List<String> texts = clonepair.getLeftNodes().stream()
					.map(node -> node.core.getText()).toList();
			boolean all = true;
			for (final String prefix : prefixes) {
				all &= texts.stream().anyMatch(text -> text.startsWith(prefix));
			}
			if (all) {
				return true;
			}
		}
		return false;
	}

	@Test
	void pairsIdenticalBranches() {
		assertTrue(anyPairContains(detect("straight", "same"),
				"log(", "warn("),
				"同じ構造の if は両方の枝ごと 1 つのペアになること");
	}

	@Test
	void doesNotPairSwappedBranches() {
		// then と else を入れ替えた if は、条件の真偽が合わないので、枝の中の
		// 文は条件から辿れない。以前は枝の向きを見ず、同型としていた。
		assertFalse(anyPairContains(detect("straight", "swapped"),
				"log(", "warn("),
				"枝を入れ替えた if を 1 つのペアにしないこと");
	}
}
