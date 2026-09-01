package yoshikihigo.tinypdg.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import yoshikihigo.tinypdg.TinyPDGException;

class TinyPDGASTVisitorTest {

	@Test
	void reportsAnUnreadableSourceFileAsAnException(@TempDir final Path workDir) {

		final File missing = workDir.resolve("NoSuchFile.java").toFile();

		// 以前は IOException を握り潰して空文字列を解析していた。読み込みに
		// 失敗したファイルと、メソッドが 1 つもない正常なファイルとが
		// 区別できなかった。
		final TinyPDGException thrown = assertThrows(TinyPDGException.class,
				() -> JavaAstFactory.createAST(missing));
		assertNotNull(thrown.getCause(), "元の例外が原因として保持されていること");
	}

	@Test
	void defaultsToTheCurrentLtsJavaVersion() {
		assertEquals("25", JavaAstFactory.DEFAULT_JAVA_VERSION);
	}
}
