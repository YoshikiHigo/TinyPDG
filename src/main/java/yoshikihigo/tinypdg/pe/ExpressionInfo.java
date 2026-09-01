package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
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

	@Override
	public SortedSet<String> getAssignedVariables() {

		final SortedSet<String> variables = new TreeSet<>();
		switch (this.category) {
		case Assignment:
			final ProgramElementInfo left = this.expressions.get(0);
			variables.addAll(left.getReferencedVariables());
			final ProgramElementInfo right = this.expressions.get(2);
			variables.addAll(right.getAssignedVariables());
			break;
		case VariableDeclarationFragment:
			variables.add(this.getExpressions().get(0).getText());
			break;
		case Postfix:
		case Prefix:
			final ProgramElementInfo operand = this.expressions.get(0);
			variables.addAll(operand.getReferencedVariables());
			break;
		default:
			for (final ProgramElementInfo expression : this.expressions) {
				variables.addAll(expression.getAssignedVariables());
			}
			// 修飾子は expressions とは別のフィールドに入るため、
			// ここで明示的に辿らないと部分木がまるごと抜け落ちる。
			if (null != this.qualifier) {
				variables.addAll(this.qualifier.getAssignedVariables());
			}
			if (null != this.getAnonymousClassDeclaration()) {
				for (final MethodInfo method : this
						.getAnonymousClassDeclaration().getMethods()) {
					variables.addAll(method.getAssignedVariables());
				}
			}
			break;
		}
		return variables;
	}

	@Override
	public SortedSet<String> getReferencedVariables() {
		final SortedSet<String> variables = new TreeSet<>();
		switch (this.category) {
		case Assignment:
			final ProgramElementInfo right = this.expressions.get(2);
			variables.addAll(right.getReferencedVariables());
			break;
		case VariableDeclarationFragment:
			if (1 < this.getExpressions().size()) {
				variables.addAll(this.getExpressions().get(1)
						.getReferencedVariables());
			}
			break;
		case Postfix:
		case Prefix:
			final ProgramElementInfo operand = this.expressions.get(0);
			variables.addAll(operand.getReferencedVariables());
			break;
		case SimpleName:
			variables.add(this.getText());
			break;
		default:
			for (final ProgramElementInfo expression : this.expressions) {
				variables.addAll(expression.getReferencedVariables());
			}
			// レシーバも参照である。reader.read() は reader を読んでいる。
			//
			// FieldAccess は受け手を addExpression で持つので以前から
			// 数えられていたが、QualifiedName・MethodInvocation・
			// SuperConstructorInvocation は setQualifier で別のフィールドに
			// 入れるため、この走査から漏れていた。構造的に同じものが、
			// visitor がどちらのセッターを使ったかだけで別扱いになっていた。
			if (null != this.qualifier) {
				variables.addAll(this.qualifier.getReferencedVariables());
			}
			if (null != this.getAnonymousClassDeclaration()) {
				for (final MethodInfo method : this
						.getAnonymousClassDeclaration().getMethods()) {
					variables.addAll(method.getReferencedVariables());
				}
			}
			break;
		}
		return variables;
	}
}
