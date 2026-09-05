package yoshikihigo.tinypdg.ast;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;
import yoshikihigo.tinypdg.pe.BlockStatementInfo;
import yoshikihigo.tinypdg.pe.ConditionalStatementInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.ForStatementInfo;
import yoshikihigo.tinypdg.pe.IfStatementInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.SimpleStatementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.TryStatementInfo;
import yoshikihigo.tinypdg.pe.TypeInfo;

/**
 * 文を扱う段。
 */
abstract class StatementVisitor extends ExpressionVisitor {

	StatementVisitor(final String path, final CompilationUnit root,
			final List<MethodInfo> methods) {
		super(path, root, methods);
	}

	@Override
	public boolean visit(final TypeDeclarationStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo statement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.TypeDeclaration, startLine, endLine);
			this.stack.push(statement);

			final ProgramElementInfo typeDeclaration = this.visitChild(node.getDeclaration());
			statement.addExpression(typeDeclaration);

			statement.setText(typeDeclaration.getText());
		}

		return false;
	}

	/** switch 式から値を返す yield 文。return とほぼ同じ形で持つ。 */
	@Override
	public boolean visit(final YieldStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo yieldStatement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Yield, startLine, endLine);
			this.stack.push(yieldStatement);

			final StringBuilder text = new StringBuilder();
			if (null != node.getExpression()) {
				final ProgramElementInfo expression = this.visitChild(node.getExpression());

				if (this.yieldTargets.isEmpty()) {
					yieldStatement.addExpression(expression);
					text.append("yield ");
					text.append(expression.getText());
				} else {
					// 脱糖中。yield expr は一時変数への代入になる。
					// 変数宣言の断片と同じ形にすると、定義が一時変数、参照が
					// expr の中身、という関係がそのまま得られる。
					final String target = this.yieldTargets.peek();
					final ExpressionInfo assignment = new ExpressionInfo(
							ExpressionInfo.CATEGORY.VariableDeclarationFragment,
							startLine, endLine);
					final ExpressionInfo name = new ExpressionInfo(
							ExpressionInfo.CATEGORY.SimpleName, startLine, endLine);
					name.setText(target);
					assignment.addExpression(name);
					assignment.addExpression(expression);
					assignment.setText(target + " = " + expression.getText());

					yieldStatement.setCategory(StatementInfo.CATEGORY.Expression);
					yieldStatement.addExpression(assignment);
					text.append(assignment.getText());
					this.yieldConverted = true;
				}
			} else {
				text.append("yield");
			}
			text.append(";");
			yieldStatement.setText(text.toString());
		}

		return false;
	}

	/**
	 * {@code this(...)}。JDT ではこれは文で、コンストラクタ本体の先頭にしか
	 * 現れない。呼び出しの式を作り、それを 1 つ持つ式文に包む。
	 *
	 * <p>以前は式の段にあり、式の visit の中で文を作っていた。
	 */
	@Override
	public boolean visit(final ConstructorInvocation node) {

		if (this.inBlock()) {

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
			this.pushInvocationStatement(invocation, startLine, endLine);
		}

		return false;
	}

	/** {@code super(...)}。{@code this(...)} と同じ扱い。 */
	@Override
	public boolean visit(final SuperConstructorInvocation node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo invocation = new ExpressionInfo(
					ExpressionInfo.CATEGORY.SuperConstructorInvocation, startLine,
					endLine);
			this.stack.push(invocation);

			final StringBuilder text = new StringBuilder();

			if (null != node.getExpression()) {
				final ProgramElementInfo qualifier = this.visitChild(node.getExpression());
				invocation.setQualifier(qualifier);
				text.append(qualifier.getText());
				text.append(".super(");
			} else {
				text.append("super(");
			}

			final List<ProgramElementInfo> arguments = this.visitChildren(node.arguments());
			arguments.forEach(invocation::addExpression);
			text.append(joinTexts(arguments, ","));
			text.append(")");
			invocation.setText(text.toString());

			this.stack.pop();
			this.pushInvocationStatement(invocation, startLine, endLine);
		}

		return false;
	}

	/** コンストラクタ呼び出しの式を、それ 1 つを持つ式文に包んで積む。 */
	private void pushInvocationStatement(final ExpressionInfo invocation,
			final int startLine, final int endLine) {
		final ProgramElementInfo ownerBlock = this.stack.peek();
		final SimpleStatementInfo statement = new SimpleStatementInfo(ownerBlock,
				StatementInfo.CATEGORY.Expression, startLine, endLine);
		statement.addExpression(invocation);
		statement.setText(invocation.getText() + ";");
		this.stack.push(statement);
	}

	@Override
	public boolean visit(final AssertStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo statement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Assert, startLine, endLine);
			this.stack.push(statement);

			final ProgramElementInfo expression = this.visitChild(node.getExpression());
			statement.addExpression(expression);

			final StringBuilder text = new StringBuilder();
			text.append("assert ");
			text.append(expression.getText());

			// メッセージは省略できる。省略されていれば getMessage() は null を返す。
			if (null != node.getMessage()) {
				final ProgramElementInfo message = this.visitChild(node.getMessage());
				statement.addExpression(message);
				text.append(" : ");
				text.append(message.getText());
			}

			text.append(";");
			statement.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final ExpressionStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo statement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Expression, startLine, endLine);
			this.stack.push(statement);

			final ProgramElementInfo expression = this.visitChild(node.getExpression());
			statement.addExpression(expression);

			final StringBuilder text = new StringBuilder();
			text.append(expression.getText());
			text.append(";");
			statement.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final ReturnStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo returnStatement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Return, startLine, endLine);
			this.stack.push(returnStatement);

			final StringBuilder text = new StringBuilder();
			text.append("return");

			if (null != node.getExpression()) {
				final ProgramElementInfo expression = this.visitChild(node.getExpression());
				returnStatement.addExpression(expression);
				text.append(" ");
				text.append(expression.getText());
			}

			text.append(";");
			returnStatement.setText(text.toString());
		}

		return false;
	}

	/**
	 * 変数宣言文。
	 *
	 * <p>{@code int a = x, b = a + 1;} のように 1 文で複数の変数を宣言して
	 * いれば、変数ごとの文 {@code int a = x; int b = a + 1;} に分ける。意味は
	 * 同じで、分けると b の初期化子が a を読むという依存が見えるようになる。
	 * 複数の文は SimpleBlock に包んで積み、文の並びを受け取る側が中身を
	 * 並べる。
	 */
	@Override
	public boolean visit(final VariableDeclarationStatement node) {

		if (this.inBlock()) {

			final ProgramElementInfo ownerBlock = this.stack.peek();
			final List<?> fragments = node.fragments();

			if (1 == fragments.size()) {
				this.stack.push(this.declaration(ownerBlock, node,
						(VariableDeclarationFragment) fragments.get(0),
						this.getStartLineNumber(node),
						this.getEndLineNumber(node)));
				return false;
			}

			final BlockStatementInfo group = new BlockStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.SimpleBlock,
					this.getStartLineNumber(node), this.getEndLineNumber(node));
			final StringBuilder text = new StringBuilder();
			for (final Object o : fragments) {
				final VariableDeclarationFragment fragment = (VariableDeclarationFragment) o;
				final SimpleStatementInfo declaration = this.declaration(
						ownerBlock, node, fragment,
						this.getStartLineNumber(fragment),
						this.getEndLineNumber(fragment));
				group.addStatement(declaration);
				text.append(declaration.getText());
				text.append(System.lineSeparator());
			}
			group.setText(text.toString());
			this.stack.push(group);
		}

		return false;
	}

	/** 変数 1 つの宣言文。修飾子と型は元の文のものを使う。 */
	private SimpleStatementInfo declaration(final ProgramElementInfo ownerBlock,
			final VariableDeclarationStatement node,
			final VariableDeclarationFragment fragment, final int startLine,
			final int endLine) {

		final SimpleStatementInfo statement = new SimpleStatementInfo(ownerBlock,
				StatementInfo.CATEGORY.VariableDeclaration, startLine, endLine);
		this.stack.push(statement);

		final StringBuilder text = new StringBuilder();
		for (final Object modifier : node.modifiers()) {
			text.append(modifier.toString());
			text.append(" ");
		}

		// C 形式の int a[] は int[] a として扱う。
		final ProgramElementInfo type = new TypeInfo(node.getType().toString()
				+ "[]".repeat(fragment.extraDimensions().size()), startLine,
				endLine);
		statement.addExpression(type);
		text.append(type.getText());
		text.append(" ");

		final ProgramElementInfo declarator = this.visitChild(fragment);
		statement.addExpression(declarator);
		text.append(declarator.getText());
		text.append(";");
		statement.setText(text.toString());

		this.stack.pop();
		return statement;
	}

	@Override
	public boolean visit(final DoStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final ConditionalStatementInfo doBlock = new ConditionalStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Do, startLine, endLine);
			this.stack.push(doBlock);

			final StatementInfo body = (StatementInfo) this.visitChild(node.getBody());
			doBlock.setStatement(body);

			final ProgramElementInfo condition = this.visitChild(node.getExpression());
			doBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(doBlock);

			final StringBuilder text = new StringBuilder();
			text.append("do ");
			text.append(body.getText());
			text.append(" while (");
			text.append(condition.getText());
			text.append(");");
			doBlock.setText(text.toString());
		}

		return false;
	}

	/**
	 * foreach。while と同じ形で、条件式の位置にヘッダ {@code T x : expr} が
	 * 入る。ヘッダは反復のたびに x を定義し expr を参照する 1 個の式である。
	 */
	@Override
	public boolean visit(final EnhancedForStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final ConditionalStatementInfo foreachBlock = new ConditionalStatementInfo(
					ownerBlock, StatementInfo.CATEGORY.Foreach, startLine,
					endLine);
			this.stack.push(foreachBlock);

			final ProgramElementInfo parameter = this.visitChild(node.getParameter());
			final ProgramElementInfo expression = this.visitChild(node.getExpression());
			final ExpressionInfo header = new ExpressionInfo(
					ExpressionInfo.CATEGORY.ForeachHeader, parameter.startLine,
					expression.endLine);
			header.addExpression(parameter);
			header.addExpression(expression);
			header.setText(parameter.getText() + " : " + expression.getText());
			foreachBlock.setCondition(header);
			header.setOwnerConditinalBlock(foreachBlock);

			final StatementInfo body = (StatementInfo) this.visitChild(node.getBody());
			foreachBlock.setStatement(body);

			final StringBuilder text = new StringBuilder();
			text.append("for (");
			text.append(header.getText());
			text.append(")");
			text.append(body.getText());
			foreachBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final ForStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final ForStatementInfo forBlock = new ForStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.For, startLine, endLine);
			this.stack.push(forBlock);

			final StringBuilder text = new StringBuilder();
			text.append("for (");

			// for (int i = 0, j = n; ...) の初期化式は変数ごとに分ける。
			final List<ProgramElementInfo> initializers = new ArrayList<>();
			for (final Object o : node.initializers()) {
				if (o instanceof VariableDeclarationExpression declaration) {
					initializers.addAll(this.declarationExpressions(declaration));
				} else {
					initializers.add(this.visitChild((ASTNode) o));
				}
			}
			initializers.forEach(forBlock::addInitializer);
			text.append(joinTexts(initializers, ","));

			text.append("; ");

			if (null != node.getExpression()) {
				final ProgramElementInfo condition = this.visitChild(node.getExpression());
				forBlock.setCondition(condition);
				condition.setOwnerConditinalBlock(forBlock);
				text.append(condition.getText());
			}

			text.append("; ");

			final List<ProgramElementInfo> updaters = this.visitChildren(node.updaters());
			updaters.forEach(forBlock::addUpdater);
			text.append(joinTexts(updaters, ","));

			text.append(")");

			final StatementInfo body = (StatementInfo) this.visitChild(node.getBody());
			forBlock.setStatement(body);
			text.append(body.getText());
			forBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final IfStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final IfStatementInfo ifBlock = new IfStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.If, startLine, endLine);
			this.stack.push(ifBlock);

			final ProgramElementInfo condition = this.visitChild(node.getExpression());
			ifBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(ifBlock);

			final StringBuilder text = new StringBuilder();
			text.append("if (");
			text.append(condition.getText());
			text.append(") ");

			if (null != node.getThenStatement()) {
				final StatementInfo thenBody = (StatementInfo) this.visitChild(node.getThenStatement());
				ifBlock.setStatement(thenBody);
				text.append(thenBody.getText());
			}

			if (null != node.getElseStatement()) {
				final StatementInfo elseBody = (StatementInfo) this.visitChild(node.getElseStatement());
				ifBlock.setElseStatement(elseBody);
				text.append(elseBody.getText());
			}

			ifBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final SwitchStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final ConditionalStatementInfo switchBlock = new ConditionalStatementInfo(ownerBlock,
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

			for (final Object o : node.statements()) {
				final StatementInfo statement = (StatementInfo) this.visitChild((ASTNode) o);
				// 複数の変数を宣言する文は変数ごとの文に分かれ、SimpleBlock に
				// 包まれて届く。中身を並べる。
				for (final StatementInfo inner : BlockStatementInfo.flatten(statement)) {
					inner.setOwnerBlock(switchBlock);
					switchBlock.addStatement(inner);
					text.append(inner.getText());
					text.append(System.lineSeparator());
				}
			}

			switchBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final SynchronizedStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final ConditionalStatementInfo synchronizedBlock = new ConditionalStatementInfo(
					ownerBlock, StatementInfo.CATEGORY.Synchronized, startLine,
					endLine);
			this.stack.push(synchronizedBlock);

			final ProgramElementInfo condition = this.visitChild(node.getExpression());
			synchronizedBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(synchronizedBlock);

			final StatementInfo body = (StatementInfo) this.visitChild(node.getBody());
			synchronizedBlock.setStatement(body);

			final StringBuilder text = new StringBuilder();
			text.append("synchronized (");
			text.append(condition.getText());
			text.append(") ");
			text.append(body.getText());
			synchronizedBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final ThrowStatement node) {

		if (this.inBlock()) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo throwStatement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Throw, startLine, endLine);
			this.stack.push(throwStatement);

			final ProgramElementInfo expression = this.visitChild(node.getExpression());
			throwStatement.addExpression(expression);

			final StringBuilder text = new StringBuilder();
			text.append("throw ");
			text.append(expression.getText());
			text.append(";");
			throwStatement.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final TryStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final TryStatementInfo tryBlock = new TryStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Try, startLine, endLine);
			this.stack.push(tryBlock);

			// try-with-resources のリソース。JLS 上、リソースは try 本体を
			// 囲む暗黙のブロックでの変数宣言と定義されている。その形どおりに
			// 宣言文へ組み替えてブロックの先頭に並べると、CFG のノードとして
			// 現れ、本体からのデータ依存もそのまま繋がる。
			final BlockStatementInfo resourceBlock = node.resources().isEmpty() ? null
					: new BlockStatementInfo(tryBlock,
							StatementInfo.CATEGORY.SimpleBlock, startLine, endLine);

			final List<StatementInfo> resources = new ArrayList<>();
			final StringBuilder resourceText = new StringBuilder();
			for (final Object o : node.resources()) {
				final ProgramElementInfo resource = this.visitChild((ASTNode) o);

				final SimpleStatementInfo declaration = new SimpleStatementInfo(resourceBlock,
						StatementInfo.CATEGORY.VariableDeclaration,
						resource.startLine, resource.endLine);
				declaration.addExpression(resource);
				declaration.setText(resource.getText() + ";");
				resources.add(declaration);

				resourceText.append(0 == resourceText.length() ? "(" : "; ");
				resourceText.append(resource.getText());
			}
			if (0 < resourceText.length()) {
				resourceText.append(") ");
			}

			final BlockStatementInfo body = (BlockStatementInfo) this.visitChild(node.getBody());

			final StatementInfo effectiveBody;
			if (null == resourceBlock) {
				effectiveBody = body;
			} else {
				final StringBuilder blockText = new StringBuilder();
				blockText.append("{");
				blockText.append(System.lineSeparator());
				for (final StatementInfo declaration : resources) {
					resourceBlock.addStatement(declaration);
					blockText.append(declaration.getText());
					blockText.append(System.lineSeparator());
				}
				// 本体ブロックをそのまま入れ子にすると、CFG が中身を展開せず
				// 1 個の不透明なノードにしてしまう。文を取り出して並べる。
				resourceBlock.addStatements(body.getStatements());
				for (final StatementInfo statement : body.getStatements()) {
					blockText.append(statement.getText());
					blockText.append(System.lineSeparator());
				}
				blockText.append("}");
				resourceBlock.setText(blockText.toString());
				effectiveBody = resourceBlock;
			}
			tryBlock.setStatement(effectiveBody);

			final StringBuilder text = new StringBuilder();
			text.append("try ");
			text.append(resourceText);
			text.append(effectiveBody.getText());

			for (final Object o : node.catchClauses()) {
				final StatementInfo catchBlock = (StatementInfo) this.visitChild((ASTNode) o);
				tryBlock.addCatchStatement(catchBlock);
				text.append(catchBlock.getText());
			}

			if (null != node.getFinally()) {
				final StatementInfo finallyBlock = (StatementInfo) this.visitChild(node.getFinally());
				tryBlock.setFinallyStatement(finallyBlock);
				text.append(finallyBlock.getText());
			}

			tryBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final WhileStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final ConditionalStatementInfo whileBlock = new ConditionalStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.While, startLine, endLine);
			this.stack.push(whileBlock);

			final ProgramElementInfo condition = this.visitChild(node.getExpression());
			whileBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(whileBlock);

			final StatementInfo body = (StatementInfo) this.visitChild(node.getBody());
			whileBlock.setStatement(body);

			final StringBuilder text = new StringBuilder();
			text.append("while (");
			text.append(condition.getText());
			text.append(") ");
			text.append(body.getText());
			whileBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final SwitchCase node) {

		if (this.inBlock()) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo switchCase = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Case, startLine, endLine);
			this.stack.push(switchCase);

			final StringBuilder text = new StringBuilder();

			// JLS14 以降、switch ラベルは複数の式を持ちうる (case 1, 2, 3:)。
			// 旧 API の getExpression() は JLS14 以降の AST では実際のラベルを
			// 返さず、空の SimpleName を遅延生成して返してしまうため使えない。
			final List<?> expressions = node.expressions();
			if (expressions.isEmpty()) {
				text.append("default");
			} else {
				text.append("case ");
				final List<ProgramElementInfo> labels = this.visitChildren(expressions);
				labels.forEach(switchCase::addExpression);
				text.append(joinTexts(labels, ", "));
			}

			// case X -> ... の矢印形式か、従来の case X: 形式か。
			text.append(node.isSwitchLabeledRule() ? " ->" : ":");
			switchCase.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final BreakStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo breakStatement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Break, startLine, endLine);
			this.stack.push(breakStatement);

			final StringBuilder text = new StringBuilder();
			text.append("break");

			if (null != node.getLabel()) {
				final ProgramElementInfo label = this.visitChild(node.getLabel());
				breakStatement.addExpression(label);

				text.append(" ");
				text.append(label.getText());
			}

			text.append(";");
			breakStatement.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final ContinueStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo continuekStatement = new SimpleStatementInfo(
					ownerBlock, StatementInfo.CATEGORY.Continue, startLine,
					endLine);
			this.stack.push(continuekStatement);

			final StringBuilder text = new StringBuilder();
			text.append("continue");

			if (null != node.getLabel()) {
				final ProgramElementInfo label = this.visitChild(node.getLabel());
				continuekStatement.addExpression(label);

				text.append(" ");
				text.append(label.getText());
			}

			text.append(";");
			continuekStatement.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final LabeledStatement node) {

		node.getBody().accept(this);
		final StatementInfo statement = (StatementInfo) this.stack.peek();

		final String label = node.getLabel().toString();
		statement.setLabel(label);

		return false;
	}

	@Override
	public boolean visit(final Block node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final BlockStatementInfo simpleBlock = new BlockStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.SimpleBlock, startLine, endLine);
			this.stack.push(simpleBlock);

			final StringBuilder text = new StringBuilder();
			text.append("{");
			text.append(System.lineSeparator());

			for (final Object o : node.statements()) {
				final ProgramElementInfo statement = this.visitChild((ASTNode) o);

				// switch 式の脱糖で生じた文を、元の文の前に置く。
				for (final StatementInfo pending : this.drainPendingStatements()) {
					pending.setOwnerBlock(simpleBlock);
					simpleBlock.addStatement(pending);
					text.append(pending.getText());
					text.append(System.lineSeparator());
				}

				// 複数の変数を宣言する文は変数ごとの文に分かれ、SimpleBlock に
				// 包まれて届く。裸のブロック { ... } も同じ形である。どちらも
				// 中身を並べる。入れ子のままだと CFG が中身を展開せず、1 個の
				// 不透明なノードにしてしまう。
				for (final StatementInfo inner : BlockStatementInfo
						.flatten((StatementInfo) statement)) {
					inner.setOwnerBlock(simpleBlock);
					simpleBlock.addStatement(inner);
					text.append(inner.getText());
					text.append(System.lineSeparator());
				}
			}

			text.append("}");
			simpleBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final CatchClause node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final ConditionalStatementInfo catchBlock = new ConditionalStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Catch, startLine, endLine);
			this.stack.push(catchBlock);

			final ProgramElementInfo exception = this.visitChild(node.getException());
			exception.setOwnerConditinalBlock(catchBlock);
			catchBlock.setCondition(exception);

			final StatementInfo body = (StatementInfo) this.visitChild(node.getBody());
			catchBlock.setStatement(body);

			final StringBuilder text = new StringBuilder();
			text.append("catch (");
			text.append(exception.getText());
			text.append(") ");
			text.append(body.getText());
			catchBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final EmptyStatement node) {

		if (this.inBlock()) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final SimpleStatementInfo emptyStatement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Empty, startLine, endLine);
			this.stack.push(emptyStatement);
			emptyStatement.setText(";");
		}

		return false;
	}
}
