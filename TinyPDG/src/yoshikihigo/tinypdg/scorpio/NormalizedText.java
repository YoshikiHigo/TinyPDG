package yoshikihigo.tinypdg.scorpio;

import java.util.List;

import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;

public class NormalizedText {

	public final ProgramElementInfo core;
	public String text;

	public NormalizedText(final ProgramElementInfo core) {
		this.core = core;
		this.text = null;
	}

	public String getText() {
		if (null == this.text) {
			this.generateText();
		}
		return this.text;
	}

	private void generateText() {

		if (this.core instanceof StatementInfo) {

			final StringBuilder text = new StringBuilder();
			switch (((StatementInfo) this.core).getCategory()) {
			case Assert: {
				text.append("assert ");
				final ExpressionInfo expression = ((StatementInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				text.append(" : ");
				final ExpressionInfo message = ((StatementInfo) this.core)
						.getExpressions().get(1);
				final NormalizedText messageText = new NormalizedText(message);
				text.append(messageText.getText());
				text.append(";");
				break;
			}
			case Break: {
				text.append("break;");
				break;
			}
			case Case: {
				text.append("case $:");
				break;
			}
			case Catch:
				break;
			case Continue: {
				text.append("continue;");
				break;
			}
			case Do:
				break;
			case Expression:
				break;
			case For:
				break;
			case Foreach:
				break;
			case If:
				break;
			case Return: {
				text.append("return ");
				final ExpressionInfo expression = ((StatementInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				break;
			}
			case SimpleBlock:
				break;
			case Switch:
				break;
			case Synchronized:
				break;
			case Throw: {
				text.append("return ");
				final ExpressionInfo expression = ((StatementInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				break;
			}
			case Try:
				break;
			case VariableDeclaration:
				break;
			case While:
				break;
			default:
				assert false : "invalide status.";
				break;
			}
			this.text = text.toString();
		}

		else if (this.core instanceof ExpressionInfo) {

			final ExpressionInfo coreExp = (ExpressionInfo) this.core;
			final StringBuilder text = new StringBuilder();
			switch (((ExpressionInfo) this.core).category) {
			case ArrayAccess: {
				final ProgramElementInfo expression = ((ExpressionInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				text.append("[");
				final ProgramElementInfo index = ((ExpressionInfo) this.core)
						.getExpressions().get(1);
				final NormalizedText indexText = new NormalizedText(index);
				text.append(indexText.getText());
				text.append("]");
				break;
			}
			case ArrayCreation: {
				text.append("new ");
				final ProgramElementInfo type = ((ExpressionInfo) this.core)
						.getExpressions().get(0);
				text.append(type.getText());
				text.append("[]");
				if (1 < ((ExpressionInfo) this.core).getExpressions().size()) {
					final ProgramElementInfo initializer = ((ExpressionInfo) this.core)
							.getExpressions().get(1);
					final NormalizedText initializerText = new NormalizedText(
							initializer);
					text.append(initializerText.getText());
				}
				break;
			}
			case ArrayInitializer: {
				text.append("{");
				for (final ProgramElementInfo expression : ((ExpressionInfo) this.core)
						.getExpressions()) {
					final NormalizedText expressionText = new NormalizedText(
							expression);
					text.append(expressionText.getText());
					text.append(",");
				}
				text.deleteCharAt(text.length() - 1);
				text.append("}");
				break;
			}
			case Assignment: {
				final ProgramElementInfo left = ((ExpressionInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText leftText = new NormalizedText(left);
				text.append(leftText.getText());
				final ProgramElementInfo operator = ((ExpressionInfo) this.core)
						.getExpressions().get(1);
				final NormalizedText operatorText = new NormalizedText(operator);
				text.append(operatorText.getText());
				final ProgramElementInfo right = ((ExpressionInfo) this.core)
						.getExpressions().get(2);
				final NormalizedText rightText = new NormalizedText(right);
				text.append(rightText.getText());
				break;
			}
			case Boolean: {
				text.append("$");
				break;
			}
			case Cast: {
				text.append("(");
				final ProgramElementInfo type = ((ExpressionInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText typeText = new NormalizedText(type);
				text.append(typeText.getText());
				text.append(")");
				final ProgramElementInfo expression = ((ExpressionInfo) this.core)
						.getExpressions().get(1);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				break;
			}
			case Character: {
				text.append("$");
				break;
			}
			case ClassInstanceCreation: {
				text.append("new ");
				final ProgramElementInfo type = ((ExpressionInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText typeText = new NormalizedText(type);
				text.append(typeText.getText());
				text.append("(");
				final List<ProgramElementInfo> expressions = ((ExpressionInfo) this.core)
						.getExpressions();
				expressions.remove(0);
				for (final ProgramElementInfo argument : expressions) {
					final NormalizedText argumentText = new NormalizedText(
							argument);
					text.append(argumentText.getText());
					text.append(",");
				}
				if (0 < expressions.size()) {
					text.deleteCharAt(text.length() - 1);
				}

				text.append(")");
				break;
			}
			case ConstructorInvocation: {
				text.append("(");

				for (final ProgramElementInfo argument : ((ExpressionInfo) this.core)
						.getExpressions()) {
					final NormalizedText argumentText = new NormalizedText(
							argument);
					text.append(argumentText.getText());
					text.append(",");
				}
				if (0 < ((ExpressionInfo) this.core).getExpressions().size()) {
					text.deleteCharAt(text.length() - 1);
				}

				text.append(")");
				break;
			}
			case FieldAccess: {
				final ProgramElementInfo expression = ((ExpressionInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				text.append(".");
				final ProgramElementInfo name = ((ExpressionInfo) this.core)
						.getExpressions().get(1);
				final NormalizedText nameText = new NormalizedText(name);
				text.append(nameText.getText());
				break;
			}
			case Infix: {
				for (final ProgramElementInfo expression : ((ExpressionInfo) this.core)
						.getExpressions()) {
					final NormalizedText expressionText = new NormalizedText(
							expression);
					text.append(expressionText.getText());
					text.append(" ");
				}
				text.deleteCharAt(text.length() - 1);
				break;
			}
			case Instanceof: {
				final ProgramElementInfo left = ((ExpressionInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText leftText = new NormalizedText(left);
				text.append(leftText.getText());
				text.append(" instanceof ");
				final ProgramElementInfo right = ((ExpressionInfo) this.core)
						.getExpressions().get(1);
				final NormalizedText rightText = new NormalizedText(right);
				text.append(rightText.getText());
				break;
			}
			case MethodEnter: {
				text.append("METHODENTER");
				break;
			}
			case MethodInvocation: {
				if (null != ((ExpressionInfo) this.core).getQualifier()) {
					final ProgramElementInfo qualifier = ((ExpressionInfo) this.core)
							.getQualifier();
					final NormalizedText qualifierText = new NormalizedText(
							qualifier);
					text.append(qualifierText.getText());
				}

				final ProgramElementInfo name = coreExp.getExpressions().get(0);
				final NormalizedText nameText = new NormalizedText(name);
				text.append(nameText.getText());

				text.append("(");

				final List<ProgramElementInfo> expressions = coreExp
						.getExpressions();
				expressions.remove(0);
				for (final ProgramElementInfo argument : expressions) {
					final NormalizedText argumentText = new NormalizedText(
							argument);
					text.append(argumentText.getText());
					text.append(",");
				}
				if (0 < ((ExpressionInfo) this.core).getExpressions().size()) {
					text.deleteCharAt(text.length() - 1);
				}

				text.append(")");
				break;
			}
			case Null: {
				text.append("null");
				break;
			}
			case Number: {
				text.append("$");
				break;
			}
			case Parenthesized: {
				text.append("(");

				final ProgramElementInfo expression = coreExp.getExpressions()
						.get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());

				text.append(")");
				break;
			}
			case Postfix: {
				final ProgramElementInfo operand = coreExp.getExpressions()
						.get(0);
				final NormalizedText operandText = new NormalizedText(operand);
				text.append(operandText.getText());

				final ProgramElementInfo operator = coreExp.getExpressions()
						.get(1);
				final NormalizedText operatorText = new NormalizedText(operator);
				text.append(operatorText.getText());
				break;
			}
			case Prefix: {
				final ProgramElementInfo operator = coreExp.getExpressions()
						.get(0);
				final NormalizedText operatorText = new NormalizedText(operator);
				text.append(operatorText.getText());

				final ProgramElementInfo operand = coreExp.getExpressions()
						.get(1);
				final NormalizedText operandText = new NormalizedText(operand);
				text.append(operandText.getText());
				break;
			}
			case QualifiedName: {
				final ProgramElementInfo qualifier = coreExp.getQualifier();
				final NormalizedText qualifierText = new NormalizedText(
						qualifier);
				text.append(qualifierText.getText());

				text.append(".");
				text.append("$");
				break;
			}
			case SimpleName: {
				text.append("$");
				break;
			}
			case String: {
				text.append("$");
				break;
			}
			case SuperConstructorInvocation: {

				if (null != coreExp.getQualifier()) {
					final ProgramElementInfo qualifier = coreExp.getQualifier();
					final NormalizedText qualifierText = new NormalizedText(
							qualifier);
					text.append(qualifierText.getText());
					text.append(".");
				}

				text.append("super(");
				for (final ProgramElementInfo argument : coreExp
						.getExpressions()) {
					final NormalizedText argumentText = new NormalizedText(
							argument);
					text.append(argumentText.getText());
					text.append(",");
				}
				if (0 < coreExp.getExpressions().size()) {
					text.deleteCharAt(text.length() - 1);
				}
				text.append(")");
				break;
			}
			case SuperFieldAccess: {
				text.append("super.");
				text.append("$");
				break;
			}
			case SuperMethodInvocation: {
				text.append("super.");
				final ProgramElementInfo name = coreExp.getExpressions().get(0);
				final NormalizedText nameText = new NormalizedText(name);
				text.append(nameText.getText());
				text.append("(");

				final List<ProgramElementInfo> expressions = coreExp
						.getExpressions();
				expressions.remove(0);
				for (final ProgramElementInfo argument : expressions) {
					final NormalizedText argumentText = new NormalizedText(
							argument);
					text.append(argumentText.getText());
					text.append(",");
				}
				if (0 < ((ExpressionInfo) this.core).getExpressions().size()) {
					text.deleteCharAt(text.length() - 1);
				}

				text.append(")");
				break;
			}
			case This: {
				text.append("this");
				break;
			}
			case Trinomial: {
				final ProgramElementInfo expression = coreExp.getExpressions()
						.get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());

				text.append("? ");

				final ProgramElementInfo thenExp = coreExp.getExpressions()
						.get(1);
				final NormalizedText thenExpText = new NormalizedText(thenExp);
				text.append(thenExpText.getText());

				text.append(": ");

				final ProgramElementInfo elseExp = coreExp.getExpressions()
						.get(1);
				final NormalizedText elseExpText = new NormalizedText(elseExp);
				text.append(elseExpText.getText());
				break;
			}
			case TypeLiteral:
				break;
			case VariableDeclarationExpression:
				break;
			case VariableDeclarationFragment:
				final ProgramElementInfo left = coreExp.getExpressions().get(0);
				final NormalizedText leftText = new NormalizedText(left);
				text.append(leftText.getText());

				text.append(" = ");

				final ProgramElementInfo right = coreExp.getExpressions()
						.get(0);
				final NormalizedText rightText = new NormalizedText(right);
				text.append(rightText.getText());
				break;
			default:
				break;

			}

			this.text = text.toString();
		}
	}
}
