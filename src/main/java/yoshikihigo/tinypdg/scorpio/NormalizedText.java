package yoshikihigo.tinypdg.scorpio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import yoshikihigo.tinypdg.TinyPDGException;
import yoshikihigo.tinypdg.pe.ClassInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.OperatorInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.TypeInfo;
import yoshikihigo.tinypdg.pe.VariableInfo;

/**
 * 要素のテキストを、識別子とリテラルの違いを消した形にする。
 *
 * <p>2 段階で行う。{@link #getText()} は要素のテキストを組み立て直し、
 * 識別子とリテラルを {@code $$} で囲んで印を付ける。{@link #normalize(String)}
 * はその印を、出現順に {@code $1}, {@code $2}, ... へ置き換える。同じ名前は
 * 同じ番号になるので、変数名だけが違うコード片は同じテキストになる。
 * 両方を続けて行うのが {@link #normalize(ProgramElementInfo)} である。
 *
 * <p>メソッド名は正規化しない。{@code foo()} と {@code bar()} は別物の
 * ままである。
 */
public class NormalizedText {

	/**
	 * 要素のテキストを組み立て、識別子とリテラルを番号へ置き換えて返す。
	 *
	 * <p>Scorpio のハッシュ計算とノードの併合、DependenceDistiller が
	 * 同じ 2 行を書いていた。
	 */
	public static String normalize(final ProgramElementInfo element) {
		return normalize(new NormalizedText(element).getText());
	}

	public static String normalize(final String text) {

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
			this.text = textOf(this.core);
		}
		return this.text;
	}

	/** 子要素のテキスト。 */
	private static String normalized(final ProgramElementInfo element) {
		return new NormalizedText(element).getText();
	}

	/** 子要素を順に組み立て、区切りで繋げる。 */
	private static String join(final List<ProgramElementInfo> elements,
			final String separator) {
		final StringJoiner joiner = new StringJoiner(separator);
		for (final ProgramElementInfo element : elements) {
			joiner.add(normalized(element));
		}
		return joiner.toString();
	}

	/** 先頭の要素を除いた残り。メソッド名の後の引数などである。 */
	private static List<ProgramElementInfo> rest(
			final List<ProgramElementInfo> elements) {
		return elements.subList(1, elements.size());
	}

	/** 識別子やリテラルに印を付ける。normalize が番号に置き換える。 */
	private static String marked(final String text) {
		return "$$" + text + "$$";
	}

	private static String textOf(final ProgramElementInfo element) {
		return switch (element) {
		case StatementInfo statement -> textOf(statement);
		case ExpressionInfo expression -> textOf(expression);
		case TypeInfo type -> type.getText();
		case OperatorInfo operator -> operator.getText();
		case VariableInfo variable -> variable.type.getText() + " "
				+ marked(variable.name);
		default -> throw new TinyPDGException(
				"正規化できない要素です: " + element.getClass().getName());
		};
	}

	/*
	 * 以下 2 つは switch 文ではなく switch 式である。default 節を持たない
	 * 代わりに全ての種別を挙げてあり、種別を足すとコンパイルが通らなくなる。
	 * 新しい種別をどう正規化するか決めることを強制される。
	 */

	private static String textOf(final StatementInfo statement) {

		final List<ProgramElementInfo> expressions = statement.getExpressions();

		return switch (statement.getCategory()) {

		// メッセージは省略できる。あれば 2 つ目の式として入っている。
		case Assert -> "assert " + normalized(expressions.get(0))
				+ (1 < expressions.size()
						? " : " + normalized(expressions.get(1))
						: "")
				+ ";";

		case Break -> "break;";

		// case 1, 2 -> のようにラベルは複数ありうる。
		case Case -> expressions.isEmpty() ? "default:"
				: "case " + join(expressions, ",") + ":";

		case Continue -> "continue;";

		case Empty -> ";";

		case Expression -> normalized(expressions.get(0)) + ";";

		case Return -> expressions.isEmpty() ? "return;"
				: "return " + normalized(expressions.get(0)) + ";";

		case Throw -> "throw " + normalized(expressions.get(0)) + ";";

		case TypeDeclaration -> "class "
				+ ((ClassInfo) expressions.get(0)).name + "{}";

		// 型の後に、宣言の断片をカンマで並べる。
		case VariableDeclaration -> normalized(expressions.get(0)) + " "
				+ join(rest(expressions), ",") + ";";

		case Yield -> expressions.isEmpty() ? "yield;"
				: "yield " + normalized(expressions.get(0)) + ";";

		// 解釈できない構文は、ソース断片をそのまま持っている。
		// 正規化はできないが、落とすと中身が消えてしまう。
		case Unsupported -> statement.getText();

		// 中身は子の要素として別に正規化される。文そのものは何も足さない。
		case Catch, Do, For,
				Foreach, If, SimpleBlock,
				Switch, Synchronized, Try,
				While -> "";
		};
	}

	private static String textOf(final ExpressionInfo expression) {

		final List<ProgramElementInfo> children = expression.getExpressions();
		final ProgramElementInfo qualifier = expression.getQualifier();

		return switch (expression.category) {

		case ArrayAccess -> normalized(children.get(0)) + "["
				+ normalized(children.get(1)) + "]";

		case ArrayCreation -> arrayCreationText(children);

		case ArrayInitializer -> "{" + join(children, ",") + "}";

		case Assignment -> normalized(children.get(0)) + " "
				+ normalized(children.get(1)) + " "
				+ normalized(children.get(2));

		// 識別子とリテラル。normalize が、同じものは同じ番号に、違うものは
		// 違う番号に置き換える。
		case Boolean, Character, Number,
				SimpleName, String -> marked(expression.getText());

		case Cast -> "(" + normalized(children.get(0)) + ")"
				+ normalized(children.get(1));

		// 型の後に引数が並ぶ。型の名前は字面のまま。
		case ClassInstanceCreation -> "new " + children.get(0).getText()
				+ "(" + join(rest(children), ",") + ")";

		case ConstructorInvocation -> "this(" + join(children, ",") + ")";

		case FieldAccess -> normalized(children.get(0)) + "."
				+ normalized(children.get(1));

		// 被演算子と演算子が交互に並んでいる。
		case Infix -> join(children, " ");

		case Instanceof -> normalized(children.get(0)) + " instanceof "
				+ children.get(1).getText();

		case MethodEnter -> "METHODENTER";

		// メソッド名の後に引数が並ぶ。メソッド名は正規化しない。
		case MethodInvocation -> (null != qualifier
				? normalized(qualifier) + "."
				: "")
				+ children.get(0).getText()
				+ "(" + join(rest(children), ",") + ")";

		case Null -> "null";

		case Parenthesized -> "(" + normalized(children.get(0)) + ")";

		// 後置は被演算子・演算子、前置は演算子・被演算子の順に入っている。
		case Postfix, Prefix -> normalized(children.get(0))
				+ normalized(children.get(1));

		case QualifiedName -> normalized(qualifier) + "."
				+ normalized(children.get(0));

		case SuperConstructorInvocation -> (null != qualifier
				? normalized(qualifier) + "."
				: "")
				+ "super(" + join(children, ",") + ")";

		case SuperFieldAccess -> "super." + normalized(children.get(0));

		// MethodInvocation と同じく、メソッド名は正規化しない。
		case SuperMethodInvocation -> "super." + children.get(0).getText()
				+ "(" + join(rest(children), ",") + ")";

		case This -> "this";

		case Trinomial -> normalized(children.get(0)) + "? "
				+ normalized(children.get(1)) + ": "
				+ normalized(children.get(2));

		// 型の後に、宣言の断片をカンマで並べる。文の VariableDeclaration と同じ。
		case VariableDeclarationExpression -> children.get(0).getText() + " "
				+ join(rest(children), ",");

		case VariableDeclarationFragment -> normalized(children.get(0))
				+ (1 < children.size()
						? " = " + normalized(children.get(1))
						: "");

		// 本体は別の解析単位になっているか、そもそも本体を持たない。
		// 解釈できない構文も同じで、字面をそのまま使う。
		case Lambda, MethodReference, Unsupported -> expression.getText();

		// 子を順に正規化して並べる。
		case SwitchExpression, Pattern -> join(children, " ");

		// 取り出す変数と反復対象。int $1 : $2 のようになる。
		case ForeachHeader -> normalized(children.get(0)) + " : "
				+ normalized(children.get(1));

		// 字面を持たない。
		case TypeLiteral -> "";
		};
	}

	/**
	 * 配列生成。
	 *
	 * <p>子は、配列型、次元式 (0 個以上)、初期化子 (あれば) の順。初期化子が
	 * あれば最後の子で、次元式と同時には現れない。
	 *
	 * <p>型の名前は、要素型に次元の数だけ [] を付けたものである (ArrayType の
	 * visit がそう作る)。末尾の [] を外して要素型と次元数に分け、先頭から
	 * 次元式の数だけ式を入れ、残りは空のままにする。{@code new int[n][]} は
	 * {@code new int[$1][]} になる。
	 */
	private static String arrayCreationText(
			final List<ProgramElementInfo> children) {

		final List<ProgramElementInfo> rest = rest(children);
		final boolean hasInitializer = !rest.isEmpty()
				&& rest.get(rest.size() - 1) instanceof ExpressionInfo last
				&& ExpressionInfo.CATEGORY.ArrayInitializer == last.category;
		final List<ProgramElementInfo> dimensions = hasInitializer
				? rest.subList(0, rest.size() - 1)
				: rest;

		String elementType = children.get(0).getText();
		int emptyDimensions = 0;
		while (elementType.endsWith("[]")) {
			elementType = elementType.substring(0, elementType.length() - 2);
			emptyDimensions++;
		}
		emptyDimensions -= dimensions.size();

		final StringBuilder text = new StringBuilder();
		text.append("new ");
		text.append(elementType);
		for (final ProgramElementInfo dimension : dimensions) {
			text.append("[");
			text.append(normalized(dimension));
			text.append("]");
		}
		for (int i = 0; i < emptyDimensions; i++) {
			text.append("[]");
		}
		if (hasInitializer) {
			text.append(normalized(rest.get(rest.size() - 1)));
		}
		return text.toString();
	}
}
