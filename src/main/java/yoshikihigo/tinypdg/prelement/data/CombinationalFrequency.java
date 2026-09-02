package yoshikihigo.tinypdg.prelement.data;

public class CombinationalFrequency {

	final public int hash;
	final public String text;
	final public Frequency control;
	final public Frequency data;
	final public Frequency execution;

	public CombinationalFrequency(final int hash, final String text,
			final Frequency control, final Frequency data,
			final Frequency execution) {

		this.hash = hash;
		this.text = text;

		this.control = null != control ? control : new Frequency(0f, 0, 0, "");
		this.data = null != data ? data : new Frequency(0f, 0, 0, "");
		this.execution = null != execution ? execution : new Frequency(0f, 0,
				0, "i");
	}

	public int getTotalSupport() {
		return this.control.support + this.data.support
				+ this.execution.support;
	}

	public float getTotalProbability() {
		return this.control.probablity + this.data.probablity
				+ this.execution.probablity;
	}
}
