package yoshikihigo.tinypdg.pdg.node;

import java.util.Objects;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;

public class PDGMethodEnterNode extends PDGControlNode {

	/**
	 * メソッドの入口ノードを新しく作る。
	 *
	 * <p>以前は getInstance という名前だった。共有の 1 個を返すように読めるが、
	 * 呼ぶたびに新しいノードを作っている。共有しているのはファクトリの側である。
	 */
	static public PDGMethodEnterNode create(final MethodInfo method) {
		Objects.requireNonNull(method, "\"method\" is null.");
		final ProgramElementInfo methodEnterExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.MethodEnter, method.startLine,
				method.endLine);
		methodEnterExpression.setText("Enter");
		return new PDGMethodEnterNode(methodEnterExpression);
	}

	private PDGMethodEnterNode(final ProgramElementInfo methodEnterExpression) {
		super(methodEnterExpression);
	}
}
