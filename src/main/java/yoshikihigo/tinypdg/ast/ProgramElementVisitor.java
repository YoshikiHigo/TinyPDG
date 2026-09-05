package yoshikihigo.tinypdg.ast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.StringJoiner;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import yoshikihigo.tinypdg.pe.BlockInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;

/**
 * 走査中に共有する状態と、どの階層からも使う小さな道具。
 *
 * <p>visit メソッドは 70 個を超え、1 つのクラスに置くと 2500 行を超えていた。
 * 扱う構文の種類で 3 段に分けてある。
 *
 * <pre>
 *   ProgramElementVisitor   共有する状態とヘルパ
 *   └─ ExpressionVisitor     式
 *       └─ StatementVisitor   文
 *           └─ TinyPDGASTVisitor  宣言。走査の入口でもある
 * </pre>
 *
 * <p>JDT は visit を 1 つのオブジェクトに振り分けるので、分けるとしても
 * 継承で重ねるほかない。下の段ほど基本的な構文を扱う。
 *
 * <p>状態を package private にしてあるのは、この 4 つが同じパッケージに
 * あって互いに触るためである。外へは出ない。
 */
abstract class ProgramElementVisitor extends ASTVisitor {

	final String path;
	final CompilationUnit root;
	final List<MethodInfo> methods;
	final Deque<ProgramElementInfo> stack;

	/**
	 * 直前に visit した文の前へ挿入したい文。switch 式の脱糖で生じる。
	 * ブロックが自分の文を組み立てるときに回収する。
	 */
	final List<StatementInfo> pendingStatements = new ArrayList<>();

	/**
	 * 脱糖中の switch 式が値を書き込む一時変数。yield をこの変数への代入に
	 * 読み替えるために使う。switch 式は入れ子になりうるのでスタックで持つ。
	 */
	final Deque<String> yieldTargets = new ArrayDeque<>();

	int switchExpressionCount = 0;

	/**
	 * 直前に組み立てた文が、脱糖された yield を含んでいたか。
	 * switch 式のアームの終わりを見分けるために使う。
	 */
	boolean yieldConverted = false;

	/** ノードのソース表現を 1 行に潰して返す。 */
	static String flatten(final ASTNode node) {
		return node.toString().trim().replaceAll("\\s+", " ");
	}

	/**
	 * スタックの先頭が、文を足せるブロックかどうか。
	 *
	 * <p>文の visit はすべてこれを見てから動く。ブロックの外にある文は
	 * 扱わない、という約束をここに置く。
	 */
	boolean inBlock() {
		return !this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo;
	}

	/**
	 * 子ノードを訪問し、その visit が積んだ要素を取り出して返す。
	 *
	 * <p>このクラスの visit は、子を辿ったら要素をちょうど 1 個積む約束で
	 * 書かれている。accept と pop を対にして書くのはその約束の裏返しで、
	 * 全ての visit に現れる。
	 */
	ProgramElementInfo visitChild(final ASTNode node) {
		node.accept(this);
		return this.stack.pop();
	}

	/** 子ノードの並びを順に訪問し、積まれた要素を同じ順で返す。 */
	List<ProgramElementInfo> visitChildren(final List<?> nodes) {
		final List<ProgramElementInfo> children = new ArrayList<>();
		for (final Object node : nodes) {
			children.add(this.visitChild((ASTNode) node));
		}
		return children;
	}

	/** 要素のテキストを区切りで繋げる。 */
	static String joinTexts(final List<? extends ProgramElementInfo> elements,
			final String separator) {
		final StringJoiner joiner = new StringJoiner(separator);
		for (final ProgramElementInfo element : elements) {
			joiner.add(element.getText());
		}
		return joiner.toString();
	}

	/** 挿入待ちの文を取り出し、待ち行列を空にする。 */
	List<StatementInfo> drainPendingStatements() {
		if (this.pendingStatements.isEmpty()) {
			return List.of();
		}
		final List<StatementInfo> drained = List.copyOf(this.pendingStatements);
		this.pendingStatements.clear();
		return drained;
	}

	/** スタック上で最も内側にあるブロックを返す。 */
	ProgramElementInfo nearestBlock() {
		for (final ProgramElementInfo element : this.stack) {
			if (element instanceof BlockInfo) {
				return element;
			}
		}
		return this.stack.isEmpty() ? null : this.stack.peek();
	}

	/**
	 * この switch 式を、それを含む文の前へ出しても評価順が変わらないか。
	 */
	static boolean canHoist(final ASTNode node) {

		ASTNode child = node;
		for (ASTNode parent = node.getParent(); null != parent; child = parent, parent = parent
				.getParent()) {

			// 短絡評価の右辺や三項演算子の枝は、そもそも評価されないことがある。
			if (parent instanceof InfixExpression) {
				final InfixExpression.Operator operator = ((InfixExpression) parent)
						.getOperator();
				if (InfixExpression.Operator.CONDITIONAL_AND == operator
						|| InfixExpression.Operator.CONDITIONAL_OR == operator) {
					return false;
				}
			}
			if (parent instanceof ConditionalExpression) {
				return false;
			}
			// 別の入れ子の内側からは外へ出せない。
			if (parent instanceof LambdaExpression
					|| parent instanceof SwitchExpression) {
				return false;
			}

			if (parent instanceof Statement) {
				// ループの条件と更新式は繰り返し評価される。
				if (parent instanceof ForStatement) {
					final ForStatement statement = (ForStatement) parent;
					if (child == statement.getExpression()
							|| statement.updaters().contains(child)) {
						return false;
					}
				}
				if (parent instanceof WhileStatement
						&& child == ((WhileStatement) parent).getExpression()) {
					return false;
				}
				if (parent instanceof DoStatement
						&& child == ((DoStatement) parent).getExpression()) {
					return false;
				}
				if (parent instanceof EnhancedForStatement
						&& child == ((EnhancedForStatement) parent).getExpression()) {
					return false;
				}
				// 挿入先のブロックが要る。
				return parent.getParent() instanceof Block;
			}
		}
		return false;
	}

	int getStartLineNumber(final ASTNode node) {
		return root.getLineNumber(node.getStartPosition());
	}

	int getEndLineNumber(final ASTNode node) {
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
	ProgramElementVisitor(final String path, final CompilationUnit root,
			final List<MethodInfo> methods) {
		this.path = path;
		this.root = root;
		this.methods = methods;
		this.stack = new ArrayDeque<>();
	}
}
