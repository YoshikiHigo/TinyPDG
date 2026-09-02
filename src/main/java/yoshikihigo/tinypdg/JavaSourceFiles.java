package yoshikihigo.tinypdg;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 解析対象の Java ソースファイルを集める。
 *
 * <p>この処理はコマンドラインツール 3 つがそれぞれ private メソッドとして
 * 持っていた。3 つは同じもののはずだったが実際には食い違っていて、
 * ファイルでもディレクトリでもないパスを、2 つは {@code assert false} で
 * 弾こうとし（表明は既定で無効なので実際には素通りする）、1 つは無視して
 * いた。重複を 1 箇所にまとめて、その扱いも決める。
 */
public final class JavaSourceFiles {

	private JavaSourceFiles() {
	}

	/**
	 * ファイルまたはディレクトリから、Java ソースファイルを再帰的に集める。
	 *
	 * <p>結果はパス順に並べる。{@link File#listFiles()} が返す順序は
	 * ファイルシステム任せで、同じ入力でも環境によって変わる。並べておかないと
	 * 出力されるグラフの番号付けが環境依存になってしまう。
	 *
	 * @param file 対象のファイルまたはディレクトリ
	 * @return 見つかった .java ファイル。パス順
	 * @throws TinyPDGException 対象がファイルでもディレクトリでもない場合、
	 *                          またはディレクトリを読めなかった場合
	 */
	public static List<File> collect(final File file) {

		Objects.requireNonNull(file, "\"file\" is null.");

		final List<File> files = new ArrayList<>();
		collectInto(file, files);
		files.sort(Comparator.comparing(File::getAbsolutePath));
		return files;
	}

	private static void collectInto(final File file, final List<File> files) {

		if (file.isFile()) {
			if (file.getName().endsWith(".java")) {
				files.add(file);
			}
			return;
		}

		if (file.isDirectory()) {
			final File[] children = file.listFiles();
			if (null == children) {
				throw new TinyPDGException(
						"ディレクトリを読めませんでした: " + file);
			}
			for (final File child : children) {
				collectInto(child, files);
			}
			return;
		}

		// 存在しないか、通常のファイルでもディレクトリでもない。
		// 黙って無視すると「解析対象 0 件で正常終了」に見えてしまう。
		throw new TinyPDGException(
				"ファイルでもディレクトリでもありません: " + file);
	}
}
