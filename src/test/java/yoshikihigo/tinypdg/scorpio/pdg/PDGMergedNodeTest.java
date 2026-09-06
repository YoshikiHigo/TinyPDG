package yoshikihigo.tinypdg.scorpio.pdg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import yoshikihigo.tinypdg.ast.JavaAstFactory;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGExecutionDependenceEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;

class PDGMergedNodeTest {

	private static final File SAMPLE = Path.of(System.getProperty("user.dir"))
			.resolve("src/test/resources/samples/lang51_merge").toFile();

	private static PDG merged(final String methodName) {
		final List<MethodInfo> methods = JavaAstFactory.collectMethods(SAMPLE,
				JavaAstFactory.DEFAULT_JAVA_VERSION);
		final MethodInfo method = methods.stream()
				.filter(m -> methodName.equals(m.name)).findFirst()
				.orElseThrow(() -> new AssertionError(
						"サンプルに " + methodName + " が見つからない"));
		final PDG pdg = new PDG(method, new PDGNodeFactory(),
				new CFGNodeFactory());
		pdg.build();
		PDGMergedNode.mergeNodes(pdg);
		return pdg;
	}

	@Test
	void leavesNoSelfLoopWhenMergingTwoStatements() {
		// i++; i++; の 2 文の間にはデータ依存の辺がある。併合すると、以前は
		// それが併合ノードの自己ループになっていた。
		final PDG pdg = merged("twice");
		final long selfLoops = pdg.getAllEdges().stream()
				.filter(e -> e.fromNode == e.toNode).count();
		assertEquals(0, selfLoops, "併合したノードに自己ループが残らないこと");
		assertTrue(pdg.getAllNodes().stream()
				.anyMatch(n -> n instanceof PDGMergedNode), "2 文が併合されること");
	}

	@Test
	void keepsTheSelfEdgeOfALoopBody() {
		// for (;;) { tick(); } の本体は自分自身へ戻る実行依存の辺を持つ。
		// 以前はそれを自分自身と併合して辺を落としていた。
		final PDG pdg = merged("spin");
		final boolean loops = pdg.getAllEdges().stream()
				.anyMatch(e -> e instanceof PDGExecutionDependenceEdge
						&& e.fromNode == e.toNode);
		assertTrue(loops, "ループの本体の自己辺が残ること");
		assertTrue(pdg.getAllNodes().stream()
				.noneMatch(n -> n instanceof PDGMergedNode),
				"1 文だけのループは併合されないこと");
		assertEquals(0, pdg.getAllEdges().stream()
				.filter(e -> !(e instanceof PDGExecutionDependenceEdge)
						&& e.fromNode == e.toNode
						&& PDGEdge.TYPE.DATA == e.type).count());
	}
}
