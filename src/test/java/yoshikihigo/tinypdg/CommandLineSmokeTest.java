package yoshikihigo.tinypdg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

/**
 * コマンドラインツールを実際に動かして、出力が出ることを確かめる。
 *
 * <p>ゴールデンテストは CFG と PDG を直接組み立てて比べるので、main から
 * 引数を解釈してファイルへ書き出すまでの経路を通らない。Scorpio に至っては
 * クローン検出そのものにテストがなく、このブランチの変更のたびに手で動かして
 * 前のコミットと出力を比べていた。それをここに移す。
 *
 * <p>細かい値は固定しない。ここで捕まえたいのは「何も出なくなった」
 * 「例外で落ちる」といった壊れ方である。
 *
 * <p>注意: これらの main は失敗すると {@code System.exit(1)} を呼ぶ。その
 * 場合はテスト JVM ごと落ちてビルドが失敗する。落ち方は綺麗ではないが、
 * 失敗が見逃されることはない。
 */
class CommandLineSmokeTest {

	private static final Path SAMPLES = Path.of(System.getProperty("user.dir"))
			.resolve("src/test/resources/samples");

	@Test
	void writerEmitsGraphvizForBothGraphs(@TempDir final Path work)
			throws IOException {

		final Path pdg = work.resolve("pdg.dot");
		final Path cfg = work.resolve("cfg.dot");

		yoshikihigo.tinypdg.graphviz.Writer.main(new String[] { "-d",
				SAMPLES.resolve("test009").toString(), "-p", pdg.toString(),
				"-c", cfg.toString() });

		assertAll(() -> assertWellFormedDot(pdg, "PDG"),
				() -> assertWellFormedDot(cfg, "CFG"));
	}

	private static void assertWellFormedDot(final Path file, final String kind)
			throws IOException {

		assertTrue(Files.exists(file), kind + " のファイルが作られること");
		final List<String> lines = Files.readAllLines(file,
				StandardCharsets.UTF_8);
		assertFalse(lines.isEmpty(), kind + " の中身が空でないこと");
		assertTrue(lines.get(0).startsWith("digraph"),
				kind + " が digraph で始まること: " + lines.get(0));

		int depth = 0;
		for (final String line : lines) {
			depth += count(line, '{') - count(line, '}');
		}
		assertEquals(0, depth, kind + " の括弧が閉じていること");
	}

	private static int count(final String line, final char c) {
		int n = 0;
		for (int i = 0; i < line.length(); i++) {
			if (c == line.charAt(i)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * 併合の有無で切り替わる経路が両方とも通ること。
	 *
	 * <p>-M を省くと併合は有効になる。off を明示しないと片方しか通らない。
	 */
	@ParameterizedTest(name = "-M {0}")
	@ValueSource(strings = { "off", "on" })
	void scorpioDetectsClonePairs(final String merging, @TempDir final Path work)
			throws IOException {

		final Path output = work.resolve("clonepairs.csv");

		final List<String> args = new ArrayList<>(
				List.of("-d", SAMPLES.toString(), "-o", output.toString(),
						"-s", "5", "-t", "2", "-M", merging));
		yoshikihigo.tinypdg.scorpio.Scorpio.main(args.toArray(new String[0]));

		assertTrue(Files.exists(output), "出力ファイルが作られること");
		final List<String> lines = Files.readAllLines(output,
				StandardCharsets.UTF_8);
		assertFalse(lines.isEmpty(), "クローンペアが 1 件は見つかること");

		boolean acrossFiles = false;
		for (final String line : lines) {
			final String[] fields = line.split("\t");
			assertEquals(8, fields.length,
					"1 行は 8 列であること: " + line);
			// 2 列目から 3 列目、5 列目から 6 列目が行番号。
			for (final int i : new int[] { 1, 2, 4, 5 }) {
				assertTrue(0 < Integer.parseInt(fields[i]),
						"行番号が正であること: " + fields[i]);
			}
			acrossFiles |= !fields[0].equals(fields[3]);
		}
		assertTrue(acrossFiles,
				"別々のファイルにまたがるクローンが見つかること");
	}
}
