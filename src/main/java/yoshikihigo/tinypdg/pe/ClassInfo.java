package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ClassInfo extends ProgramElementInfo {

	final public String path;
	final public String name;
	final private List<MethodInfo> methods;

	public ClassInfo(final String path, final String name, final int startLine,
			final int endLine) {
		super(startLine, endLine);
		Objects.requireNonNull(path, "\"path\" is null");
		this.path = path;
		this.name = name;
		this.methods = new ArrayList<>();
	}

	public boolean isAnonymous() {
		return null == this.name;
	}

	public void addMethod(final MethodInfo method) {
		Objects.requireNonNull(method, "\"method\" is null.");
		this.methods.add(method);
	}

	public List<MethodInfo> getMethods() {
		final List<MethodInfo> methods = new ArrayList<>();
		methods.addAll(this.methods);
		return methods;
	}
}
