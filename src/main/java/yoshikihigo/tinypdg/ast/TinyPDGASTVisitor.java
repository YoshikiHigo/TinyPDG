package yoshikihigo.tinypdg.ast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import java.util.Set;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
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
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.GuardedPattern;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.RecordPattern;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

import yoshikihigo.tinypdg.pe.BlockInfo;
import yoshikihigo.tinypdg.pe.ClassInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.OperatorInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.TypeInfo;
import yoshikihigo.tinypdg.pe.VariableInfo;

public class TinyPDGASTVisitor extends ASTVisitor {

	/**
	 * 解析対象として既定で仮定する Java のバージョン。
	 *
	 * <p>現時点の LTS である Java 25。呼び出し側は createAST の第 3 引数で
	 * これ以外のバージョンを指定できる。
	 */
	static public final String DEFAULT_JAVA_VERSION = JavaCore.VERSION_25;

	/**
	 * ソースファイルを UTF-8 の Java {@value #DEFAULT_JAVA_VERSION} として
	 * 読み込み、AST を構築する。
	 */
	static public CompilationUnit createAST(final File file) {
		return createAST(file, StandardCharsets.UTF_8, DEFAULT_JAVA_VERSION);
	}

	/**
	 * ソースファイルを Java {@value #DEFAULT_JAVA_VERSION} として読み込み、
	 * AST を構築する。
	 */
	static public CompilationUnit createAST(final File file, final Charset charset) {
		return createAST(file, charset, DEFAULT_JAVA_VERSION);
	}

	/**
	 * ソースファイルを指定した文字コードで読み込み、AST を構築する。
	 *
	 * <p>以前はここで "JISAutoDetect" を指定していたが、この文字コードは
	 * ISO-2022-JP / Shift_JIS / EUC-JP を判別するためのものであり、UTF-8 の
	 * ソースを正しく読めない。Java 18 以降は UTF-8 が既定の文字コードでもある
	 * ため既定を UTF-8 に改め、他の文字コードが必要な場合は呼び出し側が
	 * 指定できるようにした。
	 *
	 * @param javaVersion 解析対象として仮定する Java のバージョン
	 *                    ("8", "11", "17", "21", "25" など)
	 */
	static public CompilationUnit createAST(final File file, final Charset charset,
			final String javaVersion) {

		final String lineSeparator = System.lineSeparator();
		final StringBuilder text = new StringBuilder();

		try (final BufferedReader reader = Files.newBufferedReader(file.toPath(),
				charset)) {
			String line;
			while (null != (line = reader.readLine())) {
				text.append(line);
				text.append(lineSeparator);
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}

		// AST の API レベルと、解析対象の言語レベルは別物である。
		// 前者はどの種類のノードを表現できるかを決めるだけなので、JDT が
		// 対応する最新に固定しておけばよい。実際にどの構文を受理するかは
		// コンパイラオプションの側で決まる。
		final ASTParser parser = ASTParser.newParser(AST.getJLSLatest());

		final Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(javaVersion, options);
		parser.setCompilerOptions(options);

		parser.setSource(text.toString().toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	final private String path;
	final private CompilationUnit root;
	final private List<MethodInfo> methods;
	final private Deque<ProgramElementInfo> stack;

	public TinyPDGASTVisitor(final String path, final CompilationUnit root,
			final List<MethodInfo> methods) {
		this.path = path;
		this.root = root;
		this.methods = methods;
		this.stack = new ArrayDeque<>();
	}

	/**
	 * このクラスが visit メソッドを用意しているノード型。
	 *
	 * <p>宣言済みの visit メソッドから自動的に求めるので、visit を追加した
	 * ときにここを更新し忘れて食い違う、ということが起きない。
	 */
	static private final Set<Class<?>> HANDLED_NODE_TYPES = handledNodeTypes();

	static private Set<Class<?>> handledNodeTypes() {
		final Set<Class<?>> types = new HashSet<>();
		for (Class<?> c = TinyPDGASTVisitor.class; null != c
				&& ASTVisitor.class != c; c = c.getSuperclass()) {
			for (final Method method : c.getDeclaredMethods()) {
				if ("visit".equals(method.getName()) && 1 == method.getParameterCount()
						&& ASTNode.class.isAssignableFrom(method.getParameterTypes()[0])) {
					types.add(method.getParameterTypes()[0]);
				}
			}
		}
		return Set.copyOf(types);
	}

	/**
	 * 未対応のノード型を、その内部に立ち入らずに 1 個の要素として扱う。
	 *
	 * <p>このクラスは全ての visit が false を返し、子ノードの走査を自分で
	 * 書いたうえで、スタックへの push と pop を対にする設計になっている。
	 * ASTVisitor の既定は「子を辿る」なので、visit を用意していないノードの
	 * 内部に勝手に降りると、対応する pop のない push が積まれてスタックが
	 * ずれ、離れた場所で ClassCastException として現れる。
	 *
	 * <p>そこで未対応ノードでは子を辿らず、式なら 1 個の ExpressionInfo を、
	 * 文なら 1 個の StatementInfo を積む。親は必ず 1 個 pop することを
	 * 前提にしているので、この約束さえ守れば構造は壊れない。解析の精度は
	 * その構文の内部について落ちるが、落ちるのは精度だけで済む。
	 */
	@Override
	public boolean preVisit2(final ASTNode node) {

		if (HANDLED_NODE_TYPES.contains(node.getClass())) {
			return true;
		}

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);

		if (node instanceof Expression) {
			final ExpressionInfo expression = new ExpressionInfo(
					ExpressionInfo.CATEGORY.Unsupported, startLine, endLine);
			expression.setText(flatten(node));
			this.stack.push(expression);

		} else if (node instanceof Statement) {
			final ProgramElementInfo ownerBlock = this.stack.isEmpty() ? null
					: this.stack.peek();
			final StatementInfo statement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Unsupported, startLine, endLine);
			statement.setText(flatten(node));
			this.stack.push(statement);
		}

		return false;
	}

	/** ノードのソース表現を 1 行に潰して返す。 */
	static private String flatten(final ASTNode node) {
		return node.toString().trim().replaceAll("\\s+", " ");
	}

	/**
	 * コンパイル単位からは型宣言だけを辿る。
	 *
	 * <p>このクラスは全ての visit が false を返し、子ノードの走査を自分で
	 * 書く方針で作られている。NaiveASTFlattener を継承していた頃は、
	 * 未対応ノードの走査を基底クラスの visit が肩代わりしていた。
	 * ASTVisitor は既定で全ての子を辿るため、入口をここで明示しておかないと
	 * package 宣言や import 宣言の名前まで降りてしまい、visit(SimpleName) が
	 * 誰も pop しない要素をスタックに積む。
	 */
	@Override
	public boolean visit(final CompilationUnit node) {
		for (final Object type : node.types()) {
			((ASTNode) type).accept(this);
		}
		return false;
	}

	@Override
	public boolean visit(final TypeDeclaration node) {
		this.visitTypeDeclaration(node, node.isInterface() ? "interface " : "class ");
		return false;
	}

	@Override
	public boolean visit(final EnumDeclaration node) {
		this.visitTypeDeclaration(node, "enum ");
		return false;
	}

	@Override
	public boolean visit(final RecordDeclaration node) {
		this.visitTypeDeclaration(node, "record ");
		return false;
	}

	/**
	 * 型宣言の中からメソッドとネストした型を拾う。
	 *
	 * <p>enum と record も型宣言なので、class や interface と同じ扱いをする。
	 * enum のコンストラクタやメソッドは、この形の宣言としてここに現れる。
	 *
	 * <p>以前はメソッドだけを拾い、ネストした型を素通りしていた。そのため
	 * 内部クラスや enum の中のメソッドが 1 つも見つからなかった。
	 */
	private void visitTypeDeclaration(final AbstractTypeDeclaration node,
			final String keyword) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ClassInfo typeDeclaration = new ClassInfo(this.path, node
				.getName().toString(), startLine, endLine);
		this.stack.push(typeDeclaration);

		final StringBuilder text = new StringBuilder();
		text.append(keyword);
		text.append(node.getName().toString());
		text.append("{");
		text.append(System.lineSeparator());

		for (final Object o : node.bodyDeclarations()) {

			if (o instanceof MethodDeclaration) {
				((ASTNode) o).accept(this);
				final ProgramElementInfo method = this.stack.pop();
				this.methods.add((MethodInfo) method);
				typeDeclaration.addMethod((MethodInfo) method);
				text.append(method.getText());
				text.append(System.lineSeparator());

			} else if (o instanceof AbstractTypeDeclaration) {
				// ネストした型。その中のメソッドは、ネスト側の
				// visitTypeDeclaration が this.methods に積む。
				((ASTNode) o).accept(this);
				final ProgramElementInfo nested = this.stack.pop();
				text.append(nested.getText());
				text.append(System.lineSeparator());
			}
		}

		text.append("}");
		typeDeclaration.setText(text.toString());
	}

	@Override
	public boolean visit(final TypeDeclarationStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo statement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.TypeDeclaration, startLine, endLine);
			this.stack.push(statement);

			node.getDeclaration().accept(this);
			final ProgramElementInfo typeDeclaration = this.stack.pop();
			statement.addExpression(typeDeclaration);

			statement.setText(typeDeclaration.getText());
		}

		return false;
	}

	@Override
	public boolean visit(final AnnotationTypeDeclaration node) {

		for (final Object o : node.bodyDeclarations()) {
			((ASTNode) o).accept(this);
			final ProgramElementInfo method = this.stack.pop();
		}

		return false;
	}

	@Override
	public boolean visit(final AnonymousClassDeclaration node) {

		final StringBuilder text = new StringBuilder();
		text.append("{");
		text.append(System.getProperty("line.separator"));

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ClassInfo anonymousClass = new ClassInfo(this.path, null,
				startLine, endLine);
		this.stack.push(anonymousClass);

		for (final Object o : node.bodyDeclarations()) {
			if (o instanceof MethodDeclaration) {
				((ASTNode) o).accept(this);
				final ProgramElementInfo method = this.stack.pop();
				// 匿名クラスのメソッドも 1 つの独立した解析単位として扱う。
				// ここで this.methods に入れ忘れていたため、これまで
				// 匿名クラスの中身は誰からも見えていなかった。
				this.methods.add((MethodInfo) method);
				anonymousClass.addMethod((MethodInfo) method);
				text.append(method.getText());
			}
		}

		text.append("}");
		anonymousClass.setText(text.toString());

		return false;
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
		boolean first = true;
		for (final Object o : node.patterns()) {
			if (!first) {
				text.append(", ");
			}
			((ASTNode) o).accept(this);
			final ProgramElementInfo nested = this.stack.pop();
			pattern.addExpression(nested);
			text.append(nested.getText());
			first = false;
		}
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

		node.getPattern().accept(this);
		final ProgramElementInfo pattern = this.stack.pop();
		guarded.addExpression(pattern);

		node.getExpression().accept(this);
		final ProgramElementInfo guard = this.stack.pop();
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

		node.getLeftOperand().accept(this);
		final ProgramElementInfo left = this.stack.pop();
		instanceofExpression.addExpression(left);

		node.getPattern().accept(this);
		final ProgramElementInfo pattern = this.stack.pop();
		instanceofExpression.addExpression(pattern);

		instanceofExpression.setText(
				left.getText() + " instanceof " + pattern.getText());
		return false;
	}

	/** switch 式から値を返す yield 文。return とほぼ同じ形で持つ。 */
	@Override
	public boolean visit(final YieldStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo yieldStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Yield, startLine, endLine);
			this.stack.push(yieldStatement);

			final StringBuilder text = new StringBuilder();
			text.append("yield");
			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				yieldStatement.addExpression(expression);
				text.append(" ");
				text.append(expression.getText());
			}
			text.append(";");
			yieldStatement.setText(text.toString());
		}

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
			((ASTNode) o).accept(this);
			final ProgramElementInfo parameter = this.stack.pop();
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
			body.accept(this);
			final ProgramElementInfo statement = this.stack.pop();
			lambda.setStatement((StatementInfo) statement);
			signature.append(statement.getText());

		} else {
			// 式本体のラムダ。x -> expr は return expr; と同じ意味なので
			// return 文に組み替える。通常のメソッドは本体が必ずブロックであり、
			// PDG の構築もそれを前提にしているため、ブロックで包んでおく。
			final int bodyStart = this.getStartLineNumber(body);
			final int bodyEnd = this.getEndLineNumber(body);

			final StatementInfo block = new StatementInfo(lambda,
					StatementInfo.CATEGORY.SimpleBlock, bodyStart, bodyEnd);
			this.stack.push(block);

			final StatementInfo returnStatement = new StatementInfo(block,
					StatementInfo.CATEGORY.Return, bodyStart, bodyEnd);
			this.stack.push(returnStatement);

			body.accept(this);
			final ProgramElementInfo expression = this.stack.pop();
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
	public boolean visit(final MethodDeclaration node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final String name = node.getName().getIdentifier();
		final MethodInfo method = new MethodInfo(this.path, name, startLine,
				endLine);
		this.stack.push(method);

		final StringBuilder text = new StringBuilder();
		for (final Object modifier : node.modifiers()) {
			method.addModifier(modifier.toString());
			text.append(modifier.toString());
			text.append(" ");
		}
		if (null != node.getReturnType2()) {
			text.append(node.getReturnType2().toString());
			text.append(" ");
		}
		text.append(name);
		text.append("(");

		for (final Object o : node.parameters()) {
			((ASTNode) o).accept(this);
			final VariableInfo parameter = (VariableInfo) this.stack.pop();
			parameter.setCategory(VariableInfo.CATEGORY.PARAMETER);
			method.addParameter(parameter);
			text.append(parameter.getText());
			text.append(",");
		}
		if (0 < node.parameters().size()) {
			text.deleteCharAt(text.length() - 1);
		}
		text.append(")");

		if (null != node.getBody()) {
			node.getBody().accept(this);
			final ProgramElementInfo body = this.stack.pop();
			method.setStatement((StatementInfo) body);
			text.append(body.getText());
		}
		method.setText(text.toString());

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

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack
					.pop();

			node.getMessage().accept(this);
			final ProgramElementInfo message = this.stack
					.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo statement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Assert, startLine, endLine);
			statement.addExpression(expression);
			statement.addExpression(message);
			this.stack.push(statement);
		}

		return false;
	}

	@Override
	public boolean visit(final ArrayAccess node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo expression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.ArrayAccess, startLine, endLine);
		this.stack.push(expression);

		node.getArray().accept(this);
		final ProgramElementInfo array = this.stack.pop();
		expression.addExpression(array);

		node.getIndex().accept(this);
		final ProgramElementInfo index = this.stack.pop();
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

		node.getOperand().accept(this);
		final ProgramElementInfo operand = this.stack.pop();
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

		node.getOperand().accept(this);
		final ProgramElementInfo operand = this.stack.pop();
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

		node.getName().accept(this);
		final ProgramElementInfo name = this.stack.pop();
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

		node.getName().accept(this);
		final ProgramElementInfo name = this.stack.pop();
		superMethodInvocation.addExpression(name);

		final StringBuilder text = new StringBuilder();
		text.append("super.");
		text.append(name);
		for (final Object argument : node.arguments()) {
			((ASTNode) argument).accept(this);
			final ProgramElementInfo argumentExpression = this.stack.pop();
			superMethodInvocation.addExpression(argumentExpression);
			text.append(argumentExpression.getText());
		}
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

		node.getQualifier().accept(this);
		final ProgramElementInfo qualifier = this.stack.pop();
		qualifiedName.setQualifier(qualifier);

		node.getName().accept(this);
		final ProgramElementInfo name = this.stack.pop();
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

		node.getExpression().accept(this);
		final ProgramElementInfo expression = this.stack.pop();
		fieldAccess.addExpression(expression);

		node.getName().accept(this);
		final ProgramElementInfo name = this.stack.pop();
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

		node.getLeftOperand().accept(this);
		final ProgramElementInfo left = this.stack.pop();
		infixExpression.addExpression(left);

		final OperatorInfo operator = new OperatorInfo(node.getOperator()
				.toString(), startLine, endLine);
		infixExpression.addExpression(operator);

		node.getRightOperand().accept(this);
		final ProgramElementInfo right = this.stack.pop();
		infixExpression.addExpression(right);

		final StringBuilder text = new StringBuilder();
		text.append(left.getText());
		text.append(" ");
		text.append(operator.getText());
		text.append(" ");
		text.append(right.getText());

		if (node.hasExtendedOperands()) {
			for (final Object operand : node.extendedOperands()) {
				((ASTNode) operand).accept(this);
				final ProgramElementInfo operandExpression = this.stack.pop();
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

	@Override
	public boolean visit(final ArrayCreation node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo arrayCreation = new ExpressionInfo(
				ExpressionInfo.CATEGORY.ArrayCreation, startLine, endLine);
		this.stack.push(arrayCreation);

		node.getType().accept(this);
		final ProgramElementInfo type = this.stack.pop();
		arrayCreation.addExpression(type);

		final StringBuilder text = new StringBuilder();
		text.append("new ");
		text.append(type.getText());
		text.append("[]");

		if (null != node.getInitializer()) {
			node.getInitializer().accept(this);
			final ProgramElementInfo initializer = this.stack.pop();
			arrayCreation.addExpression(initializer);
			text.append(arrayCreation);
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
		for (final Object expression : node.expressions()) {
			((ASTNode) expression).accept(this);
			final ProgramElementInfo subexpression = this.stack.pop();
			initializer.addExpression(subexpression);
			text.append(subexpression.getText());
			text.append(",");
		}
		if (0 < node.expressions().size()) {
			text.deleteCharAt(text.length() - 1);
		}
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

		node.getLeftHandSide().accept(this);
		final ProgramElementInfo left = this.stack.pop();
		assignment.addExpression(left);

		final OperatorInfo operator = new OperatorInfo(node.getOperator()
				.toString(), startLine, endLine);
		assignment.addExpression(operator);

		node.getRightHandSide().accept(this);
		final ProgramElementInfo right = this.stack.pop();
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

		node.getExpression().accept(this);
		final ProgramElementInfo expression = this.stack.pop();
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
		for (final Object argument : node.arguments()) {
			((ASTNode) argument).accept(this);
			final ProgramElementInfo argumentExpression = this.stack.pop();
			classInstanceCreation
					.addExpression(argumentExpression);

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
					.addExpression(expression);
			text.append(expression.getText());
		}

		if (null != node.getAnonymousClassDeclaration()) {
			node.getAnonymousClassDeclaration().accept(this);
			final ProgramElementInfo anonymousClass = this.stack.pop();
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

		node.getExpression().accept(this);
		final ProgramElementInfo expression = this.stack.pop();
		trinomial.addExpression(expression);

		node.getThenExpression().accept(this);
		final ProgramElementInfo thenExpression = this.stack.pop();
		trinomial.addExpression(thenExpression);

		node.getElseExpression().accept(this);
		final ProgramElementInfo elseExpression = this.stack.pop();
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
		for (final Object argument : node.arguments()) {
			((ASTNode) argument).accept(this);
			final ProgramElementInfo argumentExpression = this.stack.pop();
			invocation.addExpression(argumentExpression);
			text.append(argumentExpression.getText());
			text.append(",");
		}
		if (0 < node.arguments().size()) {
			text.deleteCharAt(text.length() - 1);
		}
		text.append(")");
		invocation.setText(text.toString());

		this.stack.pop();
		final ProgramElementInfo ownerBlock = this.stack.peek();
		final StatementInfo statement = new StatementInfo(ownerBlock,
				StatementInfo.CATEGORY.Expression, startLine, endLine);
		this.stack.push(statement);

		statement.addExpression(invocation);
		text.append(";");
		statement.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ExpressionStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo statement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Expression, startLine, endLine);
			this.stack.push(statement);

			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack
					.pop();
			statement.addExpression(expression);

			final StringBuilder text = new StringBuilder();
			text.append(expression.getText());
			text.append(";");
			statement.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final InstanceofExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo instanceofExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.Instanceof, startLine, endLine);
		this.stack.push(instanceofExpression);

		node.getLeftOperand().accept(this);
		final ProgramElementInfo left = this.stack.pop();
		instanceofExpression.addExpression(left);

		node.getRightOperand().accept(this);
		final ProgramElementInfo right = this.stack.pop();
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
			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack.pop();
			methodInvocation.setQualifier(expression);

			text.append(expression.getText());
			text.append(".");
		}

		node.getName().accept(this);
		final ProgramElementInfo name = this.stack.pop();
		methodInvocation.addExpression(name);

		text.append(name.getText());
		text.append("(");
		for (final Object argument : node.arguments()) {
			((ASTNode) argument).accept(this);
			final ProgramElementInfo argumentExpression = this.stack.pop();
			methodInvocation
					.addExpression(argumentExpression);

			text.append(argumentExpression.getText());
			text.append(",");
		}
		if (0 < node.arguments().size()) {
			text.deleteCharAt(text.length() - 1);
		}
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

		node.getExpression().accept(this);
		final ProgramElementInfo expression = this.stack.pop();
		parenthesizedExpression.addExpression(expression);

		final StringBuilder text = new StringBuilder();
		text.append("(");
		text.append(expression.getText());
		text.append(")");
		parenthesizedExpression.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final ReturnStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo returnStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Return, startLine, endLine);
			this.stack.push(returnStatement);

			final StringBuilder text = new StringBuilder();
			text.append("return");

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo expression = this.stack.pop();
				returnStatement.addExpression(expression);
				text.append(" ");
				text.append(expression.getText());
			}

			text.append(";");
			returnStatement.setText(text.toString());
		}

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
			node.getExpression().accept(this);
			final ProgramElementInfo qualifier = this.stack.pop();
			superConstructorInvocation.setQualifier(qualifier);
			text.append(qualifier.getText());
			text.append(".super(");
		} else {
			text.append("super(");
		}

		for (final Object argument : node.arguments()) {
			((ASTNode) argument).accept(this);
			final ProgramElementInfo argumentExpression = this.stack.pop();
			superConstructorInvocation
					.addExpression(argumentExpression);
			text.append(argumentExpression.getText());
			text.append(",");
		}
		if (0 < node.arguments().size()) {
			text.deleteCharAt(text.length() - 1);
		}
		text.append(")");
		superConstructorInvocation.setText(text.toString());

		this.stack.pop();
		final ProgramElementInfo ownerBlock = this.stack.peek();
		final StatementInfo statement = new StatementInfo(ownerBlock,
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

	@Override
	public boolean visit(final VariableDeclarationExpression node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo vdExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.VariableDeclarationExpression,
				startLine, endLine);
		this.stack.push(vdExpression);

		final TypeInfo type = new TypeInfo(node.getType().toString(),
				startLine, endLine);
		vdExpression.addExpression(type);

		final StringBuilder text = new StringBuilder();
		text.append(type.getText());
		text.append(" ");

		for (final Object fragment : node.fragments()) {
			((ASTNode) fragment).accept(this);
			final ProgramElementInfo fragmentExpression = this.stack.pop();
			vdExpression.addExpression(fragmentExpression);
			text.append(fragmentExpression.getText());
		}

		vdExpression.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final VariableDeclarationStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo vdStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.VariableDeclaration, startLine,
					endLine);
			this.stack.push(vdStatement);

			final StringBuilder text = new StringBuilder();
			for (final Object modifier : node.modifiers()) {
				text.append(modifier.toString());
				text.append(" ");
			}

			final ProgramElementInfo type = new TypeInfo(node.getType()
					.toString(), startLine, endLine);
			vdStatement.addExpression(type);

			text.append(node.getType().toString());
			text.append(" ");

			boolean anyExpression = false;
			for (final Object fragment : node.fragments()) {
				anyExpression = true;
				((ASTNode) fragment).accept(this);
				final ProgramElementInfo fragmentExpression = this.stack.pop();
				vdStatement
						.addExpression(fragmentExpression);
				text.append(fragmentExpression.getText() + ",");
			}
			if (anyExpression) {
				text.deleteCharAt(text.length() - 1);
			}
			
			text.append(";");
			vdStatement.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final VariableDeclarationFragment node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ExpressionInfo vdFragment = new ExpressionInfo(
				ExpressionInfo.CATEGORY.VariableDeclarationFragment, startLine,
				endLine);
		this.stack.push(vdFragment);

		node.getName().accept(this);
		final ProgramElementInfo name = this.stack.pop();
		vdFragment.addExpression(name);

		final StringBuilder text = new StringBuilder();
		text.append(name.getText());

		if (null != node.getInitializer()) {
			node.getInitializer().accept(this);
			final ProgramElementInfo expression = this.stack.pop();
			vdFragment.addExpression(expression);

			text.append(" = ");
			text.append(expression.getText());
		}

		vdFragment.setText(text.toString());

		return false;
	}

	@Override
	public boolean visit(final DoStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo doBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Do, startLine, endLine);
			this.stack.push(doBlock);

			node.getBody().accept(this);
			final StatementInfo body = (StatementInfo) this.stack.pop();
			doBlock.setStatement(body);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack
					.pop();
			doBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(doBlock);

			final StringBuilder text = new StringBuilder();
			text.append("do ");
			text.append(body.getText());
			text.append("while (");
			text.append(condition.getText());
			text.append(");");
		}

		return false;
	}

	@Override
	public boolean visit(final EnhancedForStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			node.getParameter().accept(this);
			final ProgramElementInfo parameter = this.stack.pop();

			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack.pop();

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo foreachBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Foreach, startLine, endLine);
			foreachBlock.addInitializer(parameter);
			foreachBlock.addInitializer(expression);
			this.stack.push(foreachBlock);

			node.getBody().accept(this);
			final StatementInfo body = (StatementInfo) this.stack.pop();
			foreachBlock.setStatement(body);

			final StringBuilder text = new StringBuilder();
			text.append("for (");
			text.append(parameter.getText());
			text.append(" : ");
			text.append(expression.getText());
			text.append(")");
			text.append(body.getText());
			foreachBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final ForStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo forBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.For, startLine, endLine);
			this.stack.push(forBlock);

			final StringBuilder text = new StringBuilder();
			text.append("for (");

			for (final Object o : node.initializers()) {
				((ASTNode) o).accept(this);
				final ExpressionInfo initializer = (ExpressionInfo) this.stack
						.pop();
				forBlock.addInitializer(initializer);
				text.append(initializer.getText());
				text.append(",");
			}
			if (0 < node.initializers().size()) {
				text.deleteCharAt(text.length() - 1);
			}

			text.append("; ");

			if (null != node.getExpression()) {
				node.getExpression().accept(this);
				final ProgramElementInfo condition = this.stack
						.pop();
				forBlock.setCondition(condition);
				condition.setOwnerConditinalBlock(forBlock);
				text.append(condition.getText());
			}

			text.append("; ");

			for (final Object o : node.updaters()) {
				((ASTNode) o).accept(this);
				final ExpressionInfo updater = (ExpressionInfo) this.stack
						.pop();
				forBlock.addUpdater(updater);
				text.append(updater.getText());
				text.append(",");
			}
			if (0 < node.updaters().size()) {
				text.deleteCharAt(text.length() - 1);
			}

			text.append(")");

			node.getBody().accept(this);
			final StatementInfo body = (StatementInfo) this.stack.pop();
			forBlock.setStatement(body);
			text.append(body.getText());
			forBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final IfStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo ifBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.If, startLine, endLine);
			this.stack.push(ifBlock);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack
					.pop();
			ifBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(ifBlock);

			final StringBuilder text = new StringBuilder();
			text.append("if (");
			text.append(condition.getText());
			text.append(") ");

			if (null != node.getThenStatement()) {
				node.getThenStatement().accept(this);
				final StatementInfo thenBody = (StatementInfo) this.stack.pop();
				ifBlock.setStatement(thenBody);
				text.append(thenBody.getText());
			}

			if (null != node.getElseStatement()) {
				node.getElseStatement().accept(this);
				final StatementInfo elseBody = (StatementInfo) this.stack.pop();
				ifBlock.setElseStatement(elseBody);
				text.append(elseBody.getText());
			}

			ifBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final SwitchStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo switchBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Switch, startLine, endLine);
			this.stack.push(switchBlock);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack
					.pop();
			switchBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(switchBlock);

			final StringBuilder text = new StringBuilder();
			text.append("switch (");
			text.append(condition.getText());
			text.append(") {");
			text.append(System.getProperty("line.separator"));

			for (final Object o : node.statements()) {
				((ASTNode) o).accept(this);
				final StatementInfo statement = (StatementInfo) this.stack
						.pop();
				switchBlock.addStatement(statement);
				text.append(statement.getText());
				text.append(System.getProperty("line.separator"));
			}

			switchBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final SynchronizedStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo synchronizedBlock = new StatementInfo(
					ownerBlock, StatementInfo.CATEGORY.Synchronized, startLine,
					endLine);
			this.stack.push(synchronizedBlock);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack
					.pop();
			synchronizedBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(synchronizedBlock);

			node.getBody().accept(this);
			final StatementInfo body = (StatementInfo) this.stack.pop();
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

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo throwStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Throw, startLine, endLine);
			this.stack.push(throwStatement);

			node.getExpression().accept(this);
			final ProgramElementInfo expression = this.stack
					.pop();
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

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo tryBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Try, startLine, endLine);
			this.stack.push(tryBlock);

			// try-with-resources のリソース。JLS 上、リソースは try 本体を
			// 囲む暗黙のブロックでの変数宣言と定義されている。その形どおりに
			// 宣言文へ組み替えてブロックの先頭に並べると、CFG のノードとして
			// 現れ、本体からのデータ依存もそのまま繋がる。
			final StatementInfo resourceBlock = node.resources().isEmpty() ? null
					: new StatementInfo(tryBlock,
							StatementInfo.CATEGORY.SimpleBlock, startLine, endLine);

			final List<StatementInfo> resources = new ArrayList<>();
			final StringBuilder resourceText = new StringBuilder();
			for (final Object o : node.resources()) {
				((ASTNode) o).accept(this);
				final ProgramElementInfo resource = this.stack.pop();

				final StatementInfo declaration = new StatementInfo(resourceBlock,
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

			node.getBody().accept(this);
			final StatementInfo body = (StatementInfo) this.stack.pop();

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
				((ASTNode) o).accept(this);
				final StatementInfo catchBlock = (StatementInfo) this.stack
						.pop();
				tryBlock.addCatchStatement(catchBlock);
				text.append(catchBlock.getText());
			}

			if (null != node.getFinally()) {
				node.getFinally().accept(this);
				final StatementInfo finallyBlock = (StatementInfo) this.stack
						.pop();
				tryBlock.setFinallyStatement(finallyBlock);
				text.append(finallyBlock.getText());
			}

			tryBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final WhileStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo whileBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.While, startLine, endLine);
			this.stack.push(whileBlock);

			node.getExpression().accept(this);
			final ProgramElementInfo condition = this.stack
					.pop();
			whileBlock.setCondition(condition);
			condition.setOwnerConditinalBlock(whileBlock);

			node.getBody().accept(this);
			StatementInfo body = (StatementInfo) this.stack.pop();
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

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo switchCase = new StatementInfo(ownerBlock,
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
				boolean first = true;
				for (final Object o : expressions) {
					if (!first) {
						text.append(", ");
					}
					((ASTNode) o).accept(this);
					final ProgramElementInfo expression = this.stack.pop();
					switchCase.addExpression(expression);
					text.append(expression.getText());
					first = false;
				}
			}

			// case X -> ... の矢印形式か、従来の case X: 形式か。
			text.append(node.isSwitchLabeledRule() ? " ->" : ":");
			switchCase.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final BreakStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo breakStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Break, startLine, endLine);
			this.stack.push(breakStatement);

			final StringBuilder text = new StringBuilder();
			text.append("break");

			if (null != node.getLabel()) {
				node.getLabel().accept(this);
				final ProgramElementInfo label = this.stack.pop();
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

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo continuekStatement = new StatementInfo(
					ownerBlock, StatementInfo.CATEGORY.Continue, startLine,
					endLine);
			this.stack.push(continuekStatement);

			final StringBuilder text = new StringBuilder();
			text.append("continue");

			if (null != node.getLabel()) {
				node.getLabel().accept(this);
				final ProgramElementInfo label = this.stack.pop();
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

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo simpleBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.SimpleBlock, startLine, endLine);
			this.stack.push(simpleBlock);

			final StringBuilder text = new StringBuilder();
			text.append("{");
			text.append(System.getProperty("line.separator"));

			for (final Object o : node.statements()) {
				((ASTNode) o).accept(this);
				final ProgramElementInfo statement = this.stack.pop();
				simpleBlock.addStatement((StatementInfo) statement);
				text.append(statement.getText());
				text.append(System.getProperty("line.separator"));
			}

			text.append("}");
			simpleBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final CatchClause node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo catchBlock = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Catch, startLine, endLine);
			this.stack.push(catchBlock);

			node.getException().accept(this);
			final ProgramElementInfo exception = this.stack.pop();
			exception.setOwnerConditinalBlock(catchBlock);
			catchBlock.setCondition(exception);

			node.getBody().accept(this);
			final StatementInfo body = (StatementInfo) this.stack.pop();
			catchBlock.setStatement(body);

			final StringBuilder text = new StringBuilder();
			text.append("catch (");
			text.append(exception.getText());
			text.append(") ");
			text.append(catchBlock.getText());
			catchBlock.setText(text.toString());
		}

		return false;
	}

	@Override
	public boolean visit(final SingleVariableDeclaration node) {

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final TypeInfo type = new TypeInfo(node.getType().toString(),
				startLine, endLine);
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

	@Override
	public boolean visit(final EmptyStatement node) {

		if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {

			final int startLine = this.getStartLineNumber(node);
			final int endLine = this.getEndLineNumber(node);
			final ProgramElementInfo ownerBlock = this.stack.peek();
			final StatementInfo emptyStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Empty, startLine, endLine);
			this.stack.push(emptyStatement);
			emptyStatement.setText(";");
		}

		return false;
	}

}
