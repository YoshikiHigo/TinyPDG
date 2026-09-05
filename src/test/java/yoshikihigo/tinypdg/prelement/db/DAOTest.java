package yoshikihigo.tinypdg.prelement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import yoshikihigo.tinypdg.TinyPDGException;
import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;
import yoshikihigo.tinypdg.prelement.data.Frequency;

/**
 * sqlite-jdbc の疎通確認。
 *
 * <p>DAO は解析本体とは独立しているためゴールデンテストの対象外であり、
 * ドライバのバージョンを上げた際に「テーブルが作れて、書けて、読み戻せる」
 * ことを機械的に確かめる場がなかった。ここでそれを埋める。
 */
class DAOTest {

	@Test
	void writesAndReadsBackFrequencies(@TempDir final Path workDir) throws Exception {

		final Path database = workDir.resolve("test.db");

		// close() がバッチを flush するので、読み戻す前に必ず閉じる。
		try (final DAO dao = new DAO(database.toString(), true)) {
			dao.addToTexts(100, "int a = 10;");
			dao.addToTexts(200, "int b = 20;");
			dao.addToFrequencies(DEPENDENCE_TYPE.DATA, 100,
					new Frequency(0.75f, 3, 200, "int b = 20;"));
		}

		assertTrue(Files.exists(database), "データベースファイルが作成されていること");

		try (final DAO reader = new DAO(database.toString(), false)) {
			final List<Frequency> found =
					reader.getFrequencies(DEPENDENCE_TYPE.DATA, 100);

			assertEquals(1, found.size(), "登録した依存関係が 1 件読み戻せること");

			final Frequency frequency = found.get(0);
			assertEquals(200, frequency.hash);
			assertEquals(3, frequency.support);
			assertEquals(0.75f, frequency.probablity, 0.0001f);
			assertEquals("int b = 20;", frequency.text,
					"texts テーブルとの結合が効いていること");

			assertTrue(reader.getFrequencies(DEPENDENCE_TYPE.CONTROL, 100).isEmpty(),
					"型が異なる依存関係は返らないこと");
		}
	}

	@Test
	void reportsAnUnopenableDatabaseAsAnException(@TempDir final Path workDir) {

		final Path missing = workDir.resolve("no-such-directory").resolve("x.db");

		// 以前はスタックトレースを出して System.exit(0) を呼んでいた。
		// ライブラリが呼び出し元の JVM を落とすうえ、終了コードが 0 なので
		// 失敗したことすら呼び出し元に伝わらなかった。
		final TinyPDGException thrown = assertThrows(TinyPDGException.class,
				() -> new DAO(missing.toString(), true));
		assertNotNull(thrown.getCause(), "元の例外が原因として保持されていること");
	}
}
