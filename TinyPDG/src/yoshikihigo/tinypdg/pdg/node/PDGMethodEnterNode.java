package yoshikihigo.tinypdg.pdg.node;

import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;

public class PDGMethodEnterNode extends PDGControlNode {

	static public PDGMethodEnterNode getInstance(final MethodInfo method) {
		assert null != method : "\"method\" is null.";
		final ExpressionInfo methodEnterExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.MethodEnter, method.startLine,
				method.endLine);
		return new PDGMethodEnterNode(methodEnterExpression);
	}

	private PDGMethodEnterNode(final ExpressionInfo methodEnterExpression) {
		super(methodEnterExpression);
	}
}
