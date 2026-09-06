package yoshikihigo.tinypdg;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import yoshikihigo.tinypdg.ast.JavaAstFactory;
import yoshikihigo.tinypdg.pdg.PDG;

/**
 * コマンドラインツールが共有する、オプションの定義と読み取り、経過時間の表示。
 *
 * <p>Writer、Scorpio、DependenceDistiller は解析対象、Java のバージョン、
 * スレッド数、最小サイズのオプションを同じ文言で 3 回定義し、解析対象が
 * 存在するかの検査と Java バージョンの既定値の解決も 3 回書いていた。
 * 経過時間を "1 hour 2 minutes 3 seconds." の形にする 35 行は Scorpio と
 * DependenceDistiller に丸ごと 2 部あった。
 */
public final class CommandLineTools {

	private CommandLineTools() {
	}

	/** {@code -d}: 解析対象のファイルまたはディレクトリ。必須。 */
	public static Option targetOption() {
		final Option option = new Option("d", "directory", true,
				"target directory");
		option.setArgName("directory");
		option.setArgs(1);
		option.setRequired(true);
		return option;
	}

	/** {@code -j}: 解析対象として仮定する Java のバージョン。 */
	public static Option javaVersionOption() {
		final Option option = new Option("j", "java-version", true,
				"Java version assumed for the target source files");
		option.setArgName("version");
		option.setArgs(1);
		option.setRequired(false);
		return option;
	}

	/** {@code -t}: スレッド数。 */
	public static Option threadsOption() {
		final Option option = new Option("t", "thread", true,
				"number of threads");
		option.setArgName("thread");
		option.setArgs(1);
		option.setRequired(false);
		return option;
	}

	/** {@code -s}: 扱うグラフの最小ノード数。 */
	public static Option sizeOption(final boolean required) {
		final Option option = new Option("s", "size", true, "size");
		option.setArgName("size");
		option.setArgs(1);
		option.setRequired(required);
		return option;
	}

	/** {@code -b}: SQLite のデータベース。必須。 */
	public static Option databaseOption() {
		final Option option = new Option("b", "database", true, "database");
		option.setArgName("database");
		option.setArgs(1);
		option.setRequired(true);
		return option;
	}

	/** {@code -S}: 制御依存を後支配ではなく文の入れ子で決める。 */
	public static Option structuralOption() {
		final Option option = new Option("S", "structural", false,
				"decide control dependence by syntactic nesting instead of post-dominance");
		option.setRequired(false);
		return option;
	}

	/** {@code -S} が指定されていれば、制御依存を文の入れ子で決める設定にする。 */
	public static PDG.Dependences withControlOption(final CommandLine cmd,
			final PDG.Dependences dependences) {
		return cmd.hasOption("S") ? dependences.withStructuralControl()
				: dependences;
	}

	/** on か off を取るオプション。省略時は on。 */
	public static Option onOffOption(final String letter, final String name,
			final String description) {
		final Option option = new Option(letter, name, true, description);
		option.setArgName("on or off");
		option.setArgs(1);
		option.setRequired(false);
		return option;
	}

	/**
	 * {@code -d} の値。
	 *
	 * @throws TinyPDGException 指定されたパスが存在しない場合
	 */
	public static File target(final CommandLine cmd) {
		final File target = new File(cmd.getOptionValue("d"));
		if (!target.exists()) {
			throw new TinyPDGException(
					"指定されたファイルまたはディレクトリがありません: " + target);
		}
		return target;
	}

	/** {@code -j} の値。省略時は {@link JavaAstFactory#DEFAULT_JAVA_VERSION}。 */
	public static String javaVersion(final CommandLine cmd) {
		return cmd.hasOption("j") ? cmd.getOptionValue("j")
				: JavaAstFactory.DEFAULT_JAVA_VERSION;
	}

	/** {@code -t} の値。省略時は 1。 */
	public static int threads(final CommandLine cmd) {
		return cmd.hasOption("t") ? Integer.parseInt(cmd.getOptionValue("t"))
				: 1;
	}

	/**
	 * {@code -s} の値。省略時は defaultValue。
	 *
	 * @throws TinyPDGException 整数でない値や 1 未満の値が指定された場合。
	 *                          以前は CloneDetection の表明だけが見ていて、
	 *                          表明は既定で無効なので 0 や負の値が素通りしていた
	 */
	public static int size(final CommandLine cmd, final int defaultValue) {
		if (!cmd.hasOption("s")) {
			return defaultValue;
		}
		final String value = cmd.getOptionValue("s");
		final int size;
		try {
			size = Integer.parseInt(value);
		} catch (final NumberFormatException e) {
			throw new TinyPDGException("-s には整数を指定してください: " + value, e);
		}
		if (size < 1) {
			throw new TinyPDGException("-s には 1 以上の値を指定してください: " + value);
		}
		return size;
	}

	/**
	 * on / off オプションの値。省略時は on。
	 *
	 * <p>以前は on でも off でもない値を警告するだけで、off として続けて
	 * いた。
	 *
	 * @throws TinyPDGException on でも off でもない値が与えられた場合
	 */
	public static boolean onOff(final CommandLine cmd, final String letter) {
		if (!cmd.hasOption(letter)) {
			return true;
		}
		final String value = cmd.getOptionValue(letter);
		return switch (value) {
		case "on" -> true;
		case "off" -> false;
		default -> throw new TinyPDGException("option of \"-" + letter
				+ "\" must be \"on\" or \"off\".");
		};
	}

	/** ナノ秒で測った経過時間を "1 hour 2 minutes 3 seconds." の形にする。 */
	public static String formatElapsed(final long nanos) {

		final long sec = nanos / 1_000_000_000L;
		final long hour = sec / 3600;
		final long minute = (sec % 3600) / 60;
		final long second = (sec % 3600) % 60;

		final StringBuilder text = new StringBuilder();

		if (1 == hour) {
			text.append(hour).append(" hour ");
		} else if (1 < hour) {
			text.append(hour).append(" hours ");
		}

		if (1 == minute) {
			text.append(minute).append(" minute ");
		} else if (1 < minute) {
			text.append(minute).append(" minutes ");
		} else if (0 == minute && 1 <= hour) {
			text.append(" 0 minute ");
		}

		text.append(second).append(2 <= second ? " seconds." : " second.");
		return text.toString();
	}
}
