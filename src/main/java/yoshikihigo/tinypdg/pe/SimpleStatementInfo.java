package yoshikihigo.tinypdg.pe;

import java.util.EnumSet;
import java.util.Set;

/**
 * 内側に文を持たない文。break, continue, return, throw, 式文などである。
 *
 * <p>{@link StatementInfo} が持つもの以外に状態を持たない。
 */
public final class SimpleStatementInfo extends StatementInfo {

	static final Set<CATEGORY> CATEGORIES = EnumSet.of(CATEGORY.Assert,
			CATEGORY.Break, CATEGORY.Case, CATEGORY.Continue, CATEGORY.Empty,
			CATEGORY.Expression, CATEGORY.Return, CATEGORY.Throw,
			CATEGORY.TypeDeclaration, CATEGORY.VariableDeclaration,
			CATEGORY.Yield, CATEGORY.Unsupported);

	public SimpleStatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine) {
		super(ownerBlock, category, startLine, endLine, CATEGORIES);
	}
}
