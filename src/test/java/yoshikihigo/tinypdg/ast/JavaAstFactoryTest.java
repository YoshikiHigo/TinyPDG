package yoshikihigo.tinypdg.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import yoshikihigo.tinypdg.pe.MethodInfo;

class JavaAstFactoryTest {

	private static final File BROKEN = Path.of(System.getProperty("user.dir"))
			.resolve("src/test/resources/broken").toFile();

	@Test
	void skipsFilesWithSyntaxErrorsAndWarns() {

		final PrintStream original = System.err;
		final ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));

		final List<MethodInfo> methods;
		try {
			methods = JavaAstFactory.collectMethods(BROKEN,
					JavaAstFactory.DEFAULT_JAVA_VERSION);
		} finally {
			System.setErr(original);
		}

		assertEquals(List.of("ok"), methods.stream().map(m -> m.name).toList(),
				"構文エラーのあるファイルのメソッドは集めないこと");

		final String warning = captured.toString(StandardCharsets.UTF_8);
		assertTrue(warning.contains("Broken.java"),
				"飛ばしたファイルを報告すること: " + warning);
		assertTrue(warning.contains("1 個のファイル"),
				"飛ばした数を報告すること: " + warning);
	}
}
