package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StatementInfo extends ProgramElementInfo implements BlockInfo {

	static final private AtomicInteger ID_Generator = new AtomicInteger(0);

	final public int id;
	final public ProgramElementInfo ownerBlock;
	final public CATEGORY category;
	final public List<ExpressionInfo> expressions;

	final private List<ExpressionInfo> initializers;
	private ExpressionInfo condition;
	final private List<ExpressionInfo> updaters;

	final private List<StatementInfo> statements;

	public StatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine) {

		super(startLine, endLine);

		this.id = ID_Generator.getAndIncrement();
		this.ownerBlock = ownerBlock;
		this.category = category;
		this.expressions = new ArrayList<ExpressionInfo>();

		this.initializers = new ArrayList<ExpressionInfo>();
		this.condition = null;
		this.updaters = new ArrayList<ExpressionInfo>();

		this.statements = new ArrayList<StatementInfo>();
	}

	public enum CATEGORY {

		Assert("ASSERT"), Case("CASE"), Do("DO"), Expression("EXPRESSION"), If(
				"IF"), ForInitializer("FORINITIALIZER"), ForCondition(
				"FORCONDITION"), ForUpdater("FORUPDATER"), Foreach("FOREACH"), Return(
				"RETURN"), Synchronized("SYNCHRONIZED"), Switch("SWITCH"), Throw(
				"SWITCH"), VariableDeclaration("VARIABLEDECLARATION"), While(
				"WHILE");

		final public String id;

		CATEGORY(final String id) {
			this.id = id;
		}
	}

	public void addInitializer(final ExpressionInfo initializer) {
		assert null != initializer : "\"initializer\" is null.";
		this.initializers.add(initializer);
	}

	public void setCondition(final ExpressionInfo condition) {
		assert null != condition : "\"condition\" is null.";
		this.condition = condition;
	}

	public void addUpdater(final ExpressionInfo updater) {
		assert null != updater : "\"updater\" is null.";
		this.updaters.add(updater);
	}

	public List<ExpressionInfo> getInitializers() {
		return Collections.unmodifiableList(this.initializers);
	}

	public ExpressionInfo getCondition() {
		return this.condition;
	}

	public List<ExpressionInfo> getUpdaters() {
		return Collections.unmodifiableList(this.updaters);
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

	public void addExpression(final ExpressionInfo expression) {
		assert null != expression : "\"expression\" is null.";
		this.expressions.add(expression);
	}

	public List<ExpressionInfo> getExpressions() {
		return Collections.unmodifiableList(this.expressions);
	}
}
