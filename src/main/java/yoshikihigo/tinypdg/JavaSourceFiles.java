package yoshikihigo.tinypdg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

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
	 * <p>結果はパス順に並べる。ディレクトリの列挙順はファイルシステム任せで、
	 * 同じ入力でも環境によって変わる。並べておかないと出力されるグラフの
	 * 番号付けが環境依存になってしまう。
	 *
	 * <p>シンボリックリンクとジャンクションは辿るが、同じ実体は 1 回しか
	 * 集めない。見たディレクトリとファイルの実体のパス (toRealPath) を覚えて
	 * おき、別の名前で 2 度目に届いたら飛ばす。祖先へ戻る循環はこれで止まり、
	 * 循環しない別名 (alias と real が同じディレクトリを指す) で同じファイルを
	 * 2 度解析することもない。以前は File の再帰で辿っていて循環を知らず、
	 * パスの長さが OS の上限を超えるまで降りてから落ちていた (issue #20)。
	 * その後 walkFileTree の FileSystemLoopException に頼ったが、それは
	 * 祖先へ戻る循環しか報せず、別名は 2 度集めていた。
	 *
	 * <p>同じ実体に複数の名前で届くときは、名前順に辿って最初に届いた名前で
	 * 報告する。ディレクトリの中身は名前順に見るので、どの名前になるかは
	 * ファイルシステムの列挙順に依らない。
	 *
	 * @param file 対象のファイルまたはディレクトリ
	 * @return 見つかった .java ファイル。パス順
	 * @throws TinyPDGException 対象がファイルでもディレクトリでもない場合、
	 *                          またはディレクトリを読めなかった場合
	 */
	public static List<File> collect(final File file) {

		Objects.requireNonNull(file, "\"file\" is null.");

		final Path start = file.toPath();
		if (!Files.isRegularFile(start) && !Files.isDirectory(start)) {
			// 存在しないか、通常のファイルでもディレクトリでもない。
			// 黙って無視すると「解析対象 0 件で正常終了」に見えてしまう。
			throw new TinyPDGException(
					"ファイルでもディレクトリでもありません: " + file);
		}

		final List<File> files = new ArrayList<>();
		try {
			collect(start, new HashSet<>(), files);
		} catch (final IOException e) {
			throw new TinyPDGException("ディレクトリを読めませんでした: " + file, e);
		}

		files.sort(Comparator.comparing(File::getAbsolutePath));
		return files;
	}

	/**
	 * path とその下を集める。visited には見た実体のパスが溜まる。
	 */
	private static void collect(final Path path, final Set<Path> visited,
			final List<File> files) throws IOException {

		final Path real;
		try {
			real = path.toRealPath();
		} catch (final IOException e) {
			// 壊れたリンクなど、実体を辿れないパス。1 つのために全体を止めない。
			System.err.println("警告: 実体を辿れないので飛ばします: " + path
					+ " (" + e + ")");
			return;
		}

		if (!visited.add(real)) {
			if (Files.isDirectory(path)) {
				System.err.println("警告: 既に見たディレクトリへのリンクを飛ばします: "
						+ path);
			}
			return;
		}

		if (Files.isRegularFile(path)) {
			if (path.getFileName().toString().endsWith(".java")) {
				files.add(path.toFile());
			}
			return;
		}

		if (!Files.isDirectory(path)) {
			// 名前付きパイプなど。ソースファイルではあり得ない。
			return;
		}

		final List<Path> children;
		try (Stream<Path> stream = Files.list(path)) {
			children = stream.sorted().toList();
		}
		for (final Path child : children) {
			collect(child, visited, files);
		}
	}
}
