package yoshikihigo.tinypdg.ast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
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
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
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

	static public CompilationUnit createAST(final File file) {

		final String lineSeparator = System.getProperty("line.separator");
		final StringBuffer text = new StringBuffer();
		final BufferedReader reader;

		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(file), "JISAutoDetect"));

			while (reader.ready()) {
				final String line = reader.readLine();
				text.append(line);
				text.append(lineSeparator);
			}
			reader.close();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		final ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setSource(text.toString().toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

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

		final MethodInfo method = new MethodInfo(this.path, name, startLine,
				endLine);
		this.stack.push(method);

		if (null != node.getBody()) {
			node.getBody().accept(this);
		}

		this.stack.pop();

		this.methods.add(method);

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
			statement.addExpression((ExpressionInfo) expression);
			statement.addExpression((ExpressionInfo) message);
			((BlockInfo) ownerBlock).addStatement(statement);
		}

		return false;
	}

	@Override
	public boolean visit(final ArrayAccess node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.ArrayAccess, startLine, endLine);

			node.getArray().accept(this);
			final ProgramElementInfo array = this.stack.pop();
			expression.addExpression((ExpressionInfo) array);

			node.getIndex().accept(this);
			final ProgramElementInfo index = this.stack.pop();
			expression.addExpression((ExpressionInfo) index);

			final StringBuilder text = new StringBuilder();
			text.append(array.getText());
			text.append("[");
			text.append(index.getText());
			text.append("]");
			expression.setText(text.toString());

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
			final ExpressionInfo postfixExpression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Postfix, startLine, endLine);
			postfixExpression.addExpression((ExpressionInfo) operand);

			final StringBuilder text = new StringBuilder();
			text.append(operand.getText());
			text.append(node.getOperator().toString());
			postfixExpression.setText(text.toString());

			this.stack.push(postfixExpression);
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
			final ExpressionInfo prefixExpression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Prefix, startLine, endLine);
			prefixExpression.addExpression((ExpressionInfo) operand);

			final StringBuilder text = new StringBuilder();
			text.append(node.getOperator().toString());
			text.append(operand.getText());
			prefixExpression.setText(text.toString());

			this.stack.push(prefixExpression);
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
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo superFieldAccess = new ExpressionInfo(
					ExpressionInfo.CATEGORY.SuperFieldAccess, startLine,
					endLine);
			superFieldAccess.setText("super." + node.getNodeType());
			this.stack.push(superFieldAccess);
		}

		return false;
	}

	@Override
	public boolean visit(final SuperMethodInvocation node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo superMethodInvocation = new ExpressionInfo(
					ExpressionInfo.CATEGORY.SuperMethodInvocation, startLine,
					endLine);
			superMethodInvocation.setText("super." + node.getName());

			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final ProgramElementInfo argumentExpression = this.stack.pop();
				superMethodInvocation
						.addExpression((ExpressionInfo) argumentExpression);
			}
			this.stack.push(superMethodInvocation);
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
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo qualifiedName = new ExpressionInfo(
					ExpressionInfo.CATEGORY.QualifiedName, startLine, endLine);
			qualifiedName.setText(node.getFullyQualifiedName());
			this.stack.push(qualifiedName);
		}

		return false;
	}

	@Override
	public boolean visit(final SimpleName node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo simpleName = new ExpressionInfo(
					ExpressionInfo.CATEGORY.SimpleName, startLine, endLine);
			simpleName.setText(node.getIdentifier());
			this.stack.push(simpleName);
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
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo fieldAccess = new ExpressionInfo(
					ExpressionInfo.CATEGORY.FieldAccess, startLine, endLine);

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				fieldAccess.addExpression((ExpressionInfo) expression);
			} else {
				fieldAccess.setText(node.getName().toString());
			}

			this.stack.push(fieldAccess);
		}

		return false;
	}

	@Override
	public boolean visit(final InfixExpression node) {

		if (this.inMethod) {

			final StringBuilder text = new StringBuilder();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo infixExpression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Infix, startLine, endLine);

			node.getLeftOperand().accept(this);
			final ProgramElementInfo left = this.stack.pop();
			infixExpression.addExpression((ExpressionInfo) left);
			text.append(left.getText());

			text.append(" ");
			text.append(node.getOperator().toString());
			text.append(" ");

			node.getRightOperand().accept(this);
			final ProgramElementInfo right = this.stack.pop();
			infixExpression.addExpression((ExpressionInfo) right);
			text.append(right.getText());

			infixExpression.setText(text.toString());
			this.stack.push(infixExpression);
		}

		return false;
	}

	@Override
	public boolean visit(final ArrayCreation node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo arrayCreation = new ExpressionInfo(
					ExpressionInfo.CATEGORY.ArrayCreation, startLine, endLine);
			arrayCreation.setText(node.getType().toString());

			if (null != node.getInitializer()) {
				node.getInitializer().accept(this);
				final ProgramElementInfo initializer = this.stack.pop();
				arrayCreation.addExpression((ExpressionInfo) initializer);
			}

			this.stack.push(arrayCreation);
		}

		return false;
	}

	@Override
	public boolean visit(final ArrayInitializer node) {

		if (this.inMethod) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo initializer = new ExpressionInfo(
					ExpressionInfo.CATEGORY.ArrayInitializer, startLine,
					endLine);
			for (final Object expression : node.expressions()) {
				((ASTNode) expression).accept(this);
				final ProgramElementInfo subexpression = this.stack.pop();
				initializer.addExpression((ExpressionInfo) subexpression);
			}
			this.stack.push(initializer);
		}

		return false;
	}

	@Override
	public boolean visit(final BooleanLiteral node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Boolean, startLine, endLine);
			expression.setText(node.toString());
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final Assignment node) {

		if (this.inMethod) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo assignment = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Assignment, startLine, endLine);

			node.getLeftHandSide().accept(this);
			final ProgramElementInfo left = this.stack.pop();
			assignment.addExpression((ExpressionInfo) left);

			node.getRightHandSide().accept(this);
			final ProgramElementInfo right = this.stack.pop();
			assignment.addExpression((ExpressionInfo) right);

			final StringBuilder text = new StringBuilder();
			text.append(left.getText());
			text.append(" = ");
			text.append(right.getText());
			assignment.setText(text.toString());

			this.stack.push(assignment);
		}

		return false;
	}

	@Override
	public boolean visit(final CastExpression node) {

		if (this.inMethod) {
			node.getExpression().accept(this);

			final ProgramElementInfo expression = this.stack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo cast = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Cast, startLine, endLine);
			cast.setText(node.getType().toString());
			cast.addExpression((ExpressionInfo) expression);
			this.stack.push(cast);
		}

		return false;
	}

	@Override
	public boolean visit(final ClassInstanceCreation node) {

		if (this.inMethod) {
			final StringBuilder text = new StringBuilder();
			text.append("new ");

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo classInstanceCreation = new ExpressionInfo(
					ExpressionInfo.CATEGORY.ClassInstanceCreation, startLine,
					endLine);

			text.append(node.getType().toString());

			text.append("(");
			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final ProgramElementInfo argumentExpression = this.stack.pop();
				classInstanceCreation
						.addExpression((ExpressionInfo) argumentExpression);

				text.append(argumentExpression.getText());
				text.append(",");
			}
			if (0 < node.arguments().size()) {
				text.deleteCharAt(text.length() - 1);
			}
			text.append(")");

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				classInstanceCreation
						.addExpression((ExpressionInfo) expression);
			}

			classInstanceCreation.setText(text.toString());
			this.stack.push(classInstanceCreation);
		}

		return false;
	}

	@Override
	public boolean visit(final ConditionalExpression node) {

		if (this.inMethod) {

			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack.pop();

			node.getThenExpression().accept(this);
			final ProgramElementInfo thenExpression = this.stack.pop();

			node.getElseExpression().accept(this);
			final ProgramElementInfo elseExpression = this.stack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo trinomial = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Trinomial, startLine, endLine);
			trinomial.addExpression((ExpressionInfo) expression);
			trinomial.addExpression((ExpressionInfo) thenExpression);
			trinomial.addExpression((ExpressionInfo) elseExpression);
			this.stack.push(trinomial);
		}

		return false;
	}

	@Override
	public boolean visit(final ConstructorInvocation node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo invocation = new ExpressionInfo(
					ExpressionInfo.CATEGORY.ConstructorInvocation, startLine,
					endLine);
			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final ProgramElementInfo argumentExpression = this.stack.pop();
				invocation.addExpression((ExpressionInfo) argumentExpression);
			}
			this.stack.push(invocation);
		}

		return false;
	}

	@Override
	public boolean visit(final ExpressionStatement node) {

		if (this.inMethod) {
			final StringBuilder text = new StringBuilder();

			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack.pop();
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final StatementInfo statement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Expression, startLine, endLine);
			statement.addExpression((ExpressionInfo) expression);

			text.append(expression.getText());
			text.append(";");

			statement.setText(text.toString());
			((BlockInfo) ownerBlock).addStatement(statement);
		}
		return false;
	}

	@Override
	public boolean visit(final InstanceofExpression node) {

		if (this.inMethod) {
			node.getLeftOperand().accept(this);

			final ProgramElementInfo left = this.stack.pop();

			node.getRightOperand().accept(this);

			final ProgramElementInfo right = this.stack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);

			final ExpressionInfo instanceofExpression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Instanceof, startLine, endLine);
			instanceofExpression.addExpression((ExpressionInfo) left);
			instanceofExpression.addExpression((ExpressionInfo) right);
			this.stack.push(instanceofExpression);
		}

		return false;
	}

	@Override
	public boolean visit(final MethodInvocation node) {

		if (this.inMethod) {

			final StringBuilder text = new StringBuilder();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo methodInvocation = new ExpressionInfo(
					ExpressionInfo.CATEGORY.MethodInvocation, startLine,
					endLine);

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				methodInvocation.addExpression((ExpressionInfo) expression);

				text.append(expression.getText());
				text.append(".");
			}
			text.append(node.getName());
			text.append("(");
			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final ProgramElementInfo argumentExpression = this.stack.pop();
				methodInvocation
						.addExpression((ExpressionInfo) argumentExpression);

				text.append(argumentExpression.getText());
				text.append(",");
			}
			if (0 < node.arguments().size()) {
				text.deleteCharAt(text.length() - 1);
			}
			text.append(")");

			methodInvocation.setText(text.toString());
			this.stack.push(methodInvocation);
		}

		return false;
	}

	@Override
	public boolean visit(final ParenthesizedExpression node) {

		if (this.inMethod) {
			node.getExpression().accept(this);

			final ProgramElementInfo expression = this.stack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);

			final ExpressionInfo parenthesizedExpression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Parenthesized, startLine, endLine);
			parenthesizedExpression.addExpression((ExpressionInfo) expression);
			this.stack.push(parenthesizedExpression);
		}

		return false;
	}

	@Override
	public boolean visit(final ReturnStatement node) {

		if (this.inMethod) {
			final StringBuilder text = new StringBuilder();
			text.append("return");

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo returnStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Return, startLine, endLine);

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				returnStatement.addExpression((ExpressionInfo) expression);
				text.append(" ");
				text.append(expression.getText());
			}

			text.append(";");
			returnStatement.setText(text.toString());
			((BlockInfo) ownerBlock).addStatement(returnStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final SuperConstructorInvocation node) {

		if (this.inMethod) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo superConstructorInvocation = new ExpressionInfo(
					ExpressionInfo.CATEGORY.SuperConstructorInvocation,
					startLine, endLine);

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				superConstructorInvocation
						.addExpression((ExpressionInfo) expression);
				superConstructorInvocation.setText(".super");
			} else {
				superConstructorInvocation.setText("super");
			}

			for (final Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
				final ProgramElementInfo argumentExpression = this.stack.pop();
				superConstructorInvocation
						.addExpression((ExpressionInfo) argumentExpression);
			}
		}

		return false;
	}

	@Override
	public boolean visit(final ThisExpression node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.This, startLine, endLine);
			this.stack.push(expression);
		}

		return false;
	}

	@Override
	public boolean visit(final VariableDeclarationExpression node) {

		if (this.inMethod) {

			final StringBuilder text = new StringBuilder();
			text.append(node.getType().toString());
			text.append(" ");

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo vdExpression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.VariableDeclarationExpression,
					startLine, endLine);

			for (final Object fragment : node.fragments()) {
				((ASTNode) fragment).accept(this);
				final ProgramElementInfo fragmentExpression = this.stack.pop();
				vdExpression.addExpression((ExpressionInfo) fragmentExpression);
				text.append(fragmentExpression.getText());
			}

			vdExpression.setText(text.toString());
			this.stack.push(vdExpression);
		}

		return false;
	}

	@Override
	public boolean visit(final VariableDeclarationStatement node) {

		if (this.inMethod) {

			final StringBuilder text = new StringBuilder();

			for (final Object modifier : node.modifiers()) {
				text.append(modifier.toString());
				text.append(" ");
			}

			text.append(node.getType().toString());
			text.append(" ");

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo vdStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.VariableDeclaration, startLine,
					endLine);

			for (final Object fragment : node.fragments()) {
				((ASTNode) fragment).accept(this);
				final ProgramElementInfo fragmentExpression = this.stack.pop();
				vdStatement.addExpression((ExpressionInfo) fragmentExpression);
				text.append(fragmentExpression.getText());
			}

			text.append(";");
			vdStatement.setText(text.toString());
			((BlockInfo) ownerBlock).addStatement(vdStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final VariableDeclarationFragment node) {

		if (this.inMethod) {

			final StringBuilder text = new StringBuilder();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ExpressionInfo vdFragment = new ExpressionInfo(
					ExpressionInfo.CATEGORY.VariableDeclarationFragment,
					startLine, endLine);

			text.append(node.getName().toString());

			if (null != node.getInitializer()) {
				node.getInitializer().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				vdFragment.addExpression((ExpressionInfo) expression);

				text.append(" = ");
				text.append(expression.getText());
			}

			vdFragment.setText(text.toString());
			this.stack.push(vdFragment);
		}

		return false;
	}

	@Override
	public boolean visit(final DoStatement node) {

		if (this.inMethod) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo doStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Do, startLine, endLine);
			this.stack.push(doStatement);

			node.getBody().accept(this);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack.pop();
			doStatement.setCondition((ExpressionInfo) condition);

			this.stack.pop();

			((BlockInfo) ownerBlock).addStatement(doStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final EnhancedForStatement node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo enhancedForStatement = new StatementInfo(
					ownerBlock, StatementInfo.CATEGORY.Foreach, startLine,
					endLine);

			node.getParameter().accept(this);
			final ProgramElementInfo parameter = this.stack.pop();
			enhancedForStatement.addInitializer((ExpressionInfo) parameter);

			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack.pop();
			enhancedForStatement.addInitializer((ExpressionInfo) expression);

			this.stack.push(enhancedForStatement);

			node.getBody().accept(this);

			this.stack.pop();

			((BlockInfo) ownerBlock).addStatement(enhancedForStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final ForStatement node) {

		if (this.inMethod) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo forStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.For, startLine, endLine);

			for (final Object initializer : node.initializers()) {
				((ASTNode) initializer).accept(this);
				final ProgramElementInfo initializerExpression = this.stack
						.pop();
				forStatement
						.addInitializer((ExpressionInfo) initializerExpression);
			}

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo condition = this.stack.pop();
				forStatement.setCondition((ExpressionInfo) condition);
			}

			{
				for (final Object updater : node.updaters()) {
					((ASTNode) updater).accept(this);
					final ProgramElementInfo updaterExpression = this.stack
							.pop();
					forStatement.addUpdater((ExpressionInfo) updaterExpression);
				}
			}

			this.stack.push(forStatement);

			node.getBody().accept(this);

			this.stack.pop();

			((BlockInfo) ownerBlock).addStatement(forStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final IfStatement node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo ifStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.If, startLine, endLine);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack.pop();
			ifStatement.setCondition((ExpressionInfo) condition);

			if (null != node.getThenStatement()) {
				this.stack.push(ifStatement);
				node.getThenStatement().accept(this);
				this.stack.pop();
			}

			if (null != node.getElseStatement()) {
				final int elseStartLine = this.getStartLineNumber(node
						.getElseStatement());
				final int elseEndLine = this.getEndLineNumber(node
						.getElseStatement());
				final StatementInfo elseStatement = new StatementInfo(
						ownerBlock, StatementInfo.CATEGORY.Else, elseStartLine,
						elseEndLine);

				this.stack.push(elseStatement);
				node.getElseStatement().accept(this);
				this.stack.pop();

				ifStatement.setElseStatement(elseStatement);
			}

			((BlockInfo) ownerBlock).addStatement(ifStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final SwitchStatement node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo switchStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Switch, startLine, endLine);
			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack.pop();
			switchStatement.setCondition((ExpressionInfo) condition);

			this.stack.push(switchStatement);

			for (final Object innerStatement : node.statements()) {
				((ASTNode) innerStatement).accept(this);
			}

			this.stack.pop();

			((BlockInfo) ownerBlock).addStatement(switchStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final SynchronizedStatement node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo synchronizedStatement = new StatementInfo(
					ownerBlock, StatementInfo.CATEGORY.Synchronized, startLine,
					endLine);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack.pop();
			synchronizedStatement.setCondition((ExpressionInfo) condition);

			this.stack.push(synchronizedStatement);

			node.getBody().accept(this);

			this.stack.pop();

			((BlockInfo) ownerBlock).addStatement(synchronizedStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final ThrowStatement node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo throwStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Throw, startLine, endLine);

			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack.pop();
			throwStatement.addExpression((ExpressionInfo) expression);

			((BlockInfo) ownerBlock).addStatement(throwStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final TryStatement node) {
		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo tryStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Try, startLine, endLine);

			this.stack.push(tryStatement);

			node.getBody().accept(this);

			this.stack.pop();

			for (final Object o : node.catchClauses()) {
				final CatchClause catchClause = (CatchClause) o;

				final int catchStartLine = this.getStartLineNumber(catchClause);
				final int catchEndLine = this.getEndLineNumber(catchClause);
				final StatementInfo catchStatement = new StatementInfo(
						ownerBlock, StatementInfo.CATEGORY.Catch,
						catchStartLine, catchEndLine);

				catchClause.getException().accept(this);
				final ProgramElementInfo exception = this.stack.pop();
				catchStatement.setCondition((ExpressionInfo) exception);

				this.stack.push(catchStatement);

				catchClause.getBody().accept(this);

				this.stack.pop();

				tryStatement.addCatchStatement(catchStatement);
			}

			if (null != node.getFinally()) {
				final int finallyStartLine = this.getStartLineNumber(node
						.getFinally());
				final int finallyEndLine = this.getEndLineNumber(node
						.getFinally());
				final StatementInfo finallyStatement = new StatementInfo(
						ownerBlock, StatementInfo.CATEGORY.Finally,
						finallyStartLine, finallyEndLine);

				this.stack.push(finallyStatement);

				node.getFinally().accept(this);

				this.stack.pop();

				tryStatement.setFinallyStatement(finallyStatement);
			}

			((BlockInfo) ownerBlock).addStatement(tryStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final WhileStatement node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo whileStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.While, startLine, endLine);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack.pop();
			whileStatement.setCondition((ExpressionInfo) condition);

			this.stack.push(whileStatement);

			node.getBody().accept(this);

			this.stack.pop();

			((BlockInfo) ownerBlock).addStatement(whileStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final SwitchCase node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo caseStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Case, startLine, endLine);

			final StringBuilder text = new StringBuilder();

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				caseStatement.addExpression((ExpressionInfo) expression);

				text.append("case ");
				text.append(expression.getText());
			} else {
				text.append("default");
			}

			text.append(":");
			caseStatement.setText(text.toString());

			((BlockInfo) ownerBlock).addStatement(caseStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final BreakStatement node) {

		if (this.inMethod) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo breakStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Break, startLine, endLine);

			final StringBuilder text = new StringBuilder();
			text.append("break");

			if (null != node.getLabel()) {
				node.getLabel().accept(this);
				final ProgramElementInfo label = this.stack.pop();
				breakStatement.addExpression((ExpressionInfo) label);

				text.append(" ");
				text.append(label.getText());
			}

			text.append(";");
			breakStatement.setText(text.toString());
			((BlockInfo) ownerBlock).addStatement(breakStatement);
		}

		return false;
	}

	@Override
	public boolean visit(final ContinueStatement node) {

		if (this.inMethod) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo continuekStatement = new StatementInfo(
					ownerBlock, StatementInfo.CATEGORY.Continue, startLine,
					endLine);

			final StringBuilder text = new StringBuilder();
			text.append("continue");

			if (null != node.getLabel()) {
				node.getLabel().accept(this);
				final ProgramElementInfo label = this.stack.pop();
				continuekStatement.addExpression((ExpressionInfo) label);

				text.append(" ");
				text.append(label.getText());
			}

			text.append(";");
			continuekStatement.setText(text.toString());
			((BlockInfo) ownerBlock).addStatement(continuekStatement);
		}

		return false;
	}

}
