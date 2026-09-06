package yoshikihigo.tinypdg.prelement;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
							new InputStreamReader(System.in, consoleCharset()))) {
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
	 * 標準入力の文字コード。
	 *
	 * <p>コンソールがあればその文字コード、なければ stdin.encoding、それも
	 * なければ既定の文字コード。以前は既定の文字コード (UTF-8) で読んでいて、
	 * Windows のコンソール (ms932) から入れた日本語が化けていた (issue #28)。
	 */
	private static Charset consoleCharset() {
		if (null != System.console()) {
			return System.console().charset();
		}
		final String encoding = System.getProperty("stdin.encoding");
		if (null != encoding) {
			try {
				return Charset.forName(encoding);
			} catch (final IllegalArgumentException e) {
				// 知らない名前なら既定へ。
			}
		}
		return Charset.defaultCharset();
	}

	/**
	 * 3 種類の依存の頻度を、予測される要素 (正規化テキスト) ごとに 1 つに
	 * まとめ、支持度の合計が大きいものから並べて返す。
	 *
	 * <p>以前は種類ごとに 3 つのループがあり、それぞれが残りの種類の一覧から
	 * 同じハッシュを探して取り除いていた。同じ手順が 3 回、少しずつ短く
	 * なりながら並んでいた。
	 */
	public static List<CombinationalFrequency> getPredictedElements(
			final DAO dao, final String baseText) {

		// 出会った順を保つ。合計が同じ要素の並びは、制御依存にあるもの、
		// データ依存だけのもの、実行依存だけのもの、の順になる。
		final Map<String, EnumMap<PDGEdge.TYPE, Frequency>> byText = new LinkedHashMap<>();
		for (final PDGEdge.TYPE type : PDGEdge.TYPE.values()) {
			for (final Frequency frequency : dao.getFrequencies(type, baseText)) {
				byText.computeIfAbsent(frequency.text,
						t -> new EnumMap<>(PDGEdge.TYPE.class))
						.put(type, frequency);
			}
		}

		final List<CombinationalFrequency> frequencies = new ArrayList<>();
		for (final Entry<String, EnumMap<PDGEdge.TYPE, Frequency>> entry : byText
				.entrySet()) {
			final EnumMap<PDGEdge.TYPE, Frequency> ofText = entry.getValue();
			frequencies.add(new CombinationalFrequency(entry.getKey(),
					ofText.get(PDGEdge.TYPE.CONTROL),
					ofText.get(PDGEdge.TYPE.DATA),
					ofText.get(PDGEdge.TYPE.EXECUTION)));
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
