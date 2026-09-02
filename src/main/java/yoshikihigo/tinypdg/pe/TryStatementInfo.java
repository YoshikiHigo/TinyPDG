package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/** try 文。catch 節と finally 節を持つ。 */
public final class TryStatementInfo extends BlockStatementInfo {

	static final Set<CATEGORY> CATEGORIES = EnumSet.of(CATEGORY.Try);

	final private List<StatementInfo> catchStatements;
	private StatementInfo finallyStatement;

	public TryStatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine) {
		super(ownerBlock, category, startLine, endLine, CATEGORIES);
		this.catchStatements = new ArrayList<>();
		this.finallyStatement = null;
	}

	public void addCatchStatement(final StatementInfo catchStatement) {
		Objects.requireNonNull(catchStatement, "\"catchStatement\" is null.");
		this.catchStatements.add(catchStatement);
	}

	public List<StatementInfo> getCatchStatements() {
		return Collections.unmodifiableList(this.catchStatements);
	}

	public void setFinallyStatement(final StatementInfo finallyStatement) {
		Objects.requireNonNull(finallyStatement, "\"finallyStatement\" is null.");
		this.finallyStatement = finallyStatement;
	}

	/** @return finally 節。持たない try もあるので null がありうる */
	public StatementInfo getFinallyStatement() {
		return this.finallyStatement;
	}

	@Override
	public SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = super.getAssignedVariables();
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
		final SortedSet<String> variables = super.getReferencedVariables();
		for (final StatementInfo catchStatement : this.catchStatements) {
			variables.addAll(catchStatement.getReferencedVariables());
		}
		if (null != this.finallyStatement) {
			variables.addAll(this.finallyStatement.getReferencedVariables());
		}
		return variables;
	}
}
