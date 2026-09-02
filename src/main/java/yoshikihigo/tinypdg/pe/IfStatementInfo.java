package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/** if 文。条件式に加えて else 節を持つ。 */
public final class IfStatementInfo extends ConditionalStatementInfo {

	static final Set<CATEGORY> CATEGORIES = EnumSet.of(CATEGORY.If);

	final private List<StatementInfo> elseStatements;

	public IfStatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine) {
		super(ownerBlock, category, startLine, endLine, CATEGORIES);
		this.elseStatements = new ArrayList<>();
	}

	public void setElseStatement(final StatementInfo elseBody) {
		Objects.requireNonNull(elseBody, "\"elseBody\" is null.");
		this.elseStatements.clear();
		if (elseBody instanceof BlockStatementInfo block
				&& CATEGORY.SimpleBlock == elseBody.getCategory()) {
			// 中身を取り出す。空のブロックだった場合は何も入らず、else 節が
			// なかったのと同じになる。本体側 (flatten) は空のブロックを
			// そのまま抱えるので、そこだけ扱いが違う。元からこうなっている。
			this.elseStatements.addAll(block.getStatements());
		} else {
			this.elseStatements.add(elseBody);
		}
	}

	public List<StatementInfo> getElseStatements() {
		return Collections.unmodifiableList(this.elseStatements);
	}

	@Override
	public SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = super.getAssignedVariables();
		for (final StatementInfo statement : this.elseStatements) {
			variables.addAll(statement.getAssignedVariables());
		}
		return variables;
	}

	@Override
	public SortedSet<String> getReferencedVariables() {
		final SortedSet<String> variables = super.getReferencedVariables();
		for (final StatementInfo statement : this.elseStatements) {
			variables.addAll(statement.getReferencedVariables());
		}
		return variables;
	}
}
