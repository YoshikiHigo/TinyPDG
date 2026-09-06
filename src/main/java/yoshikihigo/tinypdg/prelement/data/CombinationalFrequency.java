package yoshikihigo.tinypdg.prelement.data;

/** 1 つの要素についての、3 種類の依存の頻度。 */
public class CombinationalFrequency {

	final public String text;
	final public Frequency control;
	final public Frequency data;
	final public Frequency execution;

	public CombinationalFrequency(final String text, final Frequency control,
			final Frequency data, final Frequency execution) {
		this.text = text;
		this.control = null != control ? control : new Frequency(0f, 0, text);
		this.data = null != data ? data : new Frequency(0f, 0, text);
		this.execution = null != execution ? execution
				: new Frequency(0f, 0, text);
	}

	public int getTotalSupport() {
		return this.control.support + this.data.support
				+ this.execution.support;
	}

	public float getTotalProbability() {
		return this.control.probability + this.data.probability
				+ this.execution.probability;
	}
}
