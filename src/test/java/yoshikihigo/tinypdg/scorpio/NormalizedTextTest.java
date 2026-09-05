package yoshikihigo.tinypdg.scorpio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import yoshikihigo.tinypdg.JavaSourceFiles;
import yoshikihigo.tinypdg.ast.JavaAstFactory;
import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;

/**
 * NormalizedText が、解析器が作りうる全ての要素を扱えることを確かめる。
 *
 * <p>NormalizedText はカテゴリごとの巨大な switch で書かれており、
 * default 節は表明で塞がれているだけだった。表明は既定で無効なので、
 * カテゴリを追加しても何も起きず、正規化テキストから中身が黙って
 * 抜け落ちる。実際、対象言語を Java 25 へ上げたときに追加した 7 つの
 * カテゴリが、どれもここで漏れていた。
 *
 * <p>ゴールデンテストは CFG と PDG しか見ないので、この経路には届かない。
 */
class NormalizedTextTest {

	private static final Path SAMPLES = Path.of(System.getProperty("user.dir"))
			.resolve("src/test/resources/samples");

	static Stream<Path> modernSamples() {
		// ラムダ・パターン・switch 式・テキストブロックを含むもの。
		return Stream.of("lang07_lambda", "lang08_patterns", "lang10_switchexpr",
				"lang01_enum", "lang04_trywithresources", "lang11_assert")
				.map(SAMPLES::resolve);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("modernSamples")
	void normalizesEveryNodeOfEveryGraph(final Path sampleDir) {

		final List<MethodInfo> methods = new ArrayList<>();
		for (final File source : JavaSourceFiles.collect(sampleDir.toFile())) {
			final CompilationUnit unit = JavaAstFactory.createAST(source);
			unit.accept(new TinyPDGASTVisitor(source.getAbsolutePath(), unit, methods));
		}

		for (final MethodInfo method : methods) {
			final PDG pdg = new PDG(method, new PDGNodeFactory(),
					new CFGNodeFactory());
			pdg.build();
			for (final PDGNode<?> node : pdg.getAllNodes()) {
				final String normalized = assertDoesNotThrow(
						() -> new NormalizedText(node.core).getText(),
						() -> "正規化できない要素: " + node.core.getClass().getSimpleName()
								+ " / " + node.getText());
				assertNotNull(normalized);
			}
		}
	}
}
