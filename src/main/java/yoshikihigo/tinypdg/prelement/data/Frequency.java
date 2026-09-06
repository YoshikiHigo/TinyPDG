package yoshikihigo.tinypdg.prelement.data;

/**
 * ある要素からある要素へ、ある種類の依存がどれだけあったか。
 *
 * <p>text は依存の先の要素の正規化テキストで、これが要素の鍵でもある。
 * 以前は正規化テキストの int のハッシュも持っていて、そちらが鍵だった。
 * 別のテキストが同じハッシュになると 1 つの要素として数えられていた
 * (issue #24)。
 */
public class Frequency {

	public final float probability;
	public final int support;
	public final String text;

	public Frequency(final float probability, final int support,
			final String text) {
		this.probability = probability;
		this.support = support;
		this.text = text;
	}
}
