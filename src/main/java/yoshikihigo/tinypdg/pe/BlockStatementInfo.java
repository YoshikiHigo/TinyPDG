package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/**
 * 内側に文を並べられる文。
 *
 * <p>このクラス自身は素のブロック <code>{ ... }</code> を表す。条件式や
 * catch 節を持つものは、これを継承したクラスが表す。
 *
 * <p>文を抱えられるのはここから下だけである。{@link BlockInfo} を実装して
 * いるのもここから下で、以前は break 文でさえ実装していた。
 */
public sealed class BlockStatementInfo extends StatementInfo implements
		BlockInfo permits ConditionalStatementInfo, TryStatementInfo {

	static final Set<CATEGORY> CATEGORIES = EnumSet.of(CATEGORY.SimpleBlock);

	final private List<StatementInfo> statements;

	public BlockStatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine) {
		this(ownerBlock, category, startLine, endLine, CATEGORIES);
	}

	BlockStatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine,
			final Set<CATEGORY> permittedCategories) {
		super(ownerBlock, category, startLine, endLine, permittedCategories);
		this.statements = new ArrayList<>();
	}

	@Override
	public void setStatement(final StatementInfo statement) {
		Objects.requireNonNull(statement, "\"statement\" is null.");
		this.statements.clear();
		this.statements.addAll(flatten(statement));
	}

	@Override
	public void addStatement(final StatementInfo statement) {
		Objects.requireNonNull(statement, "\"statement\" is null.");
		this.statements.add(statement);
	}

	@Override
	public void addStatements(final Collection<StatementInfo> statements) {
		Objects.requireNonNull(statements, "\"statements\" is null.");
		this.statements.addAll(statements);
	}

	@Override
	public List<StatementInfo> getStatements() {
		return Collections.unmodifiableList(this.statements);
	}

	/**
	 * 単一の文として与えられた本体を、並べるべき文の列に均す。
	 *
	 * <p>ブロックが渡された場合、そのブロックを 1 個の文として抱えるのでは
	 * なく中身を取り出す。入れ子のブロックのままだと CFG が中身を展開せず、
	 * 1 個の不透明なノードになってしまう。
	 *
	 * <p>ただし中身が空のブロックはそのまま抱える。取り出すと何も残らず、
	 * 本体があったこと自体が消えてしまう。
	 *
	 * <p>visitor も、文の並びを受け取るところでこれを使う。複数の変数を
	 * 宣言する文は変数ごとの文に分かれ、SimpleBlock に包まれて届く。
	 */
	public static List<StatementInfo> flatten(final StatementInfo body) {
		if (body instanceof BlockStatementInfo block
				&& CATEGORY.SimpleBlock == body.getCategory()
				&& !block.getStatements().isEmpty()) {
			return List.copyOf(block.getStatements());
		}
		return List.of(body);
	}

	@Override
	public SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = super.getAssignedVariables();
		for (final StatementInfo statement : this.statements) {
			variables.addAll(statement.getAssignedVariables());
		}
		return variables;
	}

	@Override
	public SortedSet<String> getReferencedVariables() {
		final SortedSet<String> variables = super.getReferencedVariables();
		for (final StatementInfo statement : this.statements) {
			variables.addAll(statement.getReferencedVariables());
		}
		return variables;
	}
}
