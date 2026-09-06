package yoshikihigo.tinypdg;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
	 * <p>結果はパス順に並べる。ディレクトリの列挙順はファイルシステム任せで、
	 * 同じ入力でも環境によって変わる。並べておかないと出力されるグラフの
	 * 番号付けが環境依存になってしまう。
	 *
	 * <p>シンボリックリンクとジャンクションは辿る。ただし祖先へ戻る循環は
	 * 警告して飛ばす。以前は File の再帰で辿っていて循環を知らず、パスの長さ
	 * が OS の上限を超えるまで降りてから落ちていた (issue #20)。
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
			Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
					Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {

						@Override
						public FileVisitResult visitFile(final Path path,
								final BasicFileAttributes attributes) {
							if (attributes.isRegularFile()
									&& path.getFileName().toString().endsWith(".java")) {
								files.add(path.toFile());
							}
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFileFailed(final Path path,
								final IOException e) throws IOException {
							if (e instanceof FileSystemLoopException) {
								System.err.println("警告: 循環するリンクを飛ばします: " + path);
								return FileVisitResult.CONTINUE;
							}
							throw e;
						}
					});
		} catch (final IOException e) {
			throw new TinyPDGException("ディレクトリを読めませんでした: " + file, e);
		}

		files.sort(Comparator.comparing(File::getAbsolutePath));
		return files;
	}
}
