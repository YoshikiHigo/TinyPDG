package yoshikihigo.tinypdg.prelement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import yoshikihigo.tinypdg.TinyPDGException;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
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
			dao.addToFrequencies(PDGEdge.TYPE.DATA, "int a = 10;",
					new Frequency(0.75f, 3, "int b = 20;"));
		}

		assertTrue(Files.exists(database), "データベースファイルが作成されていること");

		try (final DAO reader = new DAO(database.toString(), false)) {
			final List<Frequency> found =
					reader.getFrequencies(PDGEdge.TYPE.DATA, "int a = 10;");

			assertEquals(1, found.size(), "登録した依存関係が 1 件読み戻せること");

			final Frequency frequency = found.get(0);
			assertEquals(3, frequency.support);
			assertEquals(0.75f, frequency.probability, 0.0001f);
			assertEquals("int b = 20;", frequency.text);

			assertTrue(reader.getFrequencies(PDGEdge.TYPE.CONTROL, "int a = 10;").isEmpty(),
					"型が異なる依存関係は返らないこと");
			assertTrue(reader.getFrequencies(PDGEdge.TYPE.DATA, "int c = 30;").isEmpty(),
					"別の始点の依存関係は返らないこと");
		}
	}

	@Test
	void keepsTextsApartEvenWhenTheirHashesCollide(@TempDir final Path workDir) throws Exception {

		// "Aa" と "BB" は String.hashCode が同じ。以前はハッシュが鍵だったので、
		// 別の文が 1 つの要素として合算されていた。
		final Path database = workDir.resolve("collision.db");
		assertEquals("Aa($1);".hashCode(), "BB($1);".hashCode());

		try (final DAO dao = new DAO(database.toString(), true)) {
			dao.addToFrequencies(PDGEdge.TYPE.DATA, "int $1 = $2;",
					new Frequency(0.5f, 1, "Aa($1);"));
			dao.addToFrequencies(PDGEdge.TYPE.DATA, "int $1 = $2;",
					new Frequency(0.5f, 2, "BB($1);"));
		}

		try (final DAO reader = new DAO(database.toString(), false)) {
			assertEquals(List.of("Aa($1);", "BB($1);"),
					reader.getFrequencies(PDGEdge.TYPE.DATA, "int $1 = $2;")
							.stream().map(f -> f.text).sorted().toList());
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

	@Test
	void refusesToReadAMissingDatabaseWithoutCreatingIt(@TempDir final Path workDir) {
		final Path missing = workDir.resolve("typo.db");
		assertThrows(TinyPDGException.class,
				() -> new DAO(missing.toString(), false));
		assertFalse(Files.exists(missing), "読むだけのつもりでファイルを作らないこと");
	}

	@Test
	void startsFromEmptyTablesOnCreation(@TempDir final Path workDir) throws Exception {
		final Path database = workDir.resolve("twice.db");
		for (int round = 0; round < 2; round++) {
			try (final DAO dao = new DAO(database.toString(), true)) {
				dao.addToFrequencies(PDGEdge.TYPE.DATA, "a",
						new Frequency(0.5f, 3, "b"));
			}
		}
		try (final DAO reader = new DAO(database.toString(), false)) {
			assertEquals(1, reader.getFrequencies(PDGEdge.TYPE.DATA, "a").size(),
					"2 回書いても行が重複しないこと");
		}
	}
}
