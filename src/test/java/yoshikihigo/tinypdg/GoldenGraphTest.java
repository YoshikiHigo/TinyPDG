package yoshikihigo.tinypdg;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.CFG;
import yoshikihigo.tinypdg.cfg.edge.CFGEdge;
import yoshikihigo.tinypdg.cfg.node.CFGControlNode;
import yoshikihigo.tinypdg.cfg.node.CFGNode;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGControlNode;
import yoshikihigo.tinypdg.pdg.node.PDGMethodEnterNode;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pdg.node.PDGParameterNode;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;

/**
 * 既存の解析結果を「そのまま」固定するゴールデン（characterization）テスト。
 *
 * <p>各サンプルディレクトリの Java ソースから CFG と PDG を構築し、決定的な
 * テキスト表現に落として {@code src/test/resources/golden/*.txt} と比較する。
 * 近代化作業の前後で解析結果が変わっていないことを保証するのが目的であり、
 * ゴールデンの内容が「正しい」ことを主張するものではない。
 *
 * <p>ゴールデンの再生成:
 * <pre>./gradlew test -Dtinypdg.golden=update</pre>
 * 再生成後は必ず {@code git diff} で差分を確認すること。
 */
class GoldenGraphTest {

	private static final Path PROJECT_DIR = Path.of(System.getProperty("user.dir"));
	private static final Path SAMPLES_DIR = PROJECT_DIR.resolve("src/test/resources/samples");
	private static final Path GOLDEN_DIR = PROJECT_DIR.resolve("src/test/resources/golden");
	private static final boolean UPDATE = "update".equals(System.getProperty("tinypdg.golden"));

	static Stream<Path> samples() throws IOException {
		try (Stream<Path> children = Files.list(SAMPLES_DIR)) {
			return children.filter(Files::isDirectory).sorted().toList().stream();
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("samples")
	void graphsMatchGolden(final Path sampleDir) throws Exception {

		final String actual = dump(sampleDir);
		final Path golden = GOLDEN_DIR.resolve(sampleDir.getFileName() + ".txt");

		if (UPDATE) {
			Files.createDirectories(GOLDEN_DIR);
			Files.writeString(golden, actual, UTF_8);
			return;
		}

		if (!Files.exists(golden)) {
			throw new AssertionError("ゴールデンファイルがありません: " + golden
					+ System.lineSeparator()
					+ "  ./gradlew test -Dtinypdg.golden=update で生成してください。");
		}

		assertEquals(Files.readString(golden, UTF_8), actual,
				"解析結果がゴールデンと一致しません: " + sampleDir.getFileName());
	}

	// ------------------------------------------------------------------
	// 決定的なダンプ
	// ------------------------------------------------------------------

	private static String dump(final Path sampleDir) throws IOException {

		final List<File> sources;
		try (Stream<Path> walk = Files.walk(sampleDir)) {
			sources = walk.filter(p -> p.toString().endsWith(".java")).sorted()
					.map(Path::toFile).toList();
		}

		final List<MethodInfo> methods = new ArrayList<>();
		for (final File source : sources) {
			final CompilationUnit unit = TinyPDGASTVisitor.createAST(source);
			unit.accept(new TinyPDGASTVisitor(source.getAbsolutePath(), unit, methods));
		}

		// 走査順に依存しないよう、メソッドを安定な順序に並べ替える。
		methods.sort(Comparator.comparing((MethodInfo m) -> m.name)
				.thenComparingInt(m -> m.startLine)
				.thenComparingInt(m -> m.endLine));

		final StringBuilder out = new StringBuilder();
		out.append("# sample: ").append(sampleDir.getFileName()).append('\n');
		for (final MethodInfo method : methods) {
			appendCFG(out, method);
			appendPDG(out, method);
		}
		return out.toString();
	}

	private static void appendCFG(final StringBuilder out, final MethodInfo method) {

		final CFG cfg = new CFG(method, new CFGNodeFactory());
		cfg.build();
		cfg.removeSwitchCases();
		cfg.removeJumpStatements();

		out.append("\n## CFG ").append(signature(method)).append('\n');

		final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes = cfg.getAllNodes();
		final Map<CFGNode<? extends ProgramElementInfo>, Integer> ids = new LinkedHashMap<>();
		for (final CFGNode<? extends ProgramElementInfo> node : nodes) {
			ids.put(node, ids.size());
		}

		final CFGNode<? extends ProgramElementInfo> enter = cfg.getEnterNode();
		final SortedSet<CFGNode<? extends ProgramElementInfo>> exits = cfg.getExitNodes();

		out.append("nodes:\n");
		for (final Map.Entry<CFGNode<? extends ProgramElementInfo>, Integer> e : ids.entrySet()) {
			final CFGNode<? extends ProgramElementInfo> node = e.getKey();
			final List<String> kinds = new ArrayList<>();
			if (node.equals(enter)) {
				kinds.add("enter");
			}
			if (exits.contains(node)) {
				kinds.add("exit");
			}
			kinds.add(node instanceof CFGControlNode ? "control" : "normal");
			out.append("  ").append(e.getValue()).append(": [")
					.append(String.join(",", kinds)).append("] ")
					.append(oneLine(node.getText())).append('\n');
		}

		final SortedSet<CFGEdge> edges = new TreeSet<>();
		for (final CFGNode<? extends ProgramElementInfo> node : nodes) {
			edges.addAll(node.getForwardEdges());
			edges.addAll(node.getBackwardEdges());
		}

		out.append("edges:\n");
		for (final CFGEdge edge : edges) {
			out.append("  ").append(ids.get(edge.fromNode)).append(" -> ")
					.append(ids.get(edge.toNode)).append(" [")
					.append(edge.getDependenceString()).append("]\n");
		}
	}

	private static void appendPDG(final StringBuilder out, final MethodInfo method) {

		final PDG pdg = new PDG(method, new PDGNodeFactory(), new CFGNodeFactory(),
				true, true, true);
		pdg.build();

		out.append("\n## PDG ").append(signature(method)).append('\n');

		final SortedSet<PDGNode<?>> nodes = pdg.getAllNodes();
		final Map<PDGNode<?>, Integer> ids = new LinkedHashMap<>();
		for (final PDGNode<?> node : nodes) {
			ids.put(node, ids.size());
		}

		final SortedSet<PDGNode<?>> exits = pdg.getExitNodes();

		out.append("nodes:\n");
		for (final Map.Entry<PDGNode<?>, Integer> e : ids.entrySet()) {
			final PDGNode<?> node = e.getKey();
			final List<String> kinds = new ArrayList<>();
			if (node instanceof PDGMethodEnterNode) {
				kinds.add("enter");
			}
			if (exits.contains(node)) {
				kinds.add("exit");
			}
			if (node instanceof PDGParameterNode) {
				kinds.add("parameter");
			}
			if (node instanceof PDGControlNode) {
				kinds.add("control");
			}
			if (kinds.isEmpty()) {
				kinds.add("normal");
			}
			out.append("  ").append(e.getValue()).append(": [")
					.append(String.join(",", kinds)).append("] ")
					.append(oneLine(node.getText())).append('\n');
		}

		out.append("edges:\n");
		for (final PDGEdge edge : pdg.getAllEdges()) {
			out.append("  ").append(ids.get(edge.fromNode)).append(" -> ")
					.append(ids.get(edge.toNode)).append(" [").append(edge.type)
					.append(": ").append(edge.getDependenceString()).append("]\n");
		}
	}

	private static String signature(final MethodInfo method) {
		return method.name + " <" + method.startLine + "..." + method.endLine + ">";
	}

	private static String oneLine(final String text) {
		return text == null ? "<null>"
				: text.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
						.replace("\t", "\\t");
	}
}
