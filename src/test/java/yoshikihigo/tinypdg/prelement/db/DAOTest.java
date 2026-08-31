package yoshikihigo.tinypdg.prelement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

		final DAO dao = new DAO(database.toString(), true);
		try {
			dao.addToTexts(100, "int a = 10;");
			dao.addToTexts(200, "int b = 20;");
			dao.addToFrequencies(DEPENDENCE_TYPE.DATA, 100,
					new Frequency(0.75f, 3, 200, "int b = 20;"));
		} finally {
			// close() がバッチを flush するので、読み戻す前に必ず閉じる。
			dao.close();
		}

		assertTrue(Files.exists(database), "データベースファイルが作成されていること");

		final DAO reader = new DAO(database.toString(), false);
		try {
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
		} finally {
			reader.close();
		}
	}
}
