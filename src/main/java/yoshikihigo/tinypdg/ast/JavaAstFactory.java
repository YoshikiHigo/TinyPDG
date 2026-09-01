package yoshikihigo.tinypdg.ast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import yoshikihigo.tinypdg.TinyPDGException;

/**
 * ソースファイルから JDT の AST を組み立てる。
 *
 * <p>読み込みとパーサの設定は、木を辿ることとは別の仕事である。以前は
 * {@link TinyPDGASTVisitor} の静的メソッドとして同居していた。
 */
public final class JavaAstFactory {

	private JavaAstFactory() {
	}

	/**
	 * 解析対象として既定で仮定する Java のバージョン。
	 *
	 * <p>現時点の LTS である Java 25。呼び出し側は createAST の第 3 引数で
	 * これ以外のバージョンを指定できる。
	 */
	public static final String DEFAULT_JAVA_VERSION = JavaCore.VERSION_25;

	/**
	 * ソースファイルを UTF-8 の Java {@value #DEFAULT_JAVA_VERSION} として
	 * 読み込み、AST を構築する。
	 */
	public static CompilationUnit createAST(final File file) {
		return createAST(file, StandardCharsets.UTF_8, DEFAULT_JAVA_VERSION);
	}

	/**
	 * ソースファイルを Java {@value #DEFAULT_JAVA_VERSION} として読み込み、
	 * AST を構築する。
	 */
	public static CompilationUnit createAST(final File file, final Charset charset) {
		return createAST(file, charset, DEFAULT_JAVA_VERSION);
	}

	/**
	 * ソースファイルを指定した文字コードで読み込み、AST を構築する。
	 *
	 * <p>以前はここで "JISAutoDetect" を指定していたが、この文字コードは
	 * ISO-2022-JP / Shift_JIS / EUC-JP を判別するためのものであり、UTF-8 の
	 * ソースを正しく読めない。Java 18 以降は UTF-8 が既定の文字コードでもある
	 * ため既定を UTF-8 に改め、他の文字コードが必要な場合は呼び出し側が
	 * 指定できるようにした。
	 *
	 * @param javaVersion 解析対象として仮定する Java のバージョン
	 *                    ("8", "11", "17", "21", "25" など)
	 */
	public static CompilationUnit createAST(final File file, final Charset charset,
			final String javaVersion) {

		final String lineSeparator = System.lineSeparator();
		final StringBuilder text = new StringBuilder();

		try (final BufferedReader reader = Files.newBufferedReader(file.toPath(),
				charset)) {
			String line;
			while (null != (line = reader.readLine())) {
				text.append(line);
				text.append(lineSeparator);
			}
		} catch (final IOException e) {
			// 握り潰すと、空のソースを解析した結果と区別が付かない。
			// 「メソッドが 1 つもない正常なファイル」に見えてしまう。
			throw new TinyPDGException(
					"ソースファイルを読み込めませんでした: " + file, e);
		}

		// AST の API レベルと、解析対象の言語レベルは別物である。
		// 前者はどの種類のノードを表現できるかを決めるだけなので、JDT が
		// 対応する最新に固定しておけばよい。実際にどの構文を受理するかは
		// コンパイラオプションの側で決まる。
		final ASTParser parser = ASTParser.newParser(AST.getJLSLatest());

		final Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(javaVersion, options);
		parser.setCompilerOptions(options);

		parser.setSource(text.toString().toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
