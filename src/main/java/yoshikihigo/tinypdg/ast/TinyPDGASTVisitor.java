package yoshikihigo.tinypdg.ast;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import yoshikihigo.tinypdg.pe.ClassInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.SimpleStatementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.VariableInfo;

/**
 * 型宣言とメソッド宣言を扱う段であり、走査の入口でもある。
 *
 * <p>階層の説明は {@link ProgramElementVisitor} にある。
 *
 * <p>AST を組み立てるには {@link JavaAstFactory} を使う。
 */
public class TinyPDGASTVisitor extends StatementVisitor {

	public TinyPDGASTVisitor(final String path, final CompilationUnit root,
			final List<MethodInfo> methods) {
		super(path, root, methods);
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
			final SimpleStatementInfo statement = new SimpleStatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Unsupported, startLine, endLine);
			statement.setText(flatten(node));
			this.stack.push(statement);
		}

		return false;
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
	 * 注釈型も同じである。注釈型の要素宣言 ({@code String value();}) は
	 * MethodDeclaration ではないので拾わないが、注釈型の中に書かれた
	 * クラスや enum はネストした型としてここを通る。
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
				final ProgramElementInfo method = this.visitChild((ASTNode) o);
				this.methods.add((MethodInfo) method);
				typeDeclaration.addMethod((MethodInfo) method);
				text.append(method.getText());
				text.append(System.lineSeparator());

			} else if (o instanceof AbstractTypeDeclaration) {
				// ネストした型。その中のメソッドは、ネスト側の
				// visitTypeDeclaration が this.methods に積む。
				final ProgramElementInfo nested = this.visitChild((ASTNode) o);
				text.append(nested.getText());
				text.append(System.lineSeparator());
			}
		}

		text.append("}");
		typeDeclaration.setText(text.toString());
	}

	/**
	 * 注釈型。他の型宣言と同じ経路を通す。
	 *
	 * <p>以前は本体の要素を 1 つずつ accept して pop していた。要素宣言には
	 * visit がなく、preVisit2 も式でも文でもないものは積まないので、pop する
	 * 相手がない。トップレベルの注釈型では空のスタックから pop して落ち、
	 * クラスの中にネストした注釈型では外側のクラスを pop してしまい、その後
	 * 外側の visitTypeDeclaration が行う pop が今度は空振りして落ちていた。
	 */
	@Override
	public boolean visit(final AnnotationTypeDeclaration node) {
		this.visitTypeDeclaration(node, "@interface ");
		return false;
	}

	@Override
	public boolean visit(final AnonymousClassDeclaration node) {

		final StringBuilder text = new StringBuilder();
		text.append("{");
		text.append(System.lineSeparator());

		final int startLine = this.getStartLineNumber(node);
		final int endLine = this.getEndLineNumber(node);
		final ClassInfo anonymousClass = new ClassInfo(this.path, null,
				startLine, endLine);
		this.stack.push(anonymousClass);

		for (final Object o : node.bodyDeclarations()) {
			if (o instanceof MethodDeclaration) {
				final ProgramElementInfo method = this.visitChild((ASTNode) o);
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

		final List<ProgramElementInfo> parameters = this.visitChildren(node.parameters());
		for (final ProgramElementInfo o : parameters) {
			final VariableInfo parameter = (VariableInfo) o;
			parameter.setCategory(VariableInfo.CATEGORY.PARAMETER);
			method.addParameter(parameter);
		}
		text.append(joinTexts(parameters, ","));
		text.append(")");

		if (null != node.getBody()) {
			final ProgramElementInfo body = this.visitChild(node.getBody());
			method.setStatement((StatementInfo) body);
			text.append(body.getText());
		}
		method.setText(text.toString());

		return false;
	}
}
