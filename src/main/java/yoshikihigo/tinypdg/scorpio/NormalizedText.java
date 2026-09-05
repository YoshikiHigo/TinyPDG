package yoshikihigo.tinypdg.scorpio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import yoshikihigo.tinypdg.TinyPDGException;
import yoshikihigo.tinypdg.pe.ClassInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.OperatorInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.TypeInfo;
import yoshikihigo.tinypdg.pe.VariableInfo;

public class NormalizedText {

	public static String normalize(final String text) {

//		StringBuilder normalizedText = new StringBuilder(text);
		StringBuilder normalizedText = new StringBuilder(resolveDuplicatedMarkingTokens(text));
		final Map<String, String> mapper = new HashMap<>();

		int startIndex = 0;
		int endIndex = 0;
		while (true) {
			startIndex = normalizedText.indexOf("$$", startIndex);
			if (startIndex < 0) {
				break;
			}
			
			endIndex = normalizedText.indexOf("$$", startIndex + 1);
			final int doubleQuotationStartIndex = normalizedText.indexOf("\"", startIndex);
			final int doubleQuotationEndIndex = normalizedText.indexOf("\"", doubleQuotationStartIndex + 1);
			
			if (doubleQuotationStartIndex < endIndex && endIndex < doubleQuotationEndIndex) {
				endIndex = normalizedText.indexOf("$$", doubleQuotationEndIndex + 1);
			}
			
			assert 0 < endIndex : "invalid state.";

			final String target = normalizedText.substring(startIndex,
					endIndex + 1);
			String value = mapper.get(target);
			if (null == value) {
				value = "$" + Integer.toString(mapper.size() + 1);
				mapper.put(target, value);
			}
			normalizedText.replace(startIndex, endIndex + 2, value);
			startIndex++;
		}

		return normalizedText.toString();
	}
	
	private static String resolveDuplicatedMarkingTokens(final String text) {
		if (!text.contains("$$$")) {
			return text;
		}
		
		String result = text;
		while (result.contains("$$$")) {
			result = result.replace("$$$", "$$");
		}
		
		if (result.isEmpty()) {
			result = text;
		}
		
		return result;
	}

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
			final StatementInfo core = (StatementInfo) this.core;
			this.text = switch (core.getCategory()) {

			case Assert -> {
				text.append("assert ");
				final List<ProgramElementInfo> expressions = core
						.getExpressions();
				final NormalizedText expressionText = new NormalizedText(
						expressions.get(0));
				text.append(expressionText.getText());
				// メッセージは省略できる。あれば 2 つ目の式として入っている。
				if (1 < expressions.size()) {
					text.append(" : ");
					final NormalizedText messageText = new NormalizedText(
							expressions.get(1));
					text.append(messageText.getText());
				}
				text.append(";");
				yield text.toString();
			}

			case Break -> {
				text.append("break;");
				yield text.toString();
			}

			case Case -> {
				if (0 < core.getExpressions().size()) {
					text.append("case ");
					final ProgramElementInfo label = core.getExpressions().get(
							0);
					final NormalizedText labelText = new NormalizedText(label);
					text.append(labelText.getText());
					text.append(":");
				} else {
					text.append("default:");
				}
				yield text.toString();
			}

			case Continue -> {
				text.append("continue;");
				yield text.toString();
			}

			case Empty -> {
				text.append(";");
				yield text.toString();
			}

			case Expression -> {
				final ProgramElementInfo expression = core.getExpressions()
						.get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				text.append(";");
				yield text.toString();
			}

			case Return -> {
				text.append("return");
				if (0 < core.getExpressions().size()) {
					text.append(" ");
					final ProgramElementInfo expression = core.getExpressions()
							.get(0);
					final NormalizedText expressionText = new NormalizedText(
							expression);
					text.append(expressionText.getText());
				}
				text.append(";");
				yield text.toString();
			}

			case Throw -> {
				text.append("throw ");
				final ProgramElementInfo expression = ((StatementInfo) this.core)
						.getExpressions().get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				text.append(";");
				yield text.toString();
			}

			case TypeDeclaration -> {
				final ClassInfo typeDeclaration = (ClassInfo) ((StatementInfo) this.core)
						.getExpressions().get(0);
				text.append("class ");
				text.append(typeDeclaration.name);
				text.append("{}");
				yield text.toString();
			}

			case VariableDeclaration -> {
				final List<ProgramElementInfo> expressions = ((StatementInfo) this.core)
						.getExpressions();
				final NormalizedText typeText = new NormalizedText(
						expressions.get(0));
				text.append(typeText.getText());
				text.append(" ");
				
				boolean anyExpression = false;
				for (int i = 1; i < expressions.size(); i++) {
					anyExpression = true;
					final NormalizedText fragmentText = new NormalizedText(
							expressions.get(i));
					text.append(fragmentText.getText() + ",");
				}
				if (anyExpression) {
					text.deleteCharAt(text.length() - 1);
				}
				text.append(";");
				yield text.toString();
			}

			case Yield -> {
				text.append("yield");
				if (0 < core.getExpressions().size()) {
					text.append(" ");
					final ProgramElementInfo expression = core.getExpressions()
							.get(0);
					final NormalizedText expressionText = new NormalizedText(
							expression);
					text.append(expressionText.getText());
				}
				text.append(";");
				yield text.toString();
			}

			case Unsupported -> {
// 解釈できない構文は、ソース断片をそのまま持っている。
				// 正規化はできないが、落とすと中身が消えてしまう。
				text.append(core.getText());
				yield text.toString();
			}

			// 中身は子の要素として別に正規化される。文そのものは何も足さない。
			case Catch, Do, For,
					Foreach, If, SimpleBlock,
					Switch, Synchronized, Try,
					While -> "";
			};		}

		else if (this.core instanceof ExpressionInfo) {

			final ExpressionInfo coreExp = (ExpressionInfo) this.core;
			final StringBuilder text = new StringBuilder();
			this.text = switch (coreExp.category) {

			case ArrayAccess -> {
				final ProgramElementInfo expression = coreExp.getExpressions()
						.get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				text.append("[");
				final ProgramElementInfo index = coreExp.getExpressions()
						.get(1);
				final NormalizedText indexText = new NormalizedText(index);
				text.append(indexText.getText());
				text.append("]");
				yield text.toString();
			}

			case ArrayCreation -> {
				text.append("new ");
				// 型の名前に次元の [] まで入っている。
				final ProgramElementInfo type = coreExp.getExpressions().get(0);
				text.append(type.getText());
				if (1 < coreExp.getExpressions().size()) {
					final ProgramElementInfo initializer = coreExp
							.getExpressions().get(1);
					final NormalizedText initializerText = new NormalizedText(
							initializer);
					text.append(initializerText.getText());
				}
				yield text.toString();
			}

			case ArrayInitializer -> {
				text.append("{");
				final List<ProgramElementInfo> elements = coreExp
						.getExpressions();
				for (final ProgramElementInfo element : elements) {
					final NormalizedText elementText = new NormalizedText(
							element);
					text.append(elementText.getText());
					text.append(",");
				}
				// 空の {} では消すカンマがない。無条件に消すと "{" が消える。
				if (!elements.isEmpty()) {
					text.deleteCharAt(text.length() - 1);
				}
				text.append("}");
				yield text.toString();
			}

			case Assignment -> {
				final ProgramElementInfo left = coreExp.getExpressions().get(0);
				final NormalizedText leftText = new NormalizedText(left);
				text.append(leftText.getText());
				text.append(" ");
				final ProgramElementInfo operator = coreExp.getExpressions()
						.get(1);
				final NormalizedText operatorText = new NormalizedText(operator);
				text.append(operatorText.getText());
				text.append(" ");
				final ProgramElementInfo right = coreExp.getExpressions()
						.get(2);
				final NormalizedText rightText = new NormalizedText(right);
				text.append(rightText.getText());
				yield text.toString();
			}

			case Boolean -> {
				text.append("$$");
				text.append(coreExp.getText());
				text.append("$$");
				yield text.toString();
			}

			case Cast -> {
				text.append("(");
				final ProgramElementInfo type = coreExp.getExpressions().get(0);
				final NormalizedText typeText = new NormalizedText(type);
				text.append(typeText.getText());
				text.append(")");
				final ProgramElementInfo expression = coreExp.getExpressions()
						.get(1);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				yield text.toString();
			}

			case Character -> {
				text.append("$$");
				text.append(coreExp.getText());
				text.append("$$");
				yield text.toString();
			}

			case ClassInstanceCreation -> {
				text.append("new ");
				final ProgramElementInfo type = coreExp.getExpressions().get(0);
				text.append(type.getText());
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
				if (0 < expressions.size()) {
					text.deleteCharAt(text.length() - 1);
				}

				text.append(")");
				yield text.toString();
			}

			case ConstructorInvocation -> {
				text.append("this(");

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
				yield text.toString();
			}

			case FieldAccess -> {
				final ProgramElementInfo expression = coreExp.getExpressions()
						.get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());
				text.append(".");
				final ProgramElementInfo name = coreExp.getExpressions().get(1);
				final NormalizedText nameText = new NormalizedText(name);
				text.append(nameText.getText());
				yield text.toString();
			}

			case Infix -> {
				for (final ProgramElementInfo expression : coreExp
						.getExpressions()) {
					final NormalizedText expressionText = new NormalizedText(
							expression);
					text.append(expressionText.getText());
					text.append(" ");
				}
				text.deleteCharAt(text.length() - 1);
				yield text.toString();
			}

			case Instanceof -> {
				final ProgramElementInfo left = coreExp.getExpressions().get(0);
				final NormalizedText leftText = new NormalizedText(left);
				text.append(leftText.getText());
				text.append(" instanceof ");
				final ProgramElementInfo right = coreExp.getExpressions()
						.get(1);
				text.append(right.getText());
				yield text.toString();
			}

			case MethodEnter -> {
				text.append("METHODENTER");
				yield text.toString();
			}

			case MethodInvocation -> {
				if (null != coreExp.getQualifier()) {
					final ProgramElementInfo qualifier = coreExp.getQualifier();
					final NormalizedText qualifierText = new NormalizedText(
							qualifier);
					text.append(qualifierText.getText());
					text.append(".");
				}

				final ProgramElementInfo name = coreExp.getExpressions().get(0);
				text.append(name.getText());

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
				if (1 < coreExp.getExpressions().size()) {
					text.deleteCharAt(text.length() - 1);
				}

				text.append(")");
				yield text.toString();
			}

			case Null -> {
				text.append("null");
				yield text.toString();
			}

			case Number -> {
				text.append("$$");
				text.append(coreExp.getText());
				text.append("$$");
				yield text.toString();
			}

			case Parenthesized -> {
				text.append("(");

				final ProgramElementInfo expression = coreExp.getExpressions()
						.get(0);
				final NormalizedText expressionText = new NormalizedText(
						expression);
				text.append(expressionText.getText());

				text.append(")");
				yield text.toString();
			}

			case Postfix -> {
				final ProgramElementInfo operand = coreExp.getExpressions()
						.get(0);
				final NormalizedText operandText = new NormalizedText(operand);
				text.append(operandText.getText());

				final ProgramElementInfo operator = coreExp.getExpressions()
						.get(1);
				final NormalizedText operatorText = new NormalizedText(operator);
				text.append(operatorText.getText());
				yield text.toString();
			}

			case Prefix -> {
				final ProgramElementInfo operator = coreExp.getExpressions()
						.get(0);
				final NormalizedText operatorText = new NormalizedText(operator);
				text.append(operatorText.getText());

				final ProgramElementInfo operand = coreExp.getExpressions()
						.get(1);
				final NormalizedText operandText = new NormalizedText(operand);
				text.append(operandText.getText());
				yield text.toString();
			}

			case QualifiedName -> {
				final ProgramElementInfo qualifier = coreExp.getQualifier();
				final NormalizedText qualifierText = new NormalizedText(
						qualifier);
				text.append(qualifierText.getText());

				text.append(".");

				final ProgramElementInfo name = coreExp.getExpressions().get(0);
				final NormalizedText nameText = new NormalizedText(name);
				text.append(nameText.getText());
				yield text.toString();
			}

			case SimpleName -> {
				text.append("$$");
				text.append(coreExp.getText());
				text.append("$$");
				yield text.toString();
			}

			case String -> {
				text.append("$$");
				text.append(coreExp.getText());
				text.append("$$");
				yield text.toString();
			}

			case SuperConstructorInvocation -> {

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
				yield text.toString();
			}

			case SuperFieldAccess -> {
				text.append("super.");
				final ProgramElementInfo name = coreExp.getExpressions().get(0);
				final NormalizedText nameText = new NormalizedText(name);
				text.append(nameText.getText());
				yield text.toString();
			}

			case SuperMethodInvocation -> {
				text.append("super.");
				final ProgramElementInfo name = coreExp.getExpressions().get(0);
				final NormalizedText nameText = new NormalizedText(name);
				text.append(nameText.getText());
				text.append("(");

				final List<ProgramElementInfo> arguments = coreExp
						.getExpressions();
				arguments.remove(0);
				for (final ProgramElementInfo argument : arguments) {
					final NormalizedText argumentText = new NormalizedText(
							argument);
					text.append(argumentText.getText());
					text.append(",");
				}
				// 先頭の要素はメソッド名なので、式の数を見ると引数がなくても
				// 1 以上になる。引数の数で判断しないと "(" を消してしまう。
				if (!arguments.isEmpty()) {
					text.deleteCharAt(text.length() - 1);
				}

				text.append(")");
				yield text.toString();
			}

			case This -> {
				text.append("this");
				yield text.toString();
			}

			case Trinomial -> {
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
						.get(2);
				final NormalizedText elseExpText = new NormalizedText(elseExp);
				text.append(elseExpText.getText());
				yield text.toString();
			}

			case VariableDeclarationExpression -> {
				final List<ProgramElementInfo> expressions = coreExp
						.getExpressions();
				text.append(expressions.get(0).getText());
				text.append(" ");
				final NormalizedText expressionText = new NormalizedText(
						expressions.get(1));
				text.append(expressionText.getText());
				yield text.toString();
			}

			case VariableDeclarationFragment -> {
				final List<ProgramElementInfo> expressions = coreExp
						.getExpressions();
				final ProgramElementInfo left = expressions.get(0);
				final NormalizedText leftText = new NormalizedText(left);
				text.append(leftText.getText());

				if (1 < expressions.size()) {
					text.append(" = ");
					final ProgramElementInfo right = expressions.get(1);
					final NormalizedText rightText = new NormalizedText(right);
					text.append(rightText.getText());
				}
				yield text.toString();
			}

			case Lambda, MethodReference -> {
// 本体は別の解析単位になっているか、そもそも本体を持たない。
				// ここでは字面をそのまま使う。
				text.append(coreExp.getText());
				yield text.toString();
			}

			case SwitchExpression, Pattern -> {
				// 子を順に正規化して並べる。
				boolean first = true;
				for (final ProgramElementInfo expression : coreExp
						.getExpressions()) {
					if (!first) {
						text.append(" ");
					}
					final NormalizedText expressionText = new NormalizedText(
							expression);
					text.append(expressionText.getText());
					first = false;
				}
				yield text.toString();
			}

			case Unsupported -> {
text.append(coreExp.getText());
				yield text.toString();
			}

			// 字面を持たない、あるいは子として別に正規化されるもの。
			case TypeLiteral -> "";
			};		}

		else if (this.core instanceof TypeInfo) {
			this.text = this.core.getText();
		}

		else if (this.core instanceof OperatorInfo) {
			this.text = this.core.getText();
		}

		else if (this.core instanceof VariableInfo) {
			final StringBuilder text = new StringBuilder();
			text.append(((VariableInfo) this.core).type.getText());
			text.append(" $$");
			text.append(((VariableInfo) this.core).name);
			text.append("$$");
			this.text = text.toString();
		}
	}
}
