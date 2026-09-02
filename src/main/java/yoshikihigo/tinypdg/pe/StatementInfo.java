package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 文。
 *
 * <p>ここには、どの種類の文でも意味を持つものだけを置く。所有ブロック、
 * 種別、式の並び、ラベルである。
 *
 * <p>条件式や else 節のように一部の文しか持たないものは、それを持つ文の
 * クラスに置いてある。以前は 8 つのコレクションが全てこのクラスにあり、
 * break 文でも try 用の catch 節や for 用の更新式を抱えていた。どの文で
 * どのフィールドが意味を持つかはコードのどこにも書かれておらず、if 文に
 * finally 節を設定しても何も起こらなかった。
 *
 * <p>階層は状態の形で切ってある。同じ形のものは同じクラスが表す。
 *
 * <pre>
 *   StatementInfo                  所有ブロック・種別・式・ラベル
 *   ├─ SimpleStatementInfo         これ以上持たない (break, return, ...)
 *   └─ BlockStatementInfo          + 文の並び (ブロック)
 *       ├─ ConditionalStatementInfo    + 条件式 (while, do, switch, ...)
 *       │   ├─ ForStatementInfo            + 初期化式・更新式 (for, foreach)
 *       │   └─ IfStatementInfo             + else 節
 *       └─ TryStatementInfo            + catch 節・finally 節
 * </pre>
 */
public sealed abstract class StatementInfo extends ProgramElementInfo
		permits SimpleStatementInfo, BlockStatementInfo {

	private ProgramElementInfo ownerBlock;
	private CATEGORY category;
	final private Set<CATEGORY> permittedCategories;
	final private List<ProgramElementInfo> expressions;
	private String label;

	/**
	 * @param permittedCategories このクラスが表せる種別。サブクラスが自身の
	 *                            定数を渡す。種別とクラスがずれた文が作られる
	 *                            のを防ぐためのもの
	 */
	StatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine,
			final Set<CATEGORY> permittedCategories) {

		super(startLine, endLine);

		this.permittedCategories = permittedCategories;
		this.ownerBlock = ownerBlock;
		this.expressions = new ArrayList<>();
		this.label = null;

		requireRepresentable(category);
		this.category = category;
	}

	private void requireRepresentable(final CATEGORY category) {
		Objects.requireNonNull(category, "\"category\" is null.");
		if (!this.permittedCategories.contains(category)) {
			throw new IllegalArgumentException(this.getClass().getSimpleName()
					+ " は " + category + " を表せない。");
		}
	}

	public enum CATEGORY {

		Assert,
		Break,
		Case,
		Catch,
		Continue,
		Do,
		Empty,
		Expression,
		If,
		For,
		Foreach,
		Return,
		SimpleBlock,
		Synchronized,
		Switch,
		Throw,
		Try,
		TypeDeclaration,
		VariableDeclaration,
		While,

		/** switch 式から値を返す yield 文。 */
		Yield,

		/**
		 * このツールがまだ個別に解釈できない構文。ソース断片をそのまま
		 * 保持する不透明な 1 要素として扱われる。
		 */
		Unsupported
	}

	public ProgramElementInfo getOwnerBlock() {
		return this.ownerBlock;
	}

	public void setOwnerBlock(final ProgramElementInfo ownerBlock) {
		Objects.requireNonNull(ownerBlock, "\"ownerBlock\" is null.");
		this.ownerBlock = ownerBlock;
	}

	public CATEGORY getCategory() {
		return this.category;
	}

	/**
	 * 種別を変える。同じクラスで表せる範囲に限る。
	 *
	 * <p>脱糖した yield を代入文として扱うときのように、後から種別が
	 * 変わることがある。ただし別の形の状態を持つ種別へは変えられない。
	 */
	public void setCategory(final CATEGORY category) {
		requireRepresentable(category);
		this.category = category;
	}

	public void addExpression(final ProgramElementInfo element) {
		Objects.requireNonNull(element, "\"element\" is null.");
		this.expressions.add(element);
	}

	public List<ProgramElementInfo> getExpressions() {
		return List.copyOf(this.expressions);
	}

	public String getLabel() {
		return this.label;
	}

	public void setLabel(final String label) {
		this.label = label;
	}

	public String getJumpToLabel() {
		if (this.expressions.isEmpty()) {
			return null;
		}
		return this.expressions.get(0).getText();
	}

	@Override
	public SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = new TreeSet<>();
		for (final ProgramElementInfo expression : this.expressions) {
			variables.addAll(expression.getAssignedVariables());
		}
		return variables;
	}

	@Override
	public SortedSet<String> getReferencedVariables() {
		final SortedSet<String> variables = new TreeSet<>();
		for (final ProgramElementInfo expression : this.expressions) {
			variables.addAll(expression.getReferencedVariables());
		}
		return variables;
	}
}
