package yoshikihigo.tinypdg.ast;

import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.internal.core.dom.NaiveASTFlattener;

import yoshikihigo.tinypdg.pe.BlockInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.TypeInfo;

public class TinyPDGASTVisitor extends NaiveASTFlattener {

	final private String path;
	final private CompilationUnit root;
	final private List<MethodInfo> methods;
	final private Stack<ProgramElementInfo> stack;

	private boolean inMethod;

	public TinyPDGASTVisitor(final String path, final CompilationUnit root,
			final List<MethodInfo> methods) {
		this.path = path;
		this.root = root;
		this.methods = methods;
		this.stack = new Stack<ProgramElementInfo>();
		this.inMethod = false;
	}

	@Override
	public boolean visit(final MethodDeclaration node) {

		this.inMethod = true;

		final String name = node.getName().getIdentifier();
		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);

		this.stack.push(new MethodInfo(this.path, name, startLine, endLine));

		if (null != node.getBody()) {
			node.getBody().accept(this);
		}

		final ProgramElementInfo method = this.stack.pop();
		this.methods.add((MethodInfo) method);

		this.inMethod = false;

		return false;
	}

	private int getStartLineNumber(final ASTNode node) {
		return root.getLineNumber(node.getStartPosition());
	}

	private int getEndLineNumber(final ASTNode node) {
		if (node instanceof IfStatement) {
			final ASTNode elseStatement = ((IfStatement) node)
					.getElseStatement();
			final int thenEnd = (elseStatement == null) ? node
					.getStartPosition() + node.getLength() : elseStatement
					.getStartPosition() - 1;
			return root.getLineNumber(thenEnd);
		} else if (node instanceof TryStatement) {
			final TryStatement tryStatement = (TryStatement) node;
			int tryEnd = 0;
			for (Object obj : tryStatement.catchClauses()) {
				CatchClause catchClause = (CatchClause) obj;
				tryEnd = catchClause.getStartPosition() - 1;
				break;
			}
			if (tryEnd == 0) {
				final Block finallyBlock = tryStatement.getFinally();
				if (finallyBlock != null) {
					tryEnd = finallyBlock.getStartPosition() - 1;
				}
			}
			if (tryEnd == 0) {
				tryEnd = node.getStartPosition() + node.getLength();
			}
			return root.getLineNumber(tryEnd);
		} else {
			return root.getLineNumber(node.getStartPosition()
					+ node.getLength());
		}
	}

	@Override
	public boolean visit(final AssertStatement node) {

		if (this.inMethod) {

			node.getExpression().accept(this);

			final ProgramElementInfo expression = this.stack.pop();

			node.getMessage().accept(this);

			final ProgramElementInfo message = this.stack.pop();

			final ProgramElementInfo ownerBlock = this.stack.peek();
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);

			final StatementInfo statement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Assert, startLine, endLine);
			((BlockInfo) ownerBlock).addStatement(statement);
		}

		return false;
	}

	@Override
	public boolean visit(final ArrayAccess node) {

		if (this.inMethod) {

			node.getArray().accept(this);

			final ProgramElementInfo array = this.stack.pop();

			node.getIndex().accept(this);

			final ProgramElementInfo index = this.stack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.ArrayAccess, startLine, endLine);
			expression.addExpression((ExpressionInfo) array);
			expression.addExpression((ExpressionInfo) index);
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final ArrayType node) {

		if (this.inMethod) {
			final StringBuffer text = new StringBuffer();
			text.append(node.getElementType().toString());
			for (int i = 0; i < node.getDimensions(); i++) {
				text.append("[]");
			}
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final TypeInfo type = new TypeInfo(text.toString(), startLine,
					endLine);
			this.stack.push(type);
		}

		return false;
	}

	@Override
	public boolean visit(final NullLiteral node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Null, startLine, endLine);
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final NumberLiteral node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Number, startLine, endLine);
			expression.setText(node.getToken());
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final PostfixExpression node) {

		if (this.inMethod) {
			node.getOperand().accept(this);
			final ProgramElementInfo operand = this.stack.pop();
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Postfix, startLine, endLine);
			expression.setText(node.getOperator().toString());
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final PrefixExpression node) {

		if (this.inMethod) {
			node.getOperand().accept(this);
			final ProgramElementInfo operand = this.stack.pop();
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Prefix, startLine, endLine);
			expression.setText(node.getOperator().toString());
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final StringLiteral node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.String, startLine, endLine);
			expression.setText("\"" + node.getLiteralValue() + "\"");
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final SuperFieldAccess node) {

		if (this.inMethod) {
			this.expressionComplexityStack.push(1);
			this.expressionDepthStack.push(1);
			this.expressionTextStack.push("super."
					+ node.getName().getIdentifier());
		}

		return false;
	}

	@Override
	public boolean visit(final SuperMethodInvocation node) {

		if (this.inMethod) {

			int complexity = 1; // ���\�b�h�Ăяo���̕��G�x��1�Ƃ���
			int depth = 1;

			final StringBuilder text = new StringBuilder();
			text.append("super.");
			text.append(node.getName());
			text.append("(");

			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final int argumentComplexity = this.expressionComplexityStack
						.pop();
				complexity += argumentComplexity;
				final int argumentDepth = this.expressionDepthStack.pop();
				if (depth < argumentDepth) {
					depth = argumentDepth;
				}
				final String argumentText = this.expressionTextStack.pop();
				text.append(argumentText);
				text.append(",");
			}
			if (0 < node.arguments().size()) {
				text.deleteCharAt(text.length() - 1);
			}
			text.append(")");

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(depth + 1);
			this.expressionTextStack.push(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final TypeLiteral node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.TypeLiteral, startLine, endLine);
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final QualifiedName node) {

		if (this.inMethod) {
			this.expressionComplexityStack.push(1);
			this.expressionDepthStack.push(1);
			this.expressionTextStack.push(node.getFullyQualifiedName());
		}

		return false;
	}

	@Override
	public boolean visit(final SimpleName node) {

		if (this.inMethod) {
			this.expressionComplexityStack.push(1);
			this.expressionDepthStack.push(1);
			this.expressionTextStack.push(node.getIdentifier());
		}

		return false;
	}

	@Override
	public boolean visit(final CharacterLiteral node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Character, startLine, endLine);
			expression.setText("\'" + node.charValue() + "\'");
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final FieldAccess node) {

		if (this.inMethod) {
			this.expressionComplexityStack.push(1);
			this.expressionDepthStack.push(1);
			this.expressionTextStack.push("this."
					+ node.getName().getIdentifier());
		}

		return false;
	}

	@Override
	public boolean visit(final InfixExpression node) {

		if (this.inMethod) {
			node.getLeftOperand().accept(this);

			final ProgramElementInfo left = this.stack.pop();

			node.getRightOperand().accept(this);

			final ProgramElementInfo right = this.stack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Infix, startLine, endLine);
			expression.setText(node.getOperator().toString());
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final ArrayCreation node) {

		if (this.inMethod && (null != node.getInitializer())) {
			node.getInitializer().accept(this);
			final int initializerComplexity = this.expressionComplexityStack
					.pop();
			final int initializerDepth = this.expressionDepthStack.pop();
			final String initializerText = this.expressionTextStack.pop();

			final int complexity = initializerComplexity + 1;
			final int depth = initializerDepth + 1;

			final StringBuilder text = new StringBuilder();
			text.append("new ");
			text.append(node.getType().toString());
			text.append(initializerText);

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(depth);
			this.expressionTextStack.push(text.toString());

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text.toString(), complexity, depth,
						startLine, endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		else {
			this.expressionComplexityStack.push(1);
			this.expressionDepthStack.push(1);
			this.expressionTextStack.push(node.getType().toString());
		}

		return false;
	}

	@Override
	public boolean visit(final ArrayInitializer node) {

		if (this.inMethod) {
			int complexity = 1; // �z�񏉊�̕��G�x�͂P�ɂ��Ă���
			int depth = 1;
			final StringBuilder text = new StringBuilder();
			text.append("{");
			for (final Object expression : node.expressions()) {
				((ASTNode) expression).accept(this);
				final int expressionComplexity = this.expressionComplexityStack
						.pop();
				complexity += expressionComplexity;
				final int expressionDepth = this.expressionDepthStack.pop();
				if (depth < expressionDepth) {
					depth = expressionDepth;
				}
				final String expressionText = this.expressionTextStack.pop();
				text.append(expressionText);
				text.append(",");
			}
			if (0 < node.expressions().size()) {
				text.deleteCharAt(text.length() - 1);
			}
			text.append("}");

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(++depth);
			this.expressionTextStack.push(text.toString());

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text.toString(), complexity, depth,
						startLine, endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final BooleanLiteral node) {

		if (this.inMethod) {
			this.expressionComplexityStack.push(1);
			this.expressionDepthStack.push(1);
			this.expressionTextStack.push(node.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final Assignment node) {

		if (this.inMethod) {
			node.getLeftHandSide().accept(this);

			final int leftComplexity = this.expressionComplexityStack.pop();
			final int leftDepth = this.expressionDepthStack.pop();
			final String leftText = this.expressionTextStack.pop();

			node.getRightHandSide().accept(this);
			final int rightComplexity = this.expressionComplexityStack.pop();
			final int rightDepth = this.expressionDepthStack.pop();
			final String rightText = this.expressionTextStack.pop();

			final int complexity = leftComplexity + rightComplexity + 1;
			final int depth = leftDepth > rightDepth ? leftDepth + 1
					: rightDepth + 1;
			final String text = leftText + " = " + rightText;

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(depth);
			this.expressionTextStack.push(text);

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text, complexity, depth, startLine,
						endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final CastExpression node) {

		if (this.inMethod) {
			node.getExpression().accept(this);

			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();
			final String expressionText = this.expressionTextStack.pop();

			final int complexity = expressionComplexity + 1;
			final int depth = expressionDepth + 1;
			final StringBuilder text = new StringBuilder();
			text.append("(");
			text.append(node.getType().toString());
			text.append(")");
			text.append(expressionText);

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(depth);
			this.expressionTextStack.push(text.toString());

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text.toString(), complexity, depth,
						startLine, endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final ClassInstanceCreation node) {

		if (this.inMethod) {
			int complexity = 1; // �C���X�^���X�����̕��G�x��1�ɂ��Ă���
			int depth = 1;
			final StringBuilder text = new StringBuilder();
			text.append("new ");
			text.append(node.getType().toString());
			text.append("(");
			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final int argumentComplexity = this.expressionComplexityStack
						.pop();
				complexity += argumentComplexity;
				final int argumentDepth = this.expressionDepthStack.pop();
				if (depth < argumentDepth) {
					depth = argumentDepth;
				}
				final String argumentText = this.expressionTextStack.pop();
				text.append(argumentText);
				text.append(",");
			}
			if (0 < node.arguments().size()) {
				text.deleteCharAt(text.length() - 1);
			}
			text.append(")");

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final int expressionComplexity = this.expressionComplexityStack
						.pop();
				complexity += expressionComplexity;
				final int expressionDepth = this.expressionDepthStack.pop();
				if (depth < expressionDepth) {
					depth = expressionDepth;
				}
				final String expressionText = this.expressionTextStack.pop();
				text.append(expressionText);
			}

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(++depth);
			this.expressionTextStack.push(text.toString());

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text.toString(), complexity, depth,
						startLine, endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final ConditionalExpression node) {

		if (this.inMethod) {
			int complexity = 1; // �O�����Z�q�̕��G�x���P�ɂ��Ă���
			int depth = 1;
			final StringBuilder text = new StringBuilder();

			node.getElseExpression().accept(this);
			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			complexity += expressionComplexity;
			final int expressionDepth = this.expressionDepthStack.pop();
			if (depth < expressionDepth) {
				depth = expressionDepth;
			}
			final String expressionText = this.expressionTextStack.pop();
			text.append(expressionText);
			text.append("?");

			node.getThenExpression().accept(this);
			final int thenExpressionComplexity = this.expressionComplexityStack
					.pop();
			complexity += thenExpressionComplexity;
			final int thenExpressionDepth = this.expressionDepthStack.pop();
			if (depth < thenExpressionDepth) {
				depth = thenExpressionDepth;
			}
			final String thenExpressionText = this.expressionTextStack.pop();
			text.append(thenExpressionText);
			text.append(":");

			node.getElseExpression().accept(this);
			final int elseExpressionComplexity = this.expressionComplexityStack
					.pop();
			complexity += elseExpressionComplexity;
			final int elseExpressionDepth = this.expressionDepthStack.pop();
			if (depth < elseExpressionDepth) {
				depth = elseExpressionDepth;
			}
			final String elseExpressionText = this.expressionTextStack.pop();
			text.append(elseExpressionText);

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(++depth);
			this.expressionTextStack.push(text.toString());

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text.toString(), complexity, depth,
						startLine, endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final ConstructorInvocation node) {

		if (this.inMethod) {
			int complexity = 1; // �R���X�g���N�^�Ăяo���̕��G�x��1�ɂ��Ă���
			int depth = 1;
			final StringBuilder text = new StringBuilder();
			text.append("(");

			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final int argumentComplexity = this.expressionComplexityStack
						.pop();
				complexity += argumentComplexity;
				final int argumentDepth = this.expressionDepthStack.pop();
				if (depth < argumentDepth) {
					depth = argumentDepth;
				}
				final String argumentText = this.expressionTextStack.pop();
				text.append(argumentText);
				text.append(",");
			}
			if (0 < node.arguments().size()) {
				text.deleteCharAt(text.length() - 1);
			}
			text.append(")");

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(++depth);
			this.expressionTextStack.push(text.toString());

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text.toString(), complexity, depth,
						startLine, endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final ExpressionStatement node) {

		if (this.inMethod) {
			node.getExpression().accept(this);
			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.Expression, expressionComplexity,
					expressionDepth, startLine, endLine);
			this.methodStack.peek().add(statement);
		}
		return false;
	}

	@Override
	public boolean visit(final InstanceofExpression node) {

		if (this.inMethod) {
			final StringBuilder text = new StringBuilder();

			node.getLeftOperand().accept(this);
			final int leftComplexity = this.expressionComplexityStack.pop();
			final int leftDepth = this.expressionDepthStack.pop();
			final String leftText = this.expressionTextStack.pop();
			text.append(leftText);

			text.append(" instanceof ");

			node.getRightOperand().accept(this);
			final int rightComplexity = this.expressionComplexityStack.pop();
			final int rightDepth = this.expressionDepthStack.pop();
			final String rightText = this.expressionTextStack.pop();
			text.append(rightText);

			final int complexity = leftComplexity + rightComplexity + 1;
			final int depth = leftDepth > rightDepth ? leftDepth + 1
					: rightDepth + 1;

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(depth);
			this.expressionTextStack.push(text.toString());

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text.toString(), complexity, depth,
						startLine, endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final MethodInvocation node) {

		if (this.inMethod) {
			int complexity = 1;
			int depth = 1;
			final StringBuilder text = new StringBuilder();

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final int expressionComplexity = this.expressionComplexityStack
						.pop();
				complexity += expressionComplexity;
				final int expressionDepth = this.expressionDepthStack.pop();
				if (depth < expressionDepth) {
					depth = expressionDepth;
				}
				final String expressionText = this.expressionTextStack.pop();
				text.append(expressionText);
				text.append(".");
			}

			text.append(node.getName().getIdentifier());
			text.append("(");

			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final int argumentComplexity = this.expressionComplexityStack
						.pop();
				complexity += argumentComplexity;
				final int argumentDepth = this.expressionDepthStack.pop();
				if (depth < argumentDepth) {
					depth = argumentDepth;
				}
				final String argumentText = this.expressionTextStack.pop();
				text.append(argumentText);
				text.append(",");
			}
			if (0 < node.arguments().size()) {
				text.deleteCharAt(text.length() - 1);
			}
			text.append(")");

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(++depth);
			this.expressionTextStack.push(text.toString());

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text.toString(), complexity, depth,
						startLine, endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final ParenthesizedExpression node) {

		if (this.inMethod) {
			node.getExpression().accept(this);
			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();
			final String expressionText = this.expressionTextStack.pop();

			final int complexity = expressionComplexity + 1;
			final int depth = expressionDepth + 1;
			final String text = "(" + expressionText + ")";

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(depth);
			this.expressionTextStack.push(text);

			if (1 < complexity) {
				final int startLine = this.getStartLineNumber(node);
				final int endLine = this.getEndLineNumber(node);
				final int methodID = this.methodStack.peek().id;
				final ExpressionInfo expression = new ExpressionInfo(0,
						methodID, node, text, complexity, depth, startLine,
						endLine);
				this.methodStack.peek().addExpression(expression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final ReturnStatement node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final int complexity = this.expressionComplexityStack.pop();
				final int depth = this.expressionDepthStack.pop();
				final StatementInfo statement = new StatementInfo(methodID,
						StatementInfo.Type.Return, complexity, depth,
						startLine, endLine);
				this.methodStack.peek().add(statement);
			} else {
				final StatementInfo statement = new StatementInfo(methodID,
						StatementInfo.Type.Return, 1, 1, startLine, endLine);
				this.methodStack.peek().add(statement);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final SuperConstructorInvocation node) {

		if (this.inMethod) {
			int complexity = 1; // �X�[�p�[�N���X�̃R�X�g���N�^�Ăяo���̕��G�x��1�ɂ��Ă���
			int depth = 1;

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final int expressionComplexity = this.expressionComplexityStack
						.pop();
				complexity += expressionComplexity;
				final int expressionDepth = this.expressionDepthStack.pop();
				if (depth < expressionDepth) {
					depth = expressionDepth;
				}
			}

			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final int argumentComplexity = this.expressionComplexityStack
						.pop();
				complexity += argumentComplexity;
				final int argumentDepth = this.expressionDepthStack.pop();
				if (depth < argumentDepth) {
					depth = argumentDepth;
				}
			}

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.Expression, complexity, depth,
					startLine, endLine);
			this.methodStack.peek().add(statement);
		}

		return false;
	}

	@Override
	public boolean visit(final ThisExpression node) {

		if (this.inMethod) {
			this.expressionComplexityStack.push(1);
			this.expressionDepthStack.push(1);
			this.expressionTextStack.push("this");
		}

		return false;
	}

	@Override
	public boolean visit(final VariableDeclarationExpression node) {

		if (this.inMethod) {
			int complexity = 1; // �ϐ��錾���̕��G�x��1�ɂ��Ă���
			int depth = 1;

			for (final Object fragment : node.fragments()) {
				((ASTNode) fragment).accept(this);
				final int fragmentComplexity = this.expressionComplexityStack
						.pop();
				complexity += fragmentComplexity;
				final int fragmentDepth = this.expressionDepthStack.pop();
				if (depth < fragmentDepth) {
					depth = fragmentDepth;
				}
			}

			this.expressionComplexityStack.push(complexity);
			this.expressionDepthStack.push(depth + 1);
		}

		return false;
	}

	@Override
	public boolean visit(final VariableDeclarationStatement node) {

		if (this.inMethod) {
			int complexity = 0; // �ϐ��錾���̕��G�x��1�ɂ��Ă���
			int depth = 1;

			for (final Object fragment : node.fragments()) {
				((ASTNode) fragment).accept(this);
				final int fragmentComplexity = this.expressionComplexityStack
						.pop();
				complexity += fragmentComplexity;
				final int fragmentDepth = this.expressionDepthStack.pop();
				if (depth < fragmentDepth) {
					depth = fragmentDepth;
				}
			}

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.VariableDeclaration, complexity, depth,
					startLine, endLine);
			this.methodStack.peek().add(statement);
		}

		return false;
	}

	@Override
	public boolean visit(final DoStatement node) {

		if (this.inMethod) {
			node.getBody().accept(this);

			node.getExpression().accept(this);
			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.Do, expressionComplexity,
					expressionDepth, startLine, endLine);
			this.methodStack.peek().add(statement);
			this.methodStack.peek().increasCyclomatic();
		}

		return false;
	}

	@Override
	public boolean visit(final EnhancedForStatement node) {

		if (this.inMethod) {
			node.getExpression().accept(this);
			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.Foreach, expressionComplexity,
					expressionDepth, startLine, endLine);
			this.methodStack.peek().add(statement);
			this.methodStack.peek().increasCyclomatic();
		}

		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(final ForStatement node) {

		if (this.inMethod) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;

			{
				int complexity = 0;
				int depth = 1;
				for (final Object initializer : node.initializers()) {
					((ASTNode) initializer).accept(this);
					final int initializerComplexity = this.expressionComplexityStack
							.pop();
					complexity += initializerComplexity;
					final int initializerDepth = this.expressionDepthStack
							.pop();
					if (depth < initializerDepth) {
						depth = initializerDepth;
					}
				}

				final StatementInfo statement = new StatementInfo(methodID,
						StatementInfo.Type.ForInitializer, complexity, depth,
						startLine, endLine);
				this.methodStack.peek().add(statement);
			}

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final int expressionComplexity = this.expressionComplexityStack
						.pop();
				final int expressionDepth = this.expressionDepthStack.pop();

				final StatementInfo statement = new StatementInfo(methodID,
						StatementInfo.Type.ForCondition, expressionComplexity,
						expressionDepth, startLine, endLine);
				this.methodStack.peek().add(statement);
			}

			{
				int complexity = 0;
				int depth = 1;
				for (final Object initializer : node.initializers()) {
					((ASTNode) initializer).accept(this);
					final int initializerComplexity = this.expressionComplexityStack
							.pop();
					complexity += initializerComplexity;
					final int initializerDepth = this.expressionDepthStack
							.pop();
					if (depth < initializerDepth) {
						depth = initializerDepth;
					}
				}

				final StatementInfo statement = new StatementInfo(methodID,
						StatementInfo.Type.ForUpdater, complexity, depth,
						startLine, endLine);
				this.methodStack.peek().add(statement);
			}

			this.methodStack.peek().increasCyclomatic();

			node.getBody().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(final IfStatement node) {

		if (this.inMethod) {
			node.getExpression().accept(this);

			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.If, expressionComplexity,
					expressionDepth, startLine, endLine);
			this.methodStack.peek().add(statement);
			this.methodStack.peek().increasCyclomatic();

			if (null != node.getThenStatement()) {
				node.getThenStatement().accept(this);
			}

			if (null != node.getElseStatement()) {
				node.getElseStatement().accept(this);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final SwitchStatement node) {

		if (this.inMethod) {
			node.getExpression().accept(this);
			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();
			final String expressionText = this.expressionTextStack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.Switch, expressionComplexity,
					expressionDepth, startLine, endLine);
			this.methodStack.peek().add(statement);
			this.methodStack.peek().increasCyclomatic();

			for (final Object innerStatement : node.statements()) {
				((ASTNode) innerStatement).accept(this);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final SynchronizedStatement node) {

		if (this.inMethod) {
			node.getExpression().accept(this);

			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.Switch, expressionComplexity,
					expressionDepth, startLine, endLine);
			this.methodStack.peek().add(statement);
		}

		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(final ThrowStatement node) {

		if (this.inMethod) {
			node.getExpression().accept(this);

			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.Throw, expressionComplexity,
					expressionDepth, startLine, endLine);
			this.methodStack.peek().add(statement);
		}

		return false;
	}

	@Override
	public boolean visit(TryStatement node) {
		if (this.inMethod) {
			this.methodStack.peek().increasCyclomatic();
			super.visit(node);
		}
		return false;
	}

	@Override
	public boolean visit(final WhileStatement node) {

		if (this.inMethod) {
			node.getExpression().accept(this);
			final int expressionComplexity = this.expressionComplexityStack
					.pop();
			final int expressionDepth = this.expressionDepthStack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);

			final int methodID = this.methodStack.peek().id;

			final StatementInfo statement = new StatementInfo(methodID,
					StatementInfo.Type.If, expressionComplexity,
					expressionDepth, startLine, endLine);
			this.methodStack.peek().add(statement);
			this.methodStack.peek().increasCyclomatic();

			node.getBody().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(final SwitchCase node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final int methodID = this.methodStack.peek().id;
			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final int expressionComplexity = this.expressionComplexityStack
						.pop();
				final int expressionDepth = this.expressionDepthStack.pop();

				final StatementInfo statement = new StatementInfo(methodID,
						StatementInfo.Type.Case, expressionComplexity,
						expressionDepth, startLine, endLine);
				this.methodStack.peek().add(statement);
				this.methodStack.peek().increasCyclomatic();
			}

			else {
				final StatementInfo statement = new StatementInfo(methodID,
						StatementInfo.Type.Case, 1, 1, startLine, endLine);
				this.methodStack.peek().add(statement);
				this.methodStack.peek().increasCyclomatic();
			}
		}

		return false;
	}

}
