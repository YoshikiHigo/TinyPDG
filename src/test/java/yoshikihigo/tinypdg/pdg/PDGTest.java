package yoshikihigo.tinypdg.pdg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import yoshikihigo.tinypdg.ast.JavaAstFactory;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.edge.PDGDataDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;

class PDGTest {

	private static final File SAMPLE = Path.of(System.getProperty("user.dir"))
			.resolve("src/test/resources/samples/lang09_receiver").toFile();

	private static PDG build(final String methodName,
			final PDG.Dependences dependences) {

		final List<MethodInfo> methods = JavaAstFactory.collectMethods(SAMPLE,
				JavaAstFactory.DEFAULT_JAVA_VERSION);
		final MethodInfo method = methods.stream()
				.filter(m -> methodName.equals(m.name)).findFirst()
				.orElseThrow(() -> new AssertionError(
						"サンプルに " + methodName + " が見つからない"));

		final PDG pdg = new PDG(method, new PDGNodeFactory(),
				new CFGNodeFactory(), dependences);
		pdg.build();
		return pdg;
	}

	private static long dataEdges(final PDG pdg) {
		return pdg.getAllEdges().stream()
				.filter(e -> e instanceof PDGDataDependenceEdge).count();
	}

	@Test
	void buildsEverythingByDefault() {
		final PDG pdg = build("reassignedReceiver", PDG.Dependences.ALL);
		assertTrue(0 < dataEdges(pdg), "データ依存が作られること");
		assertTrue(pdg.getAllEdges().stream()
				.anyMatch(e -> PDGEdge.TYPE.CONTROL == e.type),
				"制御依存が作られること");
	}

	@Test
	void skipsTheKindsThatAreTurnedOff() {
		final PDG pdg = build("reassignedReceiver",
				new PDG.Dependences(true, false, true));
		assertEquals(0, dataEdges(pdg),
				"データ依存を作らないと指定したら作らないこと");
		assertTrue(pdg.getAllEdges().stream()
				.anyMatch(e -> PDGEdge.TYPE.CONTROL == e.type),
				"ほかの依存は作られること");
	}

	@Test
	void findsTheGraphEvenWithoutControlDependences() {
		// 入口ノードに繋がるのは制御依存の辺だけなので、制御依存を切ると
		// 入口はどこにも繋がらない。以前は入口から辿ってノードを集めていた
		// ため、データ依存の辺が作られていてもグラフが空に見えていた。
		final PDG pdg = build("reassignedReceiver",
				new PDG.Dependences(false, true, false));

		assertEquals(0, pdg.getAllEdges().stream()
				.filter(e -> PDGEdge.TYPE.CONTROL == e.type).count(),
				"制御依存は作られないこと");
		assertTrue(0 < dataEdges(pdg),
				"それでもデータ依存は見えること");
		assertTrue(1 < pdg.getAllNodes().size(),
				"入口だけのグラフになっていないこと");
	}

	@Test
	void dropsDependencesThatReachTooFar() {
		// 距離は |行の差| + 1 で測る。reassignedReceiver では、パラメータから
		// 最初の使用までが 2、n の再代入をまたぐ依存はそれより離れている。
		final long all = dataEdges(build("reassignedReceiver",
				PDG.Dependences.ALL));
		final long near = dataEdges(build("reassignedReceiver",
				new PDG.Dependences(true, true, true, 2, Integer.MAX_VALUE)));

		assertTrue(near < all,
				"距離を絞ると遠いデータ依存が落ちること: all=" + all + " near=" + near);
	}

	@Test
	void rejectsANonPositiveDistance() {
		assertThrows(IllegalArgumentException.class,
				() -> new PDG.Dependences(true, true, true, 0,
						Integer.MAX_VALUE));
	}
}
