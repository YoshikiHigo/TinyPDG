package yoshikihigo.tinypdg.prelement.data;

public class Frequency {

	public final float probablity;
	public final int support;
	public final String text;

	public Frequency(final float probablity, final int support,
			final String text) {
		this.probablity = probablity;
		this.support = support;
		this.text = text;
	}
}
