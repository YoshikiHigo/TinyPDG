package yoshikihigo.tinypdg.ast;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.GuardedPattern;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.RecordPattern;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import yoshikihigo.tinypdg.pe.BlockStatementInfo;
import yoshikihigo.tinypdg.pe.ClassInfo;
import yoshikihigo.tinypdg.pe.ConditionalStatementInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.OperatorInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.SimpleStatementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.TypeInfo;
import yoshikihigo.tinypdg.pe.VariableInfo;

/**
 * 式を扱う段。
 *
 * <p>変数参照や演算子のような、それ以上分解しない構文もここに含む。
 */
abstract class ExpressionVisitor extends ProgramElementVisitor {

	ExpressionVisitor(final String path, final CompilationUnit root,
			final List<MethodInfo> methods) {
		super(path, root, methods);
	}

	/**
	 * switch 式。
	 *
	 * <p>値を返す「式」でありながら内部に分岐と文を持つため、そのままでは
	 * どこにも収まらない。CFG は StatementInfo からしか組み立てられないので、
	 * 式のままでは分岐が見えない。
	 *
	 * <p>そこで、一時変数へ代入する switch 文へ書き換えて元の文の前に置き、
	 * 元の位置にはその一時変数の参照だけを残す。
	 *
	 * <pre>
	 *   int y = switch (x) { case 1 -&gt; 10; default -&gt; 20; };
	 *     ↓
	 *   switch (x) { case 1: $switch1 = 10; default: $switch1 = 20; }
	 *   int y = $switch1;
	 * </pre>
	 *
	 * <p>ただし前に出せない位置がある。短絡評価の右辺、三項演算子の枝、
	 * ループの条件や更新式では、前に出すと評価される回数やタイミングが
	 * 変わってしまう。そうした位置では脱糖せず、セレクタと各アームを子として
	 * 抱えるだけの式にする。制御フローは見えないが、参照・定義される変数は
	 * 正しく集まる。
	 */
	@Override
	public boolean visit(final SwitchExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);

		if (!canHoist(node)) {
			this.buildInlineSwitchExpression(node, startLine, endLine);
			return false;
		}

		final String target = "$switch" + (++this.switchExpressionCount);

		final ConditionalStatementInfo switchBlock = new ConditionalStatementInfo(this.nearestBlock(),
				StatementInfo.CATEGORY.Switch, startLine, endLine);
		this.stack.push(switchBlock);

		final ProgramElementInfo condition = this.visitChild(node.getExpression());
		switchBlock.setCondition(condition);
		condition.setOwnerConditinalBlock(switchBlock);

		final StringBuilder text = new StringBuilder();
		text.append("switch (");
		text.append(condition.getText());
		text.append(") {");
		text.append(System.lineSeparator());

		this.yieldTargets.push(target);
		for (final Object o : node.statements()) {
			this.yieldConverted = false;
			final StatementInfo statement = (StatementInfo) this.visitChild((ASTNode) o);

			if (statement instanceof BlockStatementInfo arm
					&& StatementInfo.CATEGORY.SimpleBlock == statement
							.getCategory()) {
				// ブロック形式のアーム。入れ子のまま置くと CFG が中身を
				// 展開せず 1 個の不透明なノードにしてしまうので、
				// 文を取り出して並べる。
				for (final StatementInfo inner : arm.getStatements()) {
					inner.setOwnerBlock(switchBlock);
					switchBlock.addStatement(inner);
					text.append(inner.getText());
					text.append(System.lineSeparator());
				}
			} else {
				switchBlock.addStatement(statement);
				text.append(statement.getText());
				text.append(System.lineSeparator());
			}

			// yield はアームを終わらせる。switch 文へ書き換えた以上、
			// 明示的に break を置かないと次のアームへ流れてしまう。
			// フラグは yield が入れ子のブロックの中にあっても立つので、
			// 矢印形式でもコロン形式でも同じ判定で済む。
			if (this.yieldConverted) {
				final SimpleStatementInfo jump = new SimpleStatementInfo(switchBlock,
						StatementInfo.CATEGORY.Break, statement.startLine,
						statement.endLine);
				jump.setText("break;");
				switchBlock.addStatement(jump);
				text.append(jump.getText());
				text.append(System.lineSeparator());
			}
		}
		this.yieldConverted = false;
		this.yieldTargets.pop();

		text.append("}");
		switchBlock.setText(text.toString());

		this.stack.pop();
		this.pendingStatements.add(switchBlock);

		// 元の位置には一時変数の参照だけを残す。
		final ExpressionInfo reference = new ExpressionInfo(
				ExpressionInfo.CATEGORY.SimpleName, startLine, endLine);
		reference.setText(target);
		this.stack.push(reference);

		return false;
	}

	/**
	 * 前に出せない位置の switch 式を、子を抱えた 1 個の式として組み立てる。
	 */
	private void buildInlineSwitchExpression(final SwitchExpression node,
			final int startLine, final int endLine) {

		final ExpressionInfo switchExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.SwitchExpression, startLine, endLine);
		this.stack.push(switchExpression);

		final ProgramElementInfo condition = this.visitChild(node.getExpression());
		switchExpression.addExpression(condition);

		// アームの中身は文なので、文の visit が動くようブロックを一枚かませる。
		// このブロック自体はグラフに出さず、中身だけを引き取る。
		final BlockStatementInfo scratch = new BlockStatementInfo(this.nearestBlock(),
				StatementInfo.CATEGORY.SimpleBlock, startLine, endLine);
		this.stack.push(scratch);
		for (final Object o : node.statements()) {
			final ProgramElementInfo statement = this.visitChild((ASTNode) o);
			switchExpression.addExpression(statement);
		}
		this.stack.pop();

		switchExpression.setText(flatten(node));
	}

	/** テキストブロック。値としては通常の文字列リテラルと変わらない。 */
	@Override
	public boolean visit(final TextBlock node) {
		final ProgramElementInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.String, this.getStartLineNumber(node),
				this.getEndLineNumber(node));
		expression.setText("\"" + node.getLiteralValue() + "\"");
		this.stack.push(expression);
		return false;
	}

	/**
	 * 型パターン (o instanceof String s の String s の部分)。
	 *
	 * <p>パターン変数はその場での変数定義なので、変数宣言と同じ形にして
	 * 「定義された変数」として数えられるようにする。
	 */
	@Override
	public boolean visit(final TypePattern node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo pattern = new ExpressionInfo(
				ExpressionInfo.CATEGORY.VariableDeclarationFragment, startLine,
				endLine);

		// getPatternVariable() は JLS20 専用の旧 API で、それより新しい AST では
		// 例外も出さずに空のダミー ("int MISSING") を返す。JLS22 以降は
		// getPatternVariable2() を使う。
		final VariableDeclaration variable = node.getPatternVariable2();
		final ExpressionInfo name = new ExpressionInfo(
				ExpressionInfo.CATEGORY.SimpleName, startLine, endLine);
		name.setText(variable.getName().getIdentifier());
		pattern.addExpression(name);
		pattern.setText(flatten(node));

		this.stack.push(pattern);
		return false;
	}

	/** record パターン。内側のパターンが定義する変数をまとめて持つ。 */
	@Override
	public boolean visit(final RecordPattern node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo pattern = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Pattern, startLine, endLine);
		this.stack.push(pattern);

		final StringBuilder text = new StringBuilder();
		text.append(node.getPatternType().toString());
		text.append("(");
		final List<ProgramElementInfo> nested = this.visitChildren(node.patterns());
		nested.forEach(pattern::addExpression);
		text.append(joinTexts(nested, ", "));
		text.append(")");
		pattern.setText(text.toString());

		return false;
	}

	/** when 節つきパターン。 */
	@Override
	public boolean visit(final GuardedPattern node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo guarded = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Pattern, startLine, endLine);
		this.stack.push(guarded);

		final ProgramElementInfo pattern = this.visitChild(node.getPattern());
		guarded.addExpression(pattern);

		final ProgramElementInfo guard = this.visitChild(node.getExpression());
		guarded.addExpression(guard);

		guarded.setText(pattern.getText() + " when " + guard.getText());

		return false;
	}

	/** o instanceof String s 形式。 */
	@Override
	public boolean visit(final PatternInstanceofExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo instanceofExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Instanceof, startLine, endLine);
		this.stack.push(instanceofExpression);

		final ProgramElementInfo left = this.visitChild(node.getLeftOperand());
		instanceofExpression.addExpression(left);

		final ProgramElementInfo pattern = this.visitChild(node.getPattern());
		instanceofExpression.addExpression(pattern);

		instanceofExpression.setText(
				left.getText() + " instanceof " + pattern.getText());
		return false;
	}

	/**
	 * ラムダ式。
	 *
	 * <p>本体は呼び出し元の式に埋め込まず、独立した 1 つの「メソッド」として
	 * 切り出す。intraprocedural な解析という前提と整合し、匿名クラスの扱いとも
	 * 揃う。呼び出し元の式には、ラムダ 1 個ぶんの要素だけを残す。
	 */
	@Override
	public boolean visit(final LambdaExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);

		final MethodInfo lambda = new MethodInfo(this.path, "lambda$" + startLine,
				startLine, endLine);
		this.stack.push(lambda);

		final StringBuilder signature = new StringBuilder();
		signature.append("(");
		for (final Object o : node.parameters()) {
			final ProgramElementInfo parameter = this.visitChild((ASTNode) o);
			// (String s) -> ... なら SingleVariableDeclaration として
			// VariableInfo が積まれる。s -> ... のように型を書かない場合は
			// VariableDeclarationFragment なので、名前から組み立て直す。
			final VariableInfo variable;
			if (parameter instanceof VariableInfo) {
				variable = (VariableInfo) parameter;
			} else {
				variable = new VariableInfo(VariableInfo.CATEGORY.PARAMETER,
						new TypeInfo("var", parameter.startLine, parameter.endLine),
						parameter.getText(), parameter.startLine, parameter.endLine);
				variable.setText(parameter.getText());
			}
			variable.setCategory(VariableInfo.CATEGORY.PARAMETER);
			lambda.addParameter(variable);
			signature.append(variable.getText());
			signature.append(",");
		}
		if (0 < node.parameters().size()) {
			signature.deleteCharAt(signature.length() - 1);
		}
		signature.append(") -> ");

		final ASTNode body = node.getBody();
		if (body instanceof Block) {
			final ProgramElementInfo statement = this.visitChild(body);
			lambda.setStatement((StatementInfo) statement);
			signature.append(statement.getText());

		} else {
			// 式本体のラムダ。x -> expr は return expr; と同じ意味なので
			// return 文に組み替える。通常のメソッドは本体が必ずブロックであり、
			// PDG の構築もそれを前提にしているため、ブロックで包んでおく。
			final int bodyStart = this.getStartLineNumber(body);
			final int bodyEnd = this.getEndLineNumber(body);

			final BlockStatementInfo block = new BlockStatementInfo(lambda,
					StatementInfo.CATEGORY.SimpleBlock, bodyStart, bodyEnd);
			this.stack.push(block);

			final SimpleStatementInfo returnStatement = new SimpleStatementInfo(block,
					StatementInfo.CATEGORY.Return, bodyStart, bodyEnd);
			this.stack.push(returnStatement);

			final ProgramElementInfo expression = this.visitChild(body);
			returnStatement.addExpression(expression);
			returnStatement.setText("return " + expression.getText() + ";");

			this.stack.pop();
			block.addStatement(returnStatement);
			block.setText("{" + System.lineSeparator() + returnStatement.getText()
					+ System.lineSeparator() + "}");

			this.stack.pop();
			lambda.setStatement(block);
			signature.append(block.getText());
		}

		lambda.setText(signature.toString());

		this.stack.pop();
		this.methods.add(lambda);

		// 呼び出し元の式に残すのは、ラムダ 1 個ぶんの要素。
		final ExpressionInfo reference = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Lambda, startLine, endLine);
		reference.setText(flatten(node));
		this.stack.push(reference);

		return false;
	}

	/** メソッド参照。式としては 1 個の要素にまとめる。 */
	@Override
	public boolean visit(final ExpressionMethodReference node) {
		return this.visitMethodReference(node);
	}

	@Override
	public boolean visit(final TypeMethodReference node) {
		return this.visitMethodReference(node);
	}

	@Override
	public boolean visit(final SuperMethodReference node) {
		return this.visitMethodReference(node);
	}

	@Override
	public boolean visit(final CreationReference node) {
		return this.visitMethodReference(node);
	}

	private boolean visitMethodReference(final ASTNode node) {
		final ExpressionInfo reference = new ExpressionInfo(
				ExpressionInfo.CATEGORY.MethodReference,
				this.getStartLineNumber(node), this.getEndLineNumber(node));
		reference.setText(flatten(node));
		this.stack.push(reference);
		return false;
	}

	@Override
	public boolean visit(final ArrayAccess node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.ArrayAccess, startLine, endLine);
		this.stack.push(expression);

		final ProgramElementInfo array = this.visitChild(node.getArray());
		expression.addExpression(array);

		final ProgramElementInfo index = this.visitChild(node.getIndex());
		expression.addExpression(index);

		final StringBuilder text = new StringBuilder();
		text.append(array.getText());
		text.append("[");
		text.append(index.getText());
		text.append("]");
		expression.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ArrayType node) {

		final StringBuilder text = new StringBuilder();
		text.append(node.getElementType().toString());
		for (int i = 0; i < node.getDimensions(); i++) {
			text.append("[]");
		}
		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final TypeInfo type = new TypeInfo(text.toString(), startLine, endLine);
		this.stack.push(type);

		return false;
	}

	@Override
	public boolean visit(final NullLiteral node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ProgramElementInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Null, startLine, endLine);
		expression.setText("null");
		this.stack.push(expression);

		return false;
	}

	@Override
	public boolean visit(final NumberLiteral node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ProgramElementInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Number, startLine, endLine);
		expression.setText(node.getToken());
		this.stack.push(expression);

		return false;
	}

	@Override
	public boolean visit(final PostfixExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo postfixExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Postfix, startLine, endLine);
		this.stack.push(postfixExpression);

		final ProgramElementInfo operand = this.visitChild(node.getOperand());
		postfixExpression.addExpression(operand);

		final OperatorInfo operator = new OperatorInfo(node.getOperator()
				.toString(), startLine, endLine);
		postfixExpression.addExpression(operator);

		final StringBuilder text = new StringBuilder();
		text.append(operand.getText());
		text.append(operator.getText());
		postfixExpression.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final PrefixExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo prefixExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Prefix, startLine, endLine);
		this.stack.push(prefixExpression);

		final OperatorInfo operator = new OperatorInfo(node.getOperator()
				.toString(), startLine, endLine);
		prefixExpression.addExpression(operator);

		final ProgramElementInfo operand = this.visitChild(node.getOperand());
		prefixExpression.addExpression(operand);

		final StringBuilder text = new StringBuilder();
		text.append(operator.getText());
		text.append(operand.getText());
		prefixExpression.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final StringLiteral node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ProgramElementInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.String, startLine, endLine);
		expression.setText("\"" + node.getLiteralValue() + "\"");
		this.stack.push(expression);

		return false;
	}

	@Override
	public boolean visit(final SuperFieldAccess node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo superFieldAccess = new ExpressionInfo(
				ExpressionInfo.CATEGORY.SuperFieldAccess, startLine, endLine);
		this.stack.push(superFieldAccess);

		final ProgramElementInfo name = this.visitChild(node.getName());
		superFieldAccess.addExpression(name);

		final StringBuilder text = new StringBuilder();
		text.append("super.");
		text.append(name.getText());
		superFieldAccess.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final SuperMethodInvocation node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo superMethodInvocation = new ExpressionInfo(
				ExpressionInfo.CATEGORY.SuperMethodInvocation, startLine,
				endLine);
		this.stack.push(superMethodInvocation);

		final ProgramElementInfo name = this.visitChild(node.getName());
		superMethodInvocation.addExpression(name);

		final StringBuilder text = new StringBuilder();
		text.append("super.");
		text.append(name.getText());
		text.append("(");
		final List<ProgramElementInfo> arguments = this.visitChildren(node.arguments());
		arguments.forEach(superMethodInvocation::addExpression);
		text.append(joinTexts(arguments, ","));
		text.append(")");
		superMethodInvocation.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final TypeLiteral node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ProgramElementInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.TypeLiteral, startLine, endLine);
		this.stack.push(expression);

		return false;
	}

	@Override
	public boolean visit(final QualifiedName node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo qualifiedName = new ExpressionInfo(
				ExpressionInfo.CATEGORY.QualifiedName, startLine, endLine);
		this.stack.push(qualifiedName);

		final ProgramElementInfo qualifier = this.visitChild(node.getQualifier());
		qualifiedName.setQualifier(qualifier);

		final ProgramElementInfo name = this.visitChild(node.getName());
		qualifiedName.addExpression(name);

		final StringBuilder text = new StringBuilder();
		text.append(qualifier.getText());
		text.append(".");
		text.append(name.getText());
		qualifiedName.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final SimpleName node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ProgramElementInfo simpleName = new ExpressionInfo(
				ExpressionInfo.CATEGORY.SimpleName, startLine, endLine);
		simpleName.setText(node.getIdentifier());
		this.stack.push(simpleName);

		return false;
	}

	@Override
	public boolean visit(final CharacterLiteral node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ProgramElementInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Character, startLine, endLine);
		expression.setText("\'" + node.charValue() + "\'");
		this.stack.push(expression);

		return false;
	}

	@Override
	public boolean visit(final FieldAccess node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo fieldAccess = new ExpressionInfo(
				ExpressionInfo.CATEGORY.FieldAccess, startLine, endLine);
		this.stack.push(fieldAccess);

		final ProgramElementInfo expression = this.visitChild(node.getExpression());
		fieldAccess.addExpression(expression);

		final ProgramElementInfo name = this.visitChild(node.getName());
		fieldAccess.addExpression(name);

		final StringBuilder text = new StringBuilder();
		text.append(expression.getText());
		text.append(".");
		text.append(name.getText());
		fieldAccess.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final InfixExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo infixExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Infix, startLine, endLine);
		this.stack.push(infixExpression);

		final ProgramElementInfo left = this.visitChild(node.getLeftOperand());
		infixExpression.addExpression(left);

		final OperatorInfo operator = new OperatorInfo(node.getOperator()
				.toString(), startLine, endLine);
		infixExpression.addExpression(operator);

		final ProgramElementInfo right = this.visitChild(node.getRightOperand());
		infixExpression.addExpression(right);

		final StringBuilder text = new StringBuilder();
		text.append(left.getText());
		text.append(" ");
		text.append(operator.getText());
		text.append(" ");
		text.append(right.getText());

		if (node.hasExtendedOperands()) {
			for (final Object operand : node.extendedOperands()) {
				final ProgramElementInfo operandExpression = this.visitChild((ASTNode) operand);
				infixExpression.addExpression(operator);
				infixExpression.addExpression(operandExpression);

				text.append(" ");
				text.append(operator.getText());
				text.append(" ");
				text.append(operandExpression.getText());
			}
		}
		infixExpression.setText(text.toString());

		return false;
	}

	/**
	 * 配列生成。
	 *
	 * <p>子は、配列型、次元式 (0 個以上)、初期化子 (あれば) の順に並べる。
	 * {@code new int[n][]} のように、次元式は型の次元より少ないことがある。
	 * 以前は次元式を訪問しておらず、n のような次元に使われた変数が参照として
	 * 数えられなかった。
	 */
	@Override
	public boolean visit(final ArrayCreation node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo arrayCreation = new ExpressionInfo(
				ExpressionInfo.CATEGORY.ArrayCreation, startLine, endLine);
		this.stack.push(arrayCreation);

		final ArrayType arrayType = node.getType();
		final ProgramElementInfo type = this.visitChild(arrayType);
		arrayCreation.addExpression(type);

		final StringBuilder text = new StringBuilder();
		text.append("new ");
		text.append(arrayType.getElementType().toString());

		// 次元式のある次元にはその式を、残りの次元には空の [] を書く。
		int dimensions = 0;
		for (final Object o : node.dimensions()) {
			final ProgramElementInfo dimension = this.visitChild((ASTNode) o);
			arrayCreation.addExpression(dimension);
			text.append("[");
			text.append(dimension.getText());
			text.append("]");
			dimensions++;
		}
		for (; dimensions < arrayType.getDimensions(); dimensions++) {
			text.append("[]");
		}

		if (null != node.getInitializer()) {
			final ProgramElementInfo initializer = this.visitChild(node.getInitializer());
			arrayCreation.addExpression(initializer);
			text.append(initializer.getText());
		}
		arrayCreation.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ArrayInitializer node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo initializer = new ExpressionInfo(
				ExpressionInfo.CATEGORY.ArrayInitializer, startLine, endLine);
		this.stack.push(initializer);

		final StringBuilder text = new StringBuilder();
		text.append("{");
		final List<ProgramElementInfo> elements = this.visitChildren(node.expressions());
		elements.forEach(initializer::addExpression);
		text.append(joinTexts(elements, ","));
		text.append("}");
		initializer.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final BooleanLiteral node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ProgramElementInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Boolean, startLine, endLine);
		this.stack.push(expression);
		expression.setText(node.toString());

		return false;
	}

	@Override
	public boolean visit(final Assignment node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo assignment = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Assignment, startLine, endLine);
		this.stack.push(assignment);

		final ProgramElementInfo left = this.visitChild(node.getLeftHandSide());
		assignment.addExpression(left);

		final OperatorInfo operator = new OperatorInfo(node.getOperator()
				.toString(), startLine, endLine);
		assignment.addExpression(operator);

		final ProgramElementInfo right = this.visitChild(node.getRightHandSide());
		assignment.addExpression(right);

		final StringBuilder text = new StringBuilder();
		text.append(left.getText());
		text.append(" ");
		text.append(operator.getText());
		text.append(" ");
		text.append(right.getText());
		assignment.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final CastExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo cast = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Cast, startLine, endLine);
		this.stack.push(cast);

		final TypeInfo type = new TypeInfo(node.getType().toString(),
				startLine, endLine);
		cast.addExpression(type);

		final ProgramElementInfo expression = this.visitChild(node.getExpression());
		cast.addExpression(expression);

		final StringBuilder text = new StringBuilder();
		text.append("(");
		text.append(type.getText());
		text.append(")");
		text.append(expression.getText());
		cast.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ClassInstanceCreation node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo classInstanceCreation = new ExpressionInfo(
				ExpressionInfo.CATEGORY.ClassInstanceCreation, startLine,
				endLine);
		this.stack.push(classInstanceCreation);

		final TypeInfo type = new TypeInfo(node.getType().toString(),
				startLine, endLine);
		classInstanceCreation.addExpression(type);

		final StringBuilder text = new StringBuilder();
		text.append("new ");
		text.append(type.getText());
		text.append("(");
		final List<ProgramElementInfo> arguments = this.visitChildren(node.arguments());
		arguments.forEach(classInstanceCreation::addExpression);
		text.append(joinTexts(arguments, ","));
		text.append(")");

		if (null != node.getExpression()) {
			final ProgramElementInfo expression = this.visitChild(node.getExpression());
			classInstanceCreation
					.addExpression(expression);
			text.append(expression.getText());
		}

		if (null != node.getAnonymousClassDeclaration()) {
			final ProgramElementInfo anonymousClass = this.visitChild(node.getAnonymousClassDeclaration());
			classInstanceCreation
					.setAnonymousClassDeclaration((ClassInfo) anonymousClass);
			text.append(anonymousClass.getText());
		}

		classInstanceCreation.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ConditionalExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo trinomial = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Trinomial, startLine, endLine);
		this.stack.push(trinomial);

		final ProgramElementInfo expression = this.visitChild(node.getExpression());
		trinomial.addExpression(expression);

		final ProgramElementInfo thenExpression = this.visitChild(node.getThenExpression());
		trinomial.addExpression(thenExpression);

		final ProgramElementInfo elseExpression = this.visitChild(node.getElseExpression());
		trinomial.addExpression(elseExpression);

		final StringBuilder text = new StringBuilder();
		text.append(expression.getText());
		text.append("? ");
		text.append(thenExpression.getText());
		text.append(": ");
		text.append(elseExpression.getText());
		trinomial.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ConstructorInvocation node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo invocation = new ExpressionInfo(
				ExpressionInfo.CATEGORY.ConstructorInvocation, startLine,
				endLine);
		this.stack.push(invocation);

		final StringBuilder text = new StringBuilder();
		text.append("this(");
		final List<ProgramElementInfo> arguments = this.visitChildren(node.arguments());
		arguments.forEach(invocation::addExpression);
		text.append(joinTexts(arguments, ","));
		text.append(")");
		invocation.setText(text.toString());

		this.stack.pop();
		final ProgramElementInfo ownerBlock = this.stack.peek();
		final SimpleStatementInfo statement = new SimpleStatementInfo(ownerBlock,
				StatementInfo.CATEGORY.Expression, startLine, endLine);
		this.stack.push(statement);

		statement.addExpression(invocation);
		text.append(";");
		statement.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final InstanceofExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo instanceofExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Instanceof, startLine, endLine);
		this.stack.push(instanceofExpression);

		final ProgramElementInfo left = this.visitChild(node.getLeftOperand());
		instanceofExpression.addExpression(left);

		final ProgramElementInfo right = this.visitChild(node.getRightOperand());
		instanceofExpression.addExpression(right);

		final StringBuilder text = new StringBuilder();
		text.append(left.getText());
		text.append(" instanceof ");
		text.append(right.getText());
		instanceofExpression.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final MethodInvocation node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo methodInvocation = new ExpressionInfo(
				ExpressionInfo.CATEGORY.MethodInvocation, startLine, endLine);
		this.stack.push(methodInvocation);

		final StringBuilder text = new StringBuilder();

		if (null != node.getExpression()) {
			final ProgramElementInfo expression = this.visitChild(node.getExpression());
			methodInvocation.setQualifier(expression);

			text.append(expression.getText());
			text.append(".");
		}

		final ProgramElementInfo name = this.visitChild(node.getName());
		methodInvocation.addExpression(name);

		text.append(name.getText());
		text.append("(");
		final List<ProgramElementInfo> arguments = this.visitChildren(node.arguments());
		arguments.forEach(methodInvocation::addExpression);
		text.append(joinTexts(arguments, ","));
		text.append(")");
		methodInvocation.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ParenthesizedExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo parenthesizedExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Parenthesized, startLine, endLine);
		this.stack.push(parenthesizedExpression);

		final ProgramElementInfo expression = this.visitChild(node.getExpression());
		parenthesizedExpression.addExpression(expression);

		final StringBuilder text = new StringBuilder();
		text.append("(");
		text.append(expression.getText());
		text.append(")");
		parenthesizedExpression.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final SuperConstructorInvocation node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo superConstructorInvocation = new ExpressionInfo(
				ExpressionInfo.CATEGORY.SuperConstructorInvocation, startLine,
				endLine);
		this.stack.push(superConstructorInvocation);

		final StringBuilder text = new StringBuilder();

		if (null != node.getExpression()) {
			final ProgramElementInfo qualifier = this.visitChild(node.getExpression());
			superConstructorInvocation.setQualifier(qualifier);
			text.append(qualifier.getText());
			text.append(".super(");
		} else {
			text.append("super(");
		}

		final List<ProgramElementInfo> arguments = this.visitChildren(node.arguments());
		arguments.forEach(superConstructorInvocation::addExpression);
		text.append(joinTexts(arguments, ","));
		text.append(")");
		superConstructorInvocation.setText(text.toString());

		this.stack.pop();
		final ProgramElementInfo ownerBlock = this.stack.peek();
		final SimpleStatementInfo statement = new SimpleStatementInfo(ownerBlock,
				StatementInfo.CATEGORY.Expression, startLine, endLine);
		this.stack.push(statement);

		statement.addExpression(superConstructorInvocation);
		text.append(";");
		statement.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ThisExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ProgramElementInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.This, startLine, endLine);
		this.stack.push(expression);
		expression.setText("this");

		return false;
	}

	/**
	 * 宣言式。for の初期化式と try のリソースに現れる。
	 *
	 * <p>断片が複数ある {@code int i = 0, j = n} は for の初期化式にしか
	 * 現れず、そこでは ForStatement の visit が {@link #declarationExpressions}
	 * で変数ごとに分ける。ここへ来るのは断片が 1 つのものである。
	 */
	@Override
	public boolean visit(final VariableDeclarationExpression node) {

		final List<ExpressionInfo> declarations = this.declarationExpressions(node);
		if (1 == declarations.size()) {
			this.stack.push(declarations.get(0));
			return false;
		}

		// 文法上ここには来ないが、来ても取りこぼさない。変数は子から集まる。
		final ExpressionInfo group = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Unsupported,
				this.getStartLineNumber(node), this.getEndLineNumber(node));
		declarations.forEach(group::addExpression);
		group.setText(flatten(node));
		this.stack.push(group);
		return false;
	}

	/**
	 * 宣言式を変数ごとの式にする。{@code int i = 0, j = n} は
	 * {@code int i = 0} と {@code int j = n} の 2 つになる。
	 */
	List<ExpressionInfo> declarationExpressions(
			final VariableDeclarationExpression node) {
		final List<ExpressionInfo> declarations = new ArrayList<>();
		for (final Object o : node.fragments()) {
			declarations.add(this.declarationExpression(node,
					(VariableDeclarationFragment) o));
		}
		return declarations;
	}

	/** 変数 1 つの宣言式。型は元の宣言のものを使う。 */
	private ExpressionInfo declarationExpression(
			final VariableDeclarationExpression node,
			final VariableDeclarationFragment fragment) {

		final int startLine = this.getStartLineNumber(fragment);
		final int endLine = this.getEndLineNumber(fragment);
		final ExpressionInfo declaration = new ExpressionInfo(
				ExpressionInfo.CATEGORY.VariableDeclarationExpression,
				startLine, endLine);
		this.stack.push(declaration);

		// C 形式の int a[] は int[] a として扱う。
		final TypeInfo type = new TypeInfo(node.getType().toString()
				+ "[]".repeat(fragment.extraDimensions().size()), startLine,
				endLine);
		declaration.addExpression(type);

		final ProgramElementInfo declarator = this.visitChild(fragment);
		declaration.addExpression(declarator);
		declaration.setText(type.getText() + " " + declarator.getText());

		this.stack.pop();
		return declaration;
	}

	@Override
	public boolean visit(final VariableDeclarationFragment node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo vdFragment = new ExpressionInfo(
				ExpressionInfo.CATEGORY.VariableDeclarationFragment, startLine,
				endLine);
		this.stack.push(vdFragment);

		final ProgramElementInfo name = this.visitChild(node.getName());
		vdFragment.addExpression(name);

		final StringBuilder text = new StringBuilder();
		text.append(name.getText());
		// C 形式の int a[] = ... の [] は断片に付いているが、宣言の側が型に
		// 寄せて int[] a にするので、ここでは書かない。

		if (null != node.getInitializer()) {
			final ProgramElementInfo expression = this.visitChild(node.getInitializer());
			vdFragment.addExpression(expression);

			text.append(" = ");
			text.append(expression.getText());
		}

		vdFragment.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final SingleVariableDeclaration node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		// 可変長引数 int... は、JDT では型 int に isVarargs の印が付いた形で
		// 来る。C 形式の int a[] も、型 int と名前の後ろの次元とに分かれて
		// 来る。どちらも型の名前に足し戻す。int a[] は int[] a として扱う。
		final TypeInfo type = new TypeInfo(node.getType().toString()
				+ "[]".repeat(node.extraDimensions().size())
				+ (node.isVarargs() ? "..." : ""), startLine, endLine);
		final String name = node.getName().toString();
		final VariableInfo variable = new VariableInfo(
				VariableInfo.CATEGORY.LOCAL, type, name, startLine, endLine);
		this.stack.push(variable);

		final StringBuilder text = new StringBuilder();
		for (final Object modifier : node.modifiers()) {
			variable.addModifier(modifier.toString());
			text.append(modifier.toString());
			text.append(" ");
		}
		text.append(type.getText());
		text.append(" ");
		text.append(name);
		variable.setText(text.toString());

		return false;
	}
}
