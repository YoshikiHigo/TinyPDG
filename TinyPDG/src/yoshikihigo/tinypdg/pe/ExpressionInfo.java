package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

		ArrayAccess("ARRAYACCESS"), Binomial("BINOMIAL"), Character("CHARACTER"), Infix(
				"INFIX"), Null("NULL"), Number("NUMBER"), Postfix("POSTFIX"), Prefix(
				"PREFIX"), String("STRING"), Trinomial("TRINOMIAL"), TypeLiteral(
				"TYPELITERAL");

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
}
