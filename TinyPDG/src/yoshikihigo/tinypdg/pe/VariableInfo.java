package yoshikihigo.tinypdg.pe;

public class VariableInfo {

	final CATEGORY category;
	final TypeInfo type;
	final String name;

	public VariableInfo(final CATEGORY category, final TypeInfo type,
			final String name) {
		this.category = category;
		this.type = type;
		this.name = name;
	}

	enum CATEGORY {
		FIELD, LOCAL, PARAMETER;
	}
}
