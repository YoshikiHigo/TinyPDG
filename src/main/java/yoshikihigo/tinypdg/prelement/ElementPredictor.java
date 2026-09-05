package yoshikihigo.tinypdg.prelement;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import yoshikihigo.tinypdg.CommandLineTools;
import yoshikihigo.tinypdg.prelement.data.CombinationalFrequency;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.prelement.data.Frequency;
import yoshikihigo.tinypdg.prelement.db.DAO;

public class ElementPredictor {

	public static void main(final String[] args) {

		try {

			final Options options = new Options();

			options.addOption(CommandLineTools.databaseOption());

			final CommandLineParser parser = new DefaultParser();
			final CommandLine cmd = parser.parse(options, args);

			final String database = cmd.getOptionValue("b");

			try (final DAO dao = new DAO(database, false);
					final BufferedReader in = new BufferedReader(
							new InputStreamReader(System.in))) {
				while (true) {
					System.out.println("input an element for prediction");
					System.out.print("> ");
					final String line = in.readLine();

					// 空行か、入力の終わりで終える。以前は入力の終わり (null) を
					// 見ておらず、NullPointerException で落ちていた。
					if (null == line || line.isEmpty()) {
						System.out.println("done.");
						// これは正常終了。main から戻れば終了コードは 0 になる。
						return;
					}

					final List<CombinationalFrequency> frequencies = getPredictedElements(
							dao, line);
					printCombinationalFrequencies(frequencies);
				}
			}

		} catch (final Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * 3 種類の依存の頻度を、予測される要素 (ハッシュ) ごとに 1 つにまとめ、
	 * 支持度の合計が大きいものから並べて返す。
	 *
	 * <p>以前は種類ごとに 3 つのループがあり、それぞれが残りの種類の一覧から
	 * 同じハッシュを探して取り除いていた。同じ手順が 3 回、少しずつ短く
	 * なりながら並んでいた。
	 */
	public static List<CombinationalFrequency> getPredictedElements(
			final DAO dao, final String baseText) {

		final int fromhash = baseText.hashCode();

		// 出会った順を保つ。合計が同じ要素の並びは、制御依存にあるもの、
		// データ依存だけのもの、実行依存だけのもの、の順になる。
		final Map<Integer, EnumMap<PDGEdge.TYPE, Frequency>> byHash = new LinkedHashMap<>();
		for (final PDGEdge.TYPE type : PDGEdge.TYPE.values()) {
			for (final Frequency frequency : dao.getFrequencies(type, fromhash)) {
				byHash.computeIfAbsent(frequency.hash,
						h -> new EnumMap<>(PDGEdge.TYPE.class))
						.put(type, frequency);
			}
		}

		final List<CombinationalFrequency> frequencies = new ArrayList<>();
		for (final EnumMap<PDGEdge.TYPE, Frequency> ofHash : byHash.values()) {
			// EnumMap は定数の順に並ぶので、先頭は最初に見つかった種類のもの。
			final Frequency first = ofHash.values().iterator().next();
			frequencies.add(new CombinationalFrequency(first.hash, first.text,
					ofHash.get(PDGEdge.TYPE.CONTROL),
					ofHash.get(PDGEdge.TYPE.DATA),
					ofHash.get(PDGEdge.TYPE.EXECUTION)));
		}

		// 支持度の合計が大きいものから。
		frequencies.sort(Comparator
				.comparingInt(CombinationalFrequency::getTotalSupport)
				.reversed());

		return frequencies;
	}

	public static void printCombinationalFrequencies(
			final List<CombinationalFrequency> frequencies) {

		for (final CombinationalFrequency frequency : frequencies) {
			System.out.print("support: ");
			System.out.print(frequency.getTotalSupport());
			System.out.print(" (control: ");
			System.out.print(frequency.control.support);
			System.out.print(", data: ");
			System.out.print(frequency.data.support);
			System.out.print(", execution: ");
			System.out.print(frequency.execution.support);
			System.out.print("), probability: ");
			System.out.print(frequency.getTotalProbability());
			System.out.print(" (control: ");
			System.out.print(frequency.control.probability);
			System.out.print(", data: ");
			System.out.print(frequency.data.probability);
			System.out.print(", execution: ");
			System.out.print(frequency.execution.probability);
			System.out.print("), predicted element: ");
			System.out.println(frequency.text);
		}
	}
}
