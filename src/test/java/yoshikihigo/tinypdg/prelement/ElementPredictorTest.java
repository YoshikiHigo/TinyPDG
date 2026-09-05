package yoshikihigo.tinypdg.prelement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import yoshikihigo.tinypdg.prelement.data.CombinationalFrequency;
import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;
import yoshikihigo.tinypdg.prelement.data.Frequency;
import yoshikihigo.tinypdg.prelement.db.DAO;

/**
 * 予測結果が支持度の合計の降順に並ぶことを確かめる。
 *
 * <p>以前はソートが、実行依存の頻度を足すループの内側にあった。1 件足す
 * たびに並べ替えるので、最後に足した 1 件は並べ替えの後に入って末尾に
 * 残り、実行依存の頻度が 1 件もなければ並べ替え自体が起きなかった。
 */
class ElementPredictorTest {

	@Test
	void ordersPredictionsByTotalSupport(@TempDir final Path workDir) {

		final String database = workDir.resolve("test.db").toString();
		final String base = "int a = 10;";
		final int from = base.hashCode();

		final DAO writer = new DAO(database, true);
		try {
			// hash 10 は制御 2 とデータ 5 で合計 7、hash 20 はデータ 1、
			// hash 30 と 40 は実行依存だけで 3 と 9。
			writer.addToFrequencies(DEPENDENCE_TYPE.CONTROL, from,
					new Frequency(0.5f, 2, 10, "a"));
			writer.addToFrequencies(DEPENDENCE_TYPE.DATA, from,
					new Frequency(0.5f, 5, 10, "a"));
			writer.addToFrequencies(DEPENDENCE_TYPE.DATA, from,
					new Frequency(0.5f, 1, 20, "b"));
			writer.addToFrequencies(DEPENDENCE_TYPE.EXECUTION, from,
					new Frequency(0.5f, 3, 30, "c"));
			writer.addToFrequencies(DEPENDENCE_TYPE.EXECUTION, from,
					new Frequency(0.5f, 9, 40, "d"));
		} finally {
			writer.close();
		}

		final DAO reader = new DAO(database, false);
		try {
			final List<CombinationalFrequency> predicted = ElementPredictor
					.getPredictedElements(reader, base);

			assertEquals(List.of(9, 7, 3, 1),
					predicted.stream()
							.map(CombinationalFrequency::getTotalSupport)
							.toList(),
					"支持度の合計の降順に並ぶこと");
			assertEquals(List.of(40, 10, 30, 20),
					predicted.stream().map(f -> f.hash).toList());
		} finally {
			reader.close();
		}
	}
}
