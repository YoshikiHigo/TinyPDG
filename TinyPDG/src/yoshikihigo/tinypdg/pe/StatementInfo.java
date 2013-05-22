package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class StatementInfo extends ProgramElementInfo implements BlockInfo,
		VariableAssignmentAndReference {

	private ProgramElementInfo ownerBlock;
	private CATEGORY category;
	private List<ExpressionInfo> expressions;

	final private List<ExpressionInfo> initializers;
	private ExpressionInfo condition;
	final private List<ExpressionInfo> updaters;

	final private List<StatementInfo> statements;
	private List<StatementInfo> elseStatements;
	final private List<StatementInfo> catchStatements;
	private StatementInfo finallyStatement;

	private String label;

	public StatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine) {

		super(startLine, endLine);

		this.ownerBlock = ownerBlock;
		this.category = category;
		this.expressions = new ArrayList<ExpressionInfo>();

		this.initializers = new ArrayList<ExpressionInfo>();
		this.condition = null;
		this.updaters = new ArrayList<ExpressionInfo>();

		this.statements = new ArrayList<StatementInfo>();
		this.elseStatements = new ArrayList<StatementInfo>();
		this.catchStatements = new ArrayList<StatementInfo>();
		this.finallyStatement = null;

		this.label = null;
	}

	public enum CATEGORY {

		Assert("ASSERT"), Break("BREAK"), Case("CASE"), Catch("CATCH"), Continue(
				"CONTINUE"), Do("DO"), Expression("EXPRESSION"), If("IF"), For(
				"FOR"), Foreach("FOREACH"), Return("RETURN"), SimpleBlock(
				"SimpleBlock"), Synchronized("SYNCHRONIZED"), Switch("SWITCH"), Throw(
				"SWITCH"), Try("TRY"), VariableDeclaration(
				"VARIABLEDECLARATION"), While("WHILE");

		final public String id;

		CATEGORY(final String id) {
			this.id = id;
		}
	}

	public ProgramElementInfo getOwnerBlock() {
		return this.ownerBlock;
	}

	public void setOwnerBlock(final ProgramElementInfo ownerBlock) {
		assert null != "\"ownerBlock\" is null.";
		this.ownerBlock = ownerBlock;
	}

	public CATEGORY getCategory() {
		return this.category;
	}

	public void setCategory(final CATEGORY category) {
		assert null != "\"category\" is null.";
		this.category = category;
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
	public void setStatement(final StatementInfo statement) {
		assert null != statement : "\"statement\" is null.";
		this.statements.clear();
		if (StatementInfo.CATEGORY.SimpleBlock == statement.getCategory()) {
			this.statements.addAll(statement.getStatements());
		} else {
			this.statements.add(statement);
		}
	}

	@Override
	public void addStatement(final StatementInfo statement) {
		assert null != statement : "\"statement\" is null.";
		this.statements.add(statement);
	}

	@Override
	public void addStatements(final Collection<StatementInfo> statements) {
		assert null != statements : "\"statements\" is null.";
		this.statements.addAll(statements);
	}

	@Override
	public List<StatementInfo> getStatements() {
		return Collections.unmodifiableList(this.statements);
	}

	public void setElseStatement(final StatementInfo elseBody) {
		assert null != elseBody : "\"elseStatement\" is null.";
		this.elseStatements.clear();
		if (StatementInfo.CATEGORY.SimpleBlock == elseBody.getCategory()) {
			this.elseStatements.addAll(elseBody.getStatements());
		} else {
			this.elseStatements.add(elseBody);
		}
	}

	public List<StatementInfo> getElseStatement() {
		return Collections.unmodifiableList(this.elseStatements);
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

	@Override
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

		for (final StatementInfo statement : this.elseStatements) {
			variables.addAll(statement.getAssignedVariables());
		}

		for (final StatementInfo catchStatement : this.catchStatements) {
			variables.addAll(catchStatement.getAssignedVariables());
		}

		if (null != this.finallyStatement) {
			variables.addAll(this.finallyStatement.getAssignedVariables());
		}

		return variables;
	}

	@Override
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

		for (final StatementInfo statement : this.elseStatements) {
			variables.addAll(statement.getReferencedVariables());
		}

		for (final StatementInfo catchStatement : this.catchStatements) {
			variables.addAll(catchStatement.getReferencedVariables());
		}

		if (null != this.finallyStatement) {
			variables.addAll(this.finallyStatement.getReferencedVariables());
		}

		return variables;
	}

	public String getLabel() {
		return this.label;
	}

	public void setLabel(final String label) {
		this.label = label;
	}

	public String getJumpToLabel() {
		if (0 == this.expressions.size()) {
			return null;
		} else {
			return this.expressions.get(0).getText();
		}
	}
}
