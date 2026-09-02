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
}
