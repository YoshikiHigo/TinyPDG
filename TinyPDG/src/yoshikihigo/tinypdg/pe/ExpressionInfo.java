package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class ExpressionInfo extends ProgramElementInfo {

	final public CATEGORY category;
	final private List<ExpressionInfo> expressions;
	private String text;

	public ExpressionInfo(final CATEGORY category, final int startLine,
			final int endLine) {
		super(startLine, endLine);
		this.category = category;
		this.expressions = new ArrayList<ExpressionInfo>();
		this.text = null;
	}

	public enum CATEGORY {

		ArrayAccess("ARRAYACCESS"), ArrayCreation("ARRAYCREATION"), ArrayInitializer(
				"ARRAYINITIALIZER"), Assignment("ASSIGNMENT"), Binomial(
				"BINOMIAL"), Boolean("BOOLEAN"), Cast("CAST"), Character(
				"CHARACTER"), ClassInstanceCreation("CLASSINSTANCECREATION"), ConstructorInvocation(
				"CONSTRUCTORINVOCATION"), FieldAccess("FIELDACCESS"), Infix(
				"INFIX"), Instanceof("INSTANCEOF"), MethodInvocation(
				"METHODINVOCATION"), Null("NULL"), Number("NUMBER"), Parenthesized(
				"PARENTHESIZED"), Postfix("POSTFIX"), Prefix("PREFIX"), QualifiedName(
				"QUALIFIEDNAME"), SimpleName("SIMPLENAME"), String("STRING"), SuperConstructorInvocation(
				"SUPERCONSTRUCTORINVOCATION"), SuperFieldAccess(
				"SUPERFIELDACCESS"), SuperMethodInvocation(
				"SUPERMETHODINVOCATION"), This("THIS"), Trinomial("TRINOMIAL"), TypeLiteral(
				"TYPELITERAL"), VariableDeclarationExpression(
				"VARIABLEDECLARATIONEXPRESSION"), VariableDeclarationFragment(
				"VARIABLEDECLARATIONFRAGMENT");

		final public String id;

		CATEGORY(final String id) {
			this.id = id;
		}
	}

	public void addExpression(final ExpressionInfo expression) {
		assert null != expression : "\"expression\" is null.";
		this.expressions.add(expression);
	}

	public List<ExpressionInfo> getExpressions() {
		return Collections.unmodifiableList(this.expressions);
	}

	public void setText(final String text) {
		assert null != text : "\"text\" is null.";
		this.text = text;
	}

	public SortedSet<String> getAssignedVariables() {

		final SortedSet<String> variables = new TreeSet<String>();
		switch (this.category) {
		case Assignment:
			final ExpressionInfo left = this.expressions.get(0);
			variables.addAll(left.getReferencedVariables());
			final ExpressionInfo right = this.expressions.get(1);
			variables.addAll(right.getAssignedVariables());
			break;
		case Postfix:
		case Prefix:
			final ExpressionInfo operand = this.expressions.get(0);
			variables.addAll(operand.getReferencedVariables());
			break;
		default:
			for (final ExpressionInfo expression : this.expressions) {
				variables.addAll(expression.getAssignedVariables());
			}
			break;
		}
		return variables;
	}

	public SortedSet<String> getReferencedVariables() {
		final SortedSet<String> variables = new TreeSet<String>();
		switch (this.category) {
		case Assignment:
			final ExpressionInfo right = this.expressions.get(1);
			variables.addAll(right.getReferencedVariables());
			break;
		case Postfix:
		case Prefix:
			final ExpressionInfo operand = this.expressions.get(0);
			variables.addAll(operand.getReferencedVariables());
			break;
		default:
			for (final ExpressionInfo expression : this.expressions) {
				variables.addAll(expression.getReferencedVariables());
			}
			break;
		}
		return variables;
	}
}
