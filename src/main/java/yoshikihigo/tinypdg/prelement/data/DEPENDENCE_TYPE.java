package yoshikihigo.tinypdg.prelement.data;

public enum DEPENDENCE_TYPE {

	CONTROL("control"), DATA("data"), EXECUTION("execution");

	final public String text;

	DEPENDENCE_TYPE(final String text) {
		this.text = text;
	}
}
