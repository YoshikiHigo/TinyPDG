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

		/** ラムダ式。本体は独立した MethodInfo として切り出される。 */
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

	public List<ProgramElementInfo> getExpressions() {
		final List<ProgramElementInfo> expressions = new ArrayList<>();
		expressions.addAll(this.expressions);
		return expressions;
	}

	public void setAnonymousClassDeclaration(
			final ClassInfo anonymousClassDeclaration) {
		Objects.requireNonNull(anonymousClassDeclaration, "\"anonymousClassDeclaration\" is null.");
		this.anonymousClassDeclaration = anonymousClassDeclaration;
	}

	public ClassInfo getAnonymousClassDeclaration() {
		return this.anonymousClassDeclaration;
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
			final SortedSet<String> variables = new TreeSet<>(
					this.expressions.get(0).getReferencedVariables());
			variables.addAll(this.expressions.get(2).getAssignedVariables());
			yield variables;
		}

		case VariableDeclarationFragment ->
			only(this.expressions.get(0).getText());

		case ForeachHeader -> {
			// 取り出す変数を定義する。反復対象の式の中で代入していれば、それも。
			final SortedSet<String> variables = only(
					((VariableInfo) this.expressions.get(0)).name);
			variables.addAll(this.expressions.get(1).getAssignedVariables());
			yield variables;
		}

		case Postfix ->
			// i++ は i を読み、かつ書く。被演算子は先頭の子。
			new TreeSet<>(this.expressions.get(0).getReferencedVariables());

		case Prefix ->
			// ++i は i を読み、かつ書く。-x や !flag は読むだけである。
			// 前置式は演算子が先頭の子で、被演算子はその次にある。
			this.isIncrementOrDecrement()
					? new TreeSet<>(this.expressions.get(1)
							.getReferencedVariables())
					: this.expressions.get(1).getAssignedVariables();

		case ArrayAccess, ArrayCreation, ArrayInitializer,
				Boolean, Cast, Character,
				ClassInstanceCreation, ConstructorInvocation, FieldAccess,
				Infix, Instanceof, MethodInvocation,
				Null, Number, Parenthesized,
				QualifiedName, SimpleName, String,
				SuperConstructorInvocation, SuperFieldAccess, SuperMethodInvocation,
				This, Trinomial, TypeLiteral,
				VariableDeclarationExpression, MethodEnter, Lambda,
				MethodReference, SwitchExpression, Pattern,
				Unsupported ->
			collectFromChildren(ProgramElementInfo::getAssignedVariables);
		};
	}

	@Override
	public SortedSet<String> getReferencedVariables() {

		return switch (this.category) {

		case Assignment -> {
			// 左辺は書き込み先であって読み出しではない。ただし += のような
			// 複合代入は左辺の値を読んでから書くので、左辺も参照に数える。
			final SortedSet<String> variables = new TreeSet<>(
					this.expressions.get(2).getReferencedVariables());
			if (!"=".equals(this.expressions.get(1).getText())) {
				variables.addAll(this.expressions.get(0).getReferencedVariables());
			}
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

		case ArrayAccess, ArrayCreation, ArrayInitializer,
				Boolean, Cast, Character,
				ClassInstanceCreation, ConstructorInvocation, FieldAccess,
				Infix, Instanceof, MethodInvocation,
				Null, Number, Parenthesized,
				QualifiedName, String, SuperConstructorInvocation,
				SuperFieldAccess, SuperMethodInvocation, This,
				Trinomial, TypeLiteral, VariableDeclarationExpression,
				MethodEnter, Lambda, MethodReference,
				SwitchExpression, Pattern, Unsupported ->
			collectFromChildren(ProgramElementInfo::getReferencedVariables);
		};
	}
}
