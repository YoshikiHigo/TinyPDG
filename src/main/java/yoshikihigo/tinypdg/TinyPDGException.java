package yoshikihigo.tinypdg;

/**
 * TinyPDG の処理中に、その場では回復できない失敗が起きたことを表す。
 *
 * <p>これらの箇所は以前、スタックトレースを出して {@code System.exit(0)} を
 * 呼んでいた。二重に問題がある。TinyPDG はライブラリなので、組み込んだ
 * プログラムの JVM ごと落としてしまう。しかも終了コードが 0 なので、
 * 呼び出し元やシェルからは正常終了と区別が付かない。
 *
 * <p>失敗は例外として伝え、どう扱うかは呼び出し元に委ねる。コマンドライン
 * ツールはそれぞれの main で受け取り、非 0 で終了する。
 */
public class TinyPDGException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TinyPDGException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public TinyPDGException(final String message) {
		super(message);
	}
}
