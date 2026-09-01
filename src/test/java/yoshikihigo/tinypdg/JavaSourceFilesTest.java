package yoshikihigo.tinypdg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
	void rejectsAPathThatIsNeitherFileNorDirectory(@TempDir final Path root) {
		// 以前は assert false で弾こうとしていたが、表明は既定で無効なので
		// 実際には素通りし、解析対象 0 件の正常終了に見えていた。
		final File missing = root.resolve("no-such-path").toFile();
		assertThrows(TinyPDGException.class, () -> JavaSourceFiles.collect(missing));
	}
}
