package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodInfo {

	static final private AtomicInteger ID_Generator = new AtomicInteger(0);

	final public int id;
	final public String path;
	final public String name;
	final private List<VariableInfo> parameters;
	final public int startLine;
	final public int endLine;

	public MethodInfo(final String path, final String name,
			final int startLine, final int endLine) {
		this.id = ID_Generator.getAndIncrement();
		this.path = path;
		this.name = name;
		this.startLine = startLine;
		this.endLine = endLine;
		this.parameters = new ArrayList<VariableInfo>();
	}

	public void addParameter(final VariableInfo parameter) {
		assert null != parameter : "\"variable\" is null.";
		this.parameters.add(parameter);
	}
}
