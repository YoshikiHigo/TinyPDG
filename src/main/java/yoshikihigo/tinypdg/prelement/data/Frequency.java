package yoshikihigo.tinypdg.prelement.data;

public class Frequency {

	public final float probablity;
	public final int support;
	public final int hash;
	public final String text;

	public Frequency(final float probablity, final int support, final int hash,
			final String text) {
		this.probablity = probablity;
		this.support = support;
		this.hash = hash;
		this.text = text;
	}
}
