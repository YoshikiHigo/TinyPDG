package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodInfo extends ProgramElementInfo implements BlockInfo {

	static final private AtomicInteger ID_Generator = new AtomicInteger(0);

	final public int id;
	final public String path;
	final public String name;
	final private List<VariableInfo> parameters;
	final private List<StatementInfo> statements;

	public MethodInfo(final String path, final String name,
			final int startLine, final int endLine) {

		super(startLine, endLine);

		this.id = ID_Generator.getAndIncrement();
		this.path = path;
		this.name = name;
		this.parameters = new ArrayList<VariableInfo>();
		this.statements = new ArrayList<StatementInfo>();
	}

	public void addParameter(final VariableInfo parameter) {
		assert null != parameter : "\"variable\" is null.";
		this.parameters.add(parameter);
	}

	@Override
	public void addStatement(final StatementInfo statement) {
		assert null != statement : "\"statement\" is null.";
		this.statements.add(statement);
	}

	@Override
	public List<StatementInfo> getStatements() {
		return Collections.unmodifiableList(this.statements);
	}
}
