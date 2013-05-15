package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
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
	private StatementInfo elseStatement;
	final private List<StatementInfo> catchStatements;
	private StatementInfo finallyStatement;

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
		this.elseStatement = null;
		this.catchStatements = new ArrayList<StatementInfo>();
		this.finallyStatement = null;
	}

	public enum CATEGORY {

		Assert("ASSERT"), Case("CASE"), Catch("CATCH"), Do("DO"), Else("ELSE"), Expression(
				"EXPRESSION"), Finally("FINALLY"), If("IF"), For("FOR"), Foreach(
				"FOREACH"), Return("RETURN"), Synchronized("SYNCHRONIZED"), Switch(
				"SWITCH"), Throw("SWITCH"), Try("TRY"), VariableDeclaration(
				"VARIABLEDECLARATION"), While("WHILE");

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

	public void setElseStatement(final StatementInfo elseStatement) {
		assert null != elseStatement : "\"elseStatement\" is null.";
		this.elseStatement = elseStatement;
	}

	public StatementInfo getElseStatement() {
		return this.elseStatement;
	}

	public void addCatchStatement(final StatementInfo catchStatement) {
		assert null != catchStatement : "\"catchStatement\" is null.";
		this.catchStatements.add(catchStatement);
	}

	public List<StatementInfo> getCatchStatements() {
		return Collections.unmodifiableList(this.catchStatements);
	}

	public void setFinallyStatement(final StatementInfo finallyStatement) {
		assert null != finallyStatement : "\"finallyStatement\" is null.";
		this.finallyStatement = finallyStatement;
	}

	public StatementInfo getFinallyStatement() {
		return this.finallyStatement;
	}

	public void addExpression(final ExpressionInfo expression) {
		assert null != expression : "\"expression\" is null.";
		this.expressions.add(expression);
	}

	public List<ExpressionInfo> getExpressions() {
		return Collections.unmodifiableList(this.expressions);
	}

	public SortedSet<String> getAssignedVariables() {

		final SortedSet<String> variables = new TreeSet<String>();

		for (final ExpressionInfo expression : this.expressions) {
			variables.addAll(expression.getAssignedVariables());
		}

		for (final ExpressionInfo initializer : this.initializers) {
			variables.addAll(initializer.getAssignedVariables());
		}

		if (null != this.condition) {
			variables.addAll(this.condition.getAssignedVariables());
		}

		for (final ExpressionInfo updater : this.updaters) {
			variables.addAll(updater.getAssignedVariables());
		}

		for (final StatementInfo statement : this.statements) {
			variables.addAll(statement.getAssignedVariables());
		}

		if (null != this.elseStatement) {
			variables.addAll(this.elseStatement.getAssignedVariables());
		}

		for (final StatementInfo catchStatement : this.catchStatements) {
			variables.addAll(catchStatement.getAssignedVariables());
		}

		if (null != this.finallyStatement) {
			variables.addAll(this.finallyStatement.getAssignedVariables());
		}

		return variables;
	}

	public SortedSet<String> getReferencedVariables() {

		final SortedSet<String> variables = new TreeSet<String>();

		for (final ExpressionInfo expression : this.expressions) {
			variables.addAll(expression.getReferencedVariables());
		}

		for (final ExpressionInfo initializer : this.initializers) {
			variables.addAll(initializer.getReferencedVariables());
		}

		if (null != this.condition) {
			variables.addAll(this.condition.getReferencedVariables());
		}

		for (final ExpressionInfo updater : this.updaters) {
			variables.addAll(updater.getReferencedVariables());
		}

		for (final StatementInfo statement : this.statements) {
			variables.addAll(statement.getReferencedVariables());
		}

		if (null != this.elseStatement) {
			variables.addAll(this.elseStatement.getReferencedVariables());
		}

		for (final StatementInfo catchStatement : this.catchStatements) {
			variables.addAll(catchStatement.getReferencedVariables());
		}

		if (null != this.finallyStatement) {
			variables.addAll(this.finallyStatement.getReferencedVariables());
		}

		return variables;
	}
}
