package yoshikihigo.tinypdg.pe;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/**
 * 条件式を持つ文。while, do, foreach, switch, synchronized, catch である。
 *
 * <p>synchronized と catch の「条件式」は真偽値ではなく、それぞれロック
 * 対象と捕捉する例外である。foreach の条件式は {@code T x : expr} という
 * ヘッダで、反復のたびに x を定義し expr を参照する。制御フローの上では
 * いずれも同じ位置に来るので、同じフィールドで扱っている。
 *
 * <p>以前は foreach を ForStatementInfo とし、変数と式を初期化式として
 * 持たせ、条件式を持たなかった。CFG では条件が疑似ノードになって消え、
 * 本体の出口がループの先頭と出口の両方に繋がっていた。PDG では変数と式が
 * CFG に現れない孤立したノードになり、ループ変数の定義から本体の使用へも、
 * 反復対象の定義からループへも、データ依存の辺が張られなかった。
 */
public sealed class ConditionalStatementInfo extends BlockStatementInfo
		permits ForStatementInfo, IfStatementInfo {

	static final Set<CATEGORY> CATEGORIES = EnumSet.of(CATEGORY.Catch,
			CATEGORY.Do, CATEGORY.Foreach, CATEGORY.Switch,
			CATEGORY.Synchronized, CATEGORY.While);

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

	/** @return 条件式。{@code for (;;)} のように持たない文もあるので null がありうる */
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
