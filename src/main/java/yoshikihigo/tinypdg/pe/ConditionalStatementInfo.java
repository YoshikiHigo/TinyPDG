package yoshikihigo.tinypdg.pe;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/**
 * 条件式を持つ文。while, do, switch, synchronized, catch である。
 *
 * <p>synchronized と catch の「条件式」は真偽値ではなく、それぞれロック
 * 対象と捕捉する例外である。制御フローの上では同じ位置に来るので、同じ
 * フィールドで扱っている。
 */
public sealed class ConditionalStatementInfo extends BlockStatementInfo
		permits ForStatementInfo, IfStatementInfo {

	static final Set<CATEGORY> CATEGORIES = EnumSet.of(CATEGORY.Catch,
			CATEGORY.Do, CATEGORY.Switch, CATEGORY.Synchronized,
			CATEGORY.While);

	private ProgramElementInfo condition;

	public ConditionalStatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine) {
		this(ownerBlock, category, startLine, endLine, CATEGORIES);
	}

	ConditionalStatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine,
			final Set<CATEGORY> permittedCategories) {
		super(ownerBlock, category, startLine, endLine, permittedCategories);
		this.condition = null;
	}

	public void setCondition(final ProgramElementInfo condition) {
		Objects.requireNonNull(condition, "\"condition\" is null.");
		this.condition = condition;
	}

	/** @return 条件式。foreach のように持たない文もあるので null がありうる */
	public ProgramElementInfo getCondition() {
		return this.condition;
	}

	@Override
	public SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = super.getAssignedVariables();
		if (null != this.condition) {
			variables.addAll(this.condition.getAssignedVariables());
		}
		return variables;
	}

	@Override
	public SortedSet<String> getReferencedVariables() {
		final SortedSet<String> variables = super.getReferencedVariables();
		if (null != this.condition) {
			variables.addAll(this.condition.getReferencedVariables());
		}
		return variables;
	}
}
