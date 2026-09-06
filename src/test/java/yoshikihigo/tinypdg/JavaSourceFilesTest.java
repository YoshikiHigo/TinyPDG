package yoshikihigo.tinypdg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceFilesTest {

	@Test
	void collectsJavaFilesRecursivelyInPathOrder(@TempDir final Path root) throws Exception {

		Files.createDirectories(root.resolve("b/deep"));
		Files.createDirectories(root.resolve("a"));
		Files.writeString(root.resolve("b/deep/Zeta.java"), "class Zeta {}");
		Files.writeString(root.resolve("a/Alpha.java"), "class Alpha {}");
		Files.writeString(root.resolve("a/notes.txt"), "無視される");
		Files.writeString(root.resolve("Beta.java"), "class Beta {}");

		final List<File> found = JavaSourceFiles.collect(root.toFile());

		assertEquals(List.of("Beta.java", "Alpha.java", "Zeta.java").size(), found.size(),
				".java だけが集まること");
		// listFiles() の順序は環境依存なので、並び順が固定であることを確かめる。
		assertEquals(List.of(root.resolve("Beta.java").toFile(),
				root.resolve("a/Alpha.java").toFile(),
				root.resolve("b/deep/Zeta.java").toFile()), found,
				"パス順に並ぶこと");
	}

	@Test
	void acceptsASingleFile(@TempDir final Path root) throws Exception {
		final Path source = root.resolve("One.java");
		Files.writeString(source, "class One {}");
		assertEquals(List.of(source.toFile()), JavaSourceFiles.collect(source.toFile()));
	}

	@Test
	void skipsALinkThatClosesACycle(@TempDir final Path root) throws Exception {
		final Path sub = Files.createDirectories(root.resolve("sub"));
		Files.writeString(sub.resolve("A.java"), "class A {}");
		final Path back = sub.resolve("back");
		linkDirectory(back, root);

		try {
			final List<File> files = JavaSourceFiles.collect(root.toFile());
			assertEquals(List.of(sub.resolve("A.java").toFile()), files,
					"循環で落ちず、ファイルは 1 回だけ集めること");
		} finally {
			Files.delete(back);
		}
	}

	@Test
	void collectsAnAliasedDirectoryOnce(@TempDir final Path root) throws Exception {
		// alias と real は同じディレクトリである。循環はしないので walkFileTree の
		// FileSystemLoopException では止まらず、A.java が 2 度集まっていた。
		final Path real = Files.createDirectories(root.resolve("real"));
		Files.writeString(real.resolve("A.java"), "class A {}");
		final Path alias = root.resolve("alias");
		linkDirectory(alias, real);

		try {
			final List<File> files = JavaSourceFiles.collect(root.toFile());
			// 名前順に辿るので、先に届く alias の名で 1 回だけ報告する。
			assertEquals(List.of(alias.resolve("A.java").toFile()), files,
					"別名を通っても同じファイルは 1 回だけ集めること");
		} finally {
			Files.delete(alias);
		}
	}

	@Test
	void collectsAJavaFileWhoseAliasHasAnotherExtension(@TempDir final Path root) throws Exception {
		// A.txt -> Z.java は名前順で Z.java より先に届く。以前はここで実体を
		// 「見た」ことにし、名前のせいで集めもせず、Z.java を重複として飛ばして
		// いたので、Java ソースが 1 つも集まらなかった。
		final Path source = root.resolve("Z.java");
		Files.writeString(source, "class Z {}");
		final Path alias = root.resolve("A.txt");
		linkFile(alias, source);

		try {
			assertEquals(List.of(source.toFile()), JavaSourceFiles.collect(root.toFile()),
					"拡張子の違う別名が Java ソースを隠さないこと");
		} finally {
			Files.delete(alias);
		}
	}

	@Test
	void rejectsAPathThatIsNeitherFileNorDirectory(@TempDir final Path root) {
		// 以前は assert false で弾こうとしていたが、表明は既定で無効なので
		// 実際には素通りし、解析対象 0 件の正常終了に見えていた。
		final File missing = root.resolve("no-such-path").toFile();
		assertThrows(TinyPDGException.class, () -> JavaSourceFiles.collect(missing));
	}

	/**
	 * link から target のディレクトリへのリンクを作る。シンボリックリンクを
	 * 作れない環境 (権限のない Windows) ではジャンクションを試し、それも
	 * 駄目ならテストを飛ばす。
	 */
	private static void linkDirectory(final Path link, final Path target) throws Exception {
		try {
			Files.createSymbolicLink(link, target);
			return;
		} catch (final IOException | UnsupportedOperationException e) {
			// 下でジャンクションを試す。
		}
		if (System.getProperty("os.name", "").startsWith("Windows")) {
			final Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J",
					link.toString(), target.toString()).redirectErrorStream(true).start();
			mklink.getInputStream().readAllBytes();
			if (0 == mklink.waitFor() && Files.isDirectory(link)) {
				return;
			}
		}
		Assumptions.abort("neither symbolic links nor junctions are available");
	}

	/**
	 * link から target のファイルへのシンボリックリンクを作る。ジャンクションは
	 * ディレクトリにしか使えないので、リンクを作れない環境ではテストを飛ばす。
	 */
	private static void linkFile(final Path link, final Path target) {
		try {
			Files.createSymbolicLink(link, target);
		} catch (final IOException | UnsupportedOperationException e) {
			Assumptions.abort("symbolic links are not available: " + e);
		}
	}
}
