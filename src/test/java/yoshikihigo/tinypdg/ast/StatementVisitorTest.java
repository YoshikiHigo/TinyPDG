package yoshikihigo.tinypdg.ast;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.TryStatementInfo;

/**
 * 文の visit が組み立てるテキストを確かめる。
 *
 * <p>ゴールデンテストは CFG と PDG のノードのテキストしか見ない。do 文や
 * catch 節は中身が展開されて条件式だけがノードになるので、文そのものの
 * テキストはゴールデンに現れない。組み立てたテキストを setText し忘れて
 * いても、本体の代わりに自分自身の空のテキストを付けていても、気づけなかった。
 */
class StatementVisitorTest {

	private static final Path SAMPLES = Path.of(System.getProperty("user.dir"))
			.resolve("src/test/resources/samples");

	private static List<StatementInfo> statementsOf(final String sample,
			final String methodName) {
		final List<MethodInfo> methods = JavaAstFactory.collectMethods(
				SAMPLES.resolve(sample).toFile(),
				JavaAstFactory.DEFAULT_JAVA_VERSION);
		return methods.stream().filter(m -> methodName.equals(m.name))
				.findFirst()
				.orElseThrow(() -> new AssertionError(
						sample + " に " + methodName + " が見つからない"))
				.getStatements();
	}

	private static StatementInfo first(final List<StatementInfo> statements,
			final StatementInfo.CATEGORY category) {
		return statements.stream()
				.filter(s -> category == s.getCategory()).findFirst()
				.orElseThrow(() -> new AssertionError(
						category + " の文が見つからない: " + statements));
	}

	@Test
	void aDoStatementHasItsText() {
		// テキストを組み立てておきながら setText を呼んでいなかった。
		final StatementInfo doStatement = first(
				statementsOf("test009", "method"), StatementInfo.CATEGORY.Do);
		final String text = doStatement.getText();
		assertTrue(text.startsWith("do {"), text);
		assertTrue(text.endsWith("} while (true);"), text);
	}

	@Test
	void aCatchClauseCarriesItsBody() {
		// 本体の代わりに、まだ空だった自分自身のテキストを付けていた。
		final TryStatementInfo tryStatement = (TryStatementInfo) first(
				statementsOf("lang04_trywithresources", "count"),
				StatementInfo.CATEGORY.Try);
		final String text = tryStatement.getCatchStatements().get(0).getText();
		assertTrue(text.startsWith("catch ("), text);
		assertTrue(text.contains("lines = -1;"), text);
		assertTrue(text.endsWith("}"), text);
	}
}
