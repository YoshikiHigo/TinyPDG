package yoshikihigo.tinypdg.scorpio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
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

	/** サンプルの 1 メソッドについて、PDG の各ノードの正規化テキストを返す。 */
	private static List<String> normalizedTexts(final String sample,
			final String methodName) {

		final List<MethodInfo> methods = JavaAstFactory.collectMethods(
				SAMPLES.resolve(sample).toFile(),
				JavaAstFactory.DEFAULT_JAVA_VERSION);
		final MethodInfo method = methods.stream()
				.filter(m -> methodName.equals(m.name)).findFirst()
				.orElseThrow(() -> new AssertionError(
						sample + " に " + methodName + " が見つからない"));

		final PDG pdg = new PDG(method);
		pdg.build();

		final List<String> texts = new ArrayList<>();
		for (final PDGNode<?> node : pdg.getAllNodes()) {
			texts.add(NormalizedText.normalize(node.core));
		}
		return texts;
	}

	private static void assertContains(final List<String> texts,
			final String expected) {
		assertTrue(texts.contains(expected),
				() -> expected + " が見つからない: " + texts);
	}

	@Test
	void keepsTheParenthesesOfASuperMethodInvocation() {
		// 引数がないとき、末尾のカンマを消すつもりで "(" を消していた。
		// メソッド名は、通常のメソッド呼び出しと同じく字面のまま残る。
		assertContains(normalizedTexts("lang13_super", "count"),
				"return super.size();");
		assertContains(normalizedTexts("lang13_super", "append"),
				"return super.add($1 + $2);");
	}

	@Test
	void writesAnArrayCreationWithItsInitializer() {
		// 型の名前に [] が含まれているのに、さらに [] を足していた。
		assertContains(normalizedTexts("lang14_arraycreation", "withInitializer"),
				"int[] $1 = new int[]{$2,$3};");
		// 空の初期化子では、消すカンマがないのに "{" を消していた。
		assertContains(normalizedTexts("lang14_arraycreation", "emptyInitializer"),
				"int[] $1 = new int[]{};");
	}

	@Test
	void writesTheDimensionExpressionsOfAnArrayCreation() {
		// 次元式を訪問していなかったので、new int[n] が new int[] になっていた。
		assertContains(normalizedTexts("lang14_arraycreation", "withDimension"),
				"int[] $1 = new int[$2];");
		// 次元式は型の次元より少ないことがある。残りは空のまま書く。
		assertContains(normalizedTexts("lang14_arraycreation", "partialDimensions"),
				"int[][] $1 = new int[$2][];");
		assertContains(normalizedTexts("lang14_arraycreation", "twoDimensions"),
				"String[][] $1 = new String[$2][$3];");
	}

	@Test
	void writesEveryFragmentOfAVariableDeclarationExpression() {
		// for の初期化式 int i = 0, j = n。以前は最初の断片しか書いていなかった。
		assertContains(normalizedTexts("lang16_forinit", "sumPairs"),
				"int $1 = $2,$3 = $4");
	}
}
