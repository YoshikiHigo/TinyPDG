package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.TreeSet;

public class ExpressionInfo extends ProgramElementInfo {

	final public CATEGORY category;
	private ProgramElementInfo qualifier;
	final private List<ProgramElementInfo> expressions;
	private ClassInfo anonymousClassDeclaration;

	public ExpressionInfo(final CATEGORY category, final int startLine,
			final int endLine) {
		super(startLine, endLine);
		this.category = category;
		this.qualifier = null;
		this.expressions = new ArrayList<>();
		this.anonymousClassDeclaration = null;
	}

	public enum CATEGORY {

		ArrayAccess,
		ArrayCreation,
		ArrayInitializer,
		Assignment,
		Boolean,
		Cast,
		Character,
		ClassInstanceCreation,
		ConstructorInvocation,
		FieldAccess,
		Infix,
		Instanceof,
		MethodInvocation,
		Null,
		Number,
		Parenthesized,
		Postfix,
		Prefix,
		QualifiedName,
		SimpleName,
		String,
		SuperConstructorInvocation,
		SuperFieldAccess,
		SuperMethodInvocation,
		This,

		/** 三項演算子 (?:)。JDT の ConditionalExpression にあたる。 */
		Trinomial,

		TypeLiteral,
		VariableDeclarationExpression,
		VariableDeclarationFragment,
		MethodEnter,

		/**
		 * ラムダ式。本体は独立した MethodInfo として切り出され、この式の唯一の
		 * 子になる。囲む文は、本体が捕捉した変数を読み書きしていると見る。
		 */
		Lambda,

		/** メソッド参照 (String::length など)。 */
		MethodReference,

		/**
		 * 前に出せない位置に現れた switch 式。制御フローは持たず、
		 * セレクタと各アームを子として抱えるだけの 1 要素として扱う。
		 */
		SwitchExpression,

		/**
		 * パターン。record パターンや when 節つきパターンなど、内側に別の
		 * パターンを含みうるもの。定義される変数は内側のパターンから集まる。
		 */
		Pattern,

		/**
		 * foreach のヘッダ {@code T x : expr}。子は取り出す変数 (VariableInfo)
		 * と反復対象の式の 2 つ。反復のたびに変数を定義し、式を参照する。
		 * foreach 文の条件式としてループの制御ノードになる。
		 */
		ForeachHeader,

		/**
		 * このツールがまだ個別に解釈できない構文。ソース断片をそのまま
		 * 保持する不透明な 1 要素として扱われる。
		 */
		Unsupported
	}

	public void setQualifier(final ProgramElementInfo qualifier) {
		Objects.requireNonNull(qualifier, "\"qualifier\" is null.");
		this.qualifier = qualifier;
	}

	public ProgramElementInfo getQualifier() {
		return this.qualifier;
	}

	public void addExpression(final ProgramElementInfo expression) {
		Objects.requireNonNull(expression, "\"expression\" is null.");
		this.expressions.add(expression);
	}

	/** 子の式。変更できない写しで、StatementInfo の getExpressions と同じ。 */
	public List<ProgramElementInfo> getExpressions() {
		return List.copyOf(this.expressions);
	}

	public void setAnonymousClassDeclaration(
			final ClassInfo anonymousClassDeclaration) {
		Objects.requireNonNull(anonymousClassDeclaration, "\"anonymousClassDeclaration\" is null.");
		this.anonymousClassDeclaration = anonymousClassDeclaration;
	}

	public ClassInfo getAnonymousClassDeclaration() {
		return this.anonymousClassDeclaration;
	}

	/**
	 * 代入先の式が定義する変数。
	 *
	 * <p>a なら a。a[i] なら配列 a であって、添字 i ではない。o.f なら
	 * フィールド f と、中身が変わる o。以前は代入先の式が参照する変数を
	 * 全て代入先と見ていたので、a[i] = x は添字 i も定義することになり、
	 * 以後の i の使用がこの代入に依存していた。
	 */
	private static SortedSet<String> targets(final ProgramElementInfo lvalue) {

		if (!(lvalue instanceof ExpressionInfo expression)) {
			return lvalue.getReferencedVariables();
		}

		final List<ProgramElementInfo> children = expression.expressions;
		return switch (expression.category) {
		case SimpleName -> only(expression.getText());
		case ArrayAccess -> targets(children.get(0));
		case FieldAccess -> {
			final SortedSet<String> variables = targets(children.get(0));
			variables.add(children.get(1).getText());
			yield variables;
		}
		case QualifiedName -> {
			final SortedSet<String> variables = targets(expression.qualifier);
			variables.add(children.get(0).getText());
			yield variables;
		}
		case SuperFieldAccess -> only(children.get(0).getText());
		case Parenthesized -> targets(children.get(0));
		// 代入先になりえない、あるいは分解して見る理由のないもの。従来どおり
		// 参照する変数を全て代入先とする。
		default -> expression.getReferencedVariables();
		};
	}

	/**
	 * 代入先の式を評価するときに読む変数。a なら何もない。a[i] なら a と i。
	 * o.f なら o。
	 */
	private static SortedSet<String> readsForWriting(
			final ProgramElementInfo lvalue) {

		if (!(lvalue instanceof ExpressionInfo expression)) {
			return new TreeSet<>();
		}

		final List<ProgramElementInfo> children = expression.expressions;
		return switch (expression.category) {
		case SimpleName, SuperFieldAccess -> new TreeSet<>();
		case ArrayAccess -> expression.getReferencedVariables();
		case FieldAccess -> children.get(0).getReferencedVariables();
		case QualifiedName -> expression.qualifier.getReferencedVariables();
		case Parenthesized -> readsForWriting(children.get(0));
		default -> expression.getReferencedVariables();
		};
	}

	/**
	 * ラムダの本体が使う変数のうち、ラムダ自身の引数を除いたもの。
	 *
	 * <p>本体は独立した解析単位だが、囲む文から見れば、捕捉した変数を読み
	 * 書きしているのはその文である。{@code values.forEach(v -> total[0] += v)}
	 * は total を読み、書く。匿名クラスの本体と同じ扱いにする。本体の中で
	 * 宣言した局所変数は引数のようには見分けられず残る。匿名クラスと同じ
	 * 近似である。
	 */
	private SortedSet<String> capturedByLambda(
			final Function<ProgramElementInfo, SortedSet<String>> collector) {

		if (this.expressions.isEmpty()) {
			return new TreeSet<>();
		}
		final MethodInfo body = (MethodInfo) this.expressions.get(0);
		final SortedSet<String> variables = new TreeSet<>(collector.apply(body));
		for (final VariableInfo parameter : body.getParameters()) {
			variables.remove(parameter.name);
		}
		return variables;
	}

	/**
	 * メソッド呼び出しが読み書きする変数。レシーバ (修飾子) と引数から集める。
	 *
	 * <p>先頭の子はメソッド名で、変数ではない。以前は他の子と同じく SimpleName
	 * として数えていたので、{@code s.length()} が変数 length を参照することに
	 * なり、同名の局所変数があると偽のデータ依存が出た (issue #7)。正規化
	 * テキストの側は元からメソッド名を変数扱いしていない。
	 */
	private SortedSet<String> collectFromInvocation(
			final Function<ProgramElementInfo, SortedSet<String>> collector) {

		final SortedSet<String> variables = new TreeSet<>();
		if (null != this.qualifier) {
			variables.addAll(collector.apply(this.qualifier));
		}
		for (final ProgramElementInfo argument : this.expressions.subList(1,
				this.expressions.size())) {
			variables.addAll(collector.apply(argument));
		}
		return variables;
	}

	/** 前置式の演算子が ++ か -- か。演算子は先頭の子である。 */
	private boolean isIncrementOrDecrement() {
		final String operator = this.expressions.get(0).getText();
		return "++".equals(operator) || "--".equals(operator);
	}

	/** 名前 1 つだけを含む集合を作る。 */
	private static SortedSet<String> only(final String name) {
		final SortedSet<String> variables = new TreeSet<>();
		variables.add(name);
		return variables;
	}

	/**
	 * 子要素をひととおり辿って変数を集める。
	 *
	 * <p>ほとんどの種別の式は、自分では何も足さず子の結果を集めるだけである。
	 * その共通部分をここに置く。
	 *
	 * <p>子は 3 か所に分かれて入っている。expressions のほかに、修飾子が
	 * 専用のフィールドに、無名クラスの本体がさらに別のフィールドに入る。
	 * 修飾子を辿り忘れると、reader.read() の reader のようなレシーバが
	 * まるごと抜け落ちる。
	 */
	private SortedSet<String> collectFromChildren(
			final Function<ProgramElementInfo, SortedSet<String>> collector) {

		final SortedSet<String> variables = new TreeSet<>();

		for (final ProgramElementInfo expression : this.expressions) {
			variables.addAll(collector.apply(expression));
		}

		if (null != this.qualifier) {
			variables.addAll(collector.apply(this.qualifier));
		}

		if (null != this.anonymousClassDeclaration) {
			for (final MethodInfo method : this.anonymousClassDeclaration
					.getMethods()) {
				variables.addAll(collector.apply(method));
			}
		}

		return variables;
	}

	/*
	 * 以下 2 つは switch 文ではなく switch 式である。default 節を持たない
	 * 代わりに全ての定数を挙げてあり、CATEGORY に定数を足すとコンパイルが
	 * 通らなくなる。新しい種別をどちらの扱いにするか決めることを強制される。
	 *
	 * 文ではなく式にしているのはそのためである。switch 文は網羅していなくても
	 * コンパイルが通ってしまい、書き漏らした種別は黙って何もせず素通りする。
	 * 網羅性を検査してもらえるのは switch 式だけである。
	 */

	@Override
	public SortedSet<String> getAssignedVariables() {

		return switch (this.category) {

		case Assignment -> {
			// 左辺が代入先。右辺は右辺でさらに代入しているかもしれない (a = b = c)。
			// 左辺の中でも代入しうる (a[i++] = x の i)。以前はどちらも見ていな
			// かった (issue #21)。
			final SortedSet<String> variables = targets(this.expressions.get(0));
			variables.addAll(this.expressions.get(0).getAssignedVariables());
			variables.addAll(this.expressions.get(2).getAssignedVariables());
			yield variables;
		}

		case VariableDeclarationFragment -> {
			// 宣言する変数と、初期化子の中で代入している変数 (int x = (y = 1) の y)。
			final SortedSet<String> variables = only(this.expressions.get(0).getText());
			if (1 < this.expressions.size()) {
				variables.addAll(this.expressions.get(1).getAssignedVariables());
			}
			yield variables;
		}

		case ForeachHeader -> {
			// 取り出す変数を定義する。反復対象の式の中で代入していれば、それも。
			final SortedSet<String> variables = only(
					((VariableInfo) this.expressions.get(0)).name);
			variables.addAll(this.expressions.get(1).getAssignedVariables());
			yield variables;
		}

		case Postfix -> {
			// i++ は i を読み、かつ書く。被演算子は先頭の子。被演算子の中の代入
			// (a[i++]++ の i) も数える。
			final SortedSet<String> variables = targets(this.expressions.get(0));
			variables.addAll(this.expressions.get(0).getAssignedVariables());
			yield variables;
		}

		case Prefix -> {
			// ++i は i を読み、かつ書く。-x や !flag は読むだけである。
			// 前置式は演算子が先頭の子で、被演算子はその次にある。
			final SortedSet<String> variables = this.expressions.get(1)
					.getAssignedVariables();
			if (this.isIncrementOrDecrement()) {
				variables.addAll(targets(this.expressions.get(1)));
			}
			yield variables;
		}

		case Lambda ->
			this.capturedByLambda(ProgramElementInfo::getAssignedVariables);

		case MethodInvocation, SuperMethodInvocation ->
			this.collectFromInvocation(ProgramElementInfo::getAssignedVariables);

		case ArrayAccess, ArrayCreation, ArrayInitializer,
				Boolean, Cast, Character,
				ClassInstanceCreation, ConstructorInvocation, FieldAccess,
				Infix, Instanceof, Null,
				Number, Parenthesized, QualifiedName,
				SimpleName, String, SuperConstructorInvocation,
				SuperFieldAccess, This, Trinomial,
				TypeLiteral, VariableDeclarationExpression, MethodEnter,
				MethodReference, SwitchExpression, Pattern,
				Unsupported ->
			collectFromChildren(ProgramElementInfo::getAssignedVariables);
		};
	}

	@Override
	public SortedSet<String> getReferencedVariables() {

		return switch (this.category) {

		case Assignment -> {
			// 右辺と、左辺を評価するために読むもの (a[i] = x の a と i)。
			// += のような複合代入は、それに加えて左辺の値そのものも読む。
			final ProgramElementInfo left = this.expressions.get(0);
			final SortedSet<String> variables = new TreeSet<>(
					this.expressions.get(2).getReferencedVariables());
			variables.addAll("=".equals(this.expressions.get(1).getText())
					? readsForWriting(left)
					: left.getReferencedVariables());
			yield variables;
		}

		case VariableDeclarationFragment ->
			// 初期化子を持たない宣言では、読み出している変数はない。
			1 < this.expressions.size()
					? new TreeSet<>(this.expressions.get(1)
							.getReferencedVariables())
					: new TreeSet<>();

		case ForeachHeader ->
			// 反復対象の式を読む。取り出す変数は定義であって参照ではない。
			new TreeSet<>(this.expressions.get(1).getReferencedVariables());

		case Postfix ->
			new TreeSet<>(this.expressions.get(0).getReferencedVariables());

		case Prefix ->
			// 被演算子は 2 つ目の子。以前は先頭の子、つまり演算子を見ていたので、
			// 前置式は何も参照も定義もしていなかった。
			new TreeSet<>(this.expressions.get(1).getReferencedVariables());

		case SimpleName ->
			only(this.getText());

		case Lambda ->
			this.capturedByLambda(ProgramElementInfo::getReferencedVariables);

		case MethodInvocation, SuperMethodInvocation ->
			this.collectFromInvocation(ProgramElementInfo::getReferencedVariables);

		case ArrayAccess, ArrayCreation, ArrayInitializer,
				Boolean, Cast, Character,
				ClassInstanceCreation, ConstructorInvocation, FieldAccess,
				Infix, Instanceof, Null,
				Number, Parenthesized, QualifiedName,
				String, SuperConstructorInvocation, SuperFieldAccess,
				This, Trinomial, TypeLiteral,
				VariableDeclarationExpression, MethodEnter, MethodReference,
				SwitchExpression, Pattern, Unsupported ->
			collectFromChildren(ProgramElementInfo::getReferencedVariables);
		};
	}
}
