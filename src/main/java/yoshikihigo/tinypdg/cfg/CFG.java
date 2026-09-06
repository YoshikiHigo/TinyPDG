package yoshikihigo.tinypdg.cfg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.TinyPDGException;
import yoshikihigo.tinypdg.cfg.edge.CFGControlEdge;
import yoshikihigo.tinypdg.cfg.edge.CFGEdge;
import yoshikihigo.tinypdg.cfg.node.CFGBreakStatementNode;
import yoshikihigo.tinypdg.cfg.node.CFGContinueStatementNode;
import yoshikihigo.tinypdg.cfg.node.CFGJumpStatementNode;
import yoshikihigo.tinypdg.cfg.node.CFGNode;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.cfg.node.CFGPseudoNode;
import yoshikihigo.tinypdg.cfg.node.CFGSwitchCaseNode;
import yoshikihigo.tinypdg.pe.BlockInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.BlockStatementInfo;
import yoshikihigo.tinypdg.pe.ConditionalStatementInfo;
import yoshikihigo.tinypdg.pe.ForStatementInfo;
import yoshikihigo.tinypdg.pe.IfStatementInfo;
import yoshikihigo.tinypdg.pe.TryStatementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;

public class CFG {

	final public ProgramElementInfo core;

	final private CFGNodeFactory nodeFactory;

	final protected SortedSet<CFGNode<? extends ProgramElementInfo>> nodes;

	protected CFGNode<? extends ProgramElementInfo> enterNode;

	final protected Set<CFGNode<? extends ProgramElementInfo>> exitNodes;

	/** 飛び越えの種類。EXIT は return と throw で、メソッドの外へ出る。 */
	enum JUMP {
		BREAK, CONTINUE, EXIT
	}

	/**
	 * 行き先がまだ決まっていない飛び越え。
	 *
	 * <p>break と continue はそれを囲むループや switch が、return と throw は
	 * メソッドが行き先を決めるので、そこへ着くまで外側へ渡していく。途中に
	 * finally のある try があれば、飛び越えはまず finally へ進み、finally の
	 * 出口を始点にした同じ種類の飛び越えとして先へ渡される。
	 *
	 * <p>以前は break、continue、return と throw のノードを別々のリストで
	 * 持っていた。始点が飛び越えの文そのものに限られていたので、finally を
	 * 通った後の続きを表せなかった。
	 *
	 * @param kind  種類
	 * @param label break と continue のラベル。なければ null
	 * @param from  辺の始点。飛び越えの文のノードか、finally を通った後は
	 *              finally の出口のノード
	 */
	record PendingJump(JUMP kind, String label,
			CFGNode<? extends ProgramElementInfo> from) {
	}

	final protected LinkedList<PendingJump> pendingJumps;

	protected boolean built;

	/**
	 * 別の CFG の部分グラフとして組み立てられているか。
	 *
	 * <p>疑似ノードは、グラフ全体ができてから消す。以前は文ごとの CFG が
	 * それぞれ自分の疑似ノードを消していた。すると if (c) {} の空の本体の
	 * 疑似ノードは「条件から真の辺で進む先」という情報を持ったまま消え、
	 * 残った条件ノードから次の文へ引く辺は偽になっていた。
	 */
	final private boolean nested;

	public CFG(final ProgramElementInfo core, final CFGNodeFactory nodeFactory) {
		this(core, nodeFactory, false);
	}

	private CFG(final ProgramElementInfo core, final CFGNodeFactory nodeFactory,
			final boolean nested) {
		Objects.requireNonNull(nodeFactory, "\"nodeFactory\" is null.");
		this.core = core;
		this.nodeFactory = nodeFactory;
		this.nested = nested;
		this.nodes = new TreeSet<>();
		this.enterNode = null;
		this.exitNodes = new TreeSet<>();
		this.built = false;

		this.pendingJumps = new LinkedList<>();
	}

	public boolean isEmpty() {
		return 0 == this.nodes.size();
	}

	public CFGNode<? extends ProgramElementInfo> getEnterNode() {
		return this.enterNode;
	}

	public SortedSet<CFGNode<? extends ProgramElementInfo>> getExitNodes() {
		final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes = new TreeSet<>();
		nodes.addAll(this.exitNodes);
		return nodes;
	}

	public SortedSet<CFGNode<? extends ProgramElementInfo>> getAllNodes() {
		final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes = new TreeSet<>();
		nodes.addAll(this.nodes);
		return nodes;
	}

	/**
	 * case ラベルのノードを消し、条件から直接アームへ繋ぐ。
	 *
	 * <p>ただしパターンを持つラベル (case String s ->、case Circle(double r)
	 * when r > 0 ->) は残す。値の比較ではなく変数の束縛なので、消すと s を
	 * 定義するノードがなくなり、アームの中で s を使う文にデータ依存の辺が
	 * 出なかった。case 1: や case RED: のような定数のラベルは今までどおり消す。
	 */
	public void removeSwitchCases() {
		final Iterator<CFGNode<? extends ProgramElementInfo>> iterator = this.nodes
				.iterator();
		while (iterator.hasNext()) {
			final CFGNode<? extends ProgramElementInfo> node = iterator.next();
			if (node instanceof CFGSwitchCaseNode && !bindsVariables(node)) {

				this.replaceExitNode(node);
				for (final CFGEdge edge : node.getBackwardEdges()) {
					final CFGNode<?> fromNode = edge.fromNode;

					for (final CFGNode<?> toNode : node.getForwardNodes()) {
						if (edge instanceof CFGControlEdge controlEdge) {
							connect(fromNode, toNode, controlEdge.control);
						} else {
							connect(fromNode, toNode);
						}
					}
				}

				node.remove();
				iterator.remove();
			}
		}
	}

	public void removeJumpStatements() {
		final Iterator<CFGNode<? extends ProgramElementInfo>> iterator = this.nodes
				.iterator();
		while (iterator.hasNext()) {
			final CFGNode<? extends ProgramElementInfo> node = iterator.next();
			if (node instanceof CFGJumpStatementNode) {

				// ループの最後の break はメソッドの出口でもある。ノードが消えた
				// 後も出口の集合に残っていて、PDG はグラフに現れない出口を作って
				// いた。
				this.replaceExitNode(node);
				// 条件ノードから来た辺なら、その真偽を jump の辺に持たせる。
				for (final CFGEdge backwardEdge : node.getBackwardEdges()) {
					for (final CFGNode<?> toNode : node.getForwardNodes()) {
						CFGEdge.makeJumpEdge(backwardEdge.fromNode, toNode,
								backwardEdge.getControl()).connect();
					}
				}

				node.remove();
				iterator.remove();
			}
		}
	}

	public void build() {

		assert !this.built : "this CFG has already built.";
		this.built = true;

		if (null == this.core) {
			final CFGNode<? extends ProgramElementInfo> node = nodeFactory
					.makeNormalNode(null);
			this.nodes.add(node);
			this.enterNode = node;
			this.exitNodes.add(node);
		}

		else if (this.core instanceof StatementInfo) {
			final StatementInfo coreStatement = (StatementInfo) this.core;
			// 種別で振り分けるが、渡すのはその文が実際に持っている状態を
			// 表す型である。for に更新式があり try に catch 節があることが、
			// 受け取る側のシグネチャに書いてある。
			// 中身を展開して部分グラフを作る文と、それ自体が 1 ノードに
			// なる文とに分かれる。switch 式なので全ての種別に枝が要る。
			// 種別を足すとここでビルドが止まり、どちらなのかを決めることに
			// なる。文のままだと黙って「1 ノード」に倒れる。
			//
			// 渡すのはその文が実際に持っている状態を表す型である。for に
			// 更新式があり try に catch 節があることが、受け取る側の
			// シグネチャに書いてある。
			final boolean expanded = switch (coreStatement.getCategory()) {
			case Catch, Synchronized -> {
				this.buildConditionalBlockCFG(
						(ConditionalStatementInfo) coreStatement, false);
				yield true;
			}
			case Do -> {
				this.buildDoBlockCFG((ConditionalStatementInfo) coreStatement);
				yield true;
			}
			case For -> {
				this.buildForBlockCFG((ForStatementInfo) coreStatement);
				yield true;
			}
			case Foreach -> {
				this.buildConditionalBlockCFG(
						(ConditionalStatementInfo) coreStatement, true);
				yield true;
			}
			case If -> {
				this.buildIfBlockCFG((IfStatementInfo) coreStatement);
				yield true;
			}
			case Switch -> {
				this.buildSwitchBlockCFG(
						(ConditionalStatementInfo) coreStatement);
				yield true;
			}
			case Try -> {
				this.buildTryBlockCFG((TryStatementInfo) coreStatement);
				yield true;
			}
			case While -> {
				this.buildConditionalBlockCFG(
						(ConditionalStatementInfo) coreStatement, true);
				yield true;
			}
			// ブロックは中身を並べたものである。ここへ来るのは、ラベル付きの
			// ブロック、finally ブロック、空のブロックで、それ以外は visitor が
			// 親の並びに平らにしている。以前は 1 個の不透明なノードだったので、
			// finally の中の依存が見えず、ラベルへの break は行き先を失っていた。
			case SimpleBlock -> {
				this.buildSimpleBlockCFG((BlockStatementInfo) coreStatement);
				this.connectCFGBreakStatementNode(coreStatement, false);
				yield true;
			}
			// 型宣言は制御フローを持たない。ノードも作らない。
			case TypeDeclaration -> true;
			case Assert, Break, Case,
					Continue, Empty, Expression,
					Return, Throw, VariableDeclaration,
					Yield, Unsupported -> false;
			};

			if (!expanded) {
				final CFGNode<? extends ProgramElementInfo> node = this.nodeFactory
						.makeNormalNode(coreStatement);
				this.enterNode = node;
				if (node instanceof CFGBreakStatementNode) {
					this.pendingJumps.add(new PendingJump(JUMP.BREAK,
							coreStatement.getJumpToLabel(), node));
				} else if (node instanceof CFGContinueStatementNode) {
					this.pendingJumps.add(new PendingJump(JUMP.CONTINUE,
							coreStatement.getJumpToLabel(), node));
				} else if (leavesTheMethod(coreStatement)) {
					this.pendingJumps.add(new PendingJump(JUMP.EXIT, null, node));
				} else {
					this.exitNodes.add(node);
				}
				this.nodes.add(node);
			}
		}

		else if (this.core instanceof ExpressionInfo) {
			final ProgramElementInfo coreExpression = this.core;
			final CFGNode<? extends ProgramElementInfo> node = this.nodeFactory
					.makeNormalNode(coreExpression);
			this.enterNode = node;
			this.exitNodes.add(node);
			this.nodes.add(node);
		}

		else if (this.core instanceof MethodInfo coreMethod) {
			this.buildSimpleBlockCFG(coreMethod);
			// メソッドの出口は、本体の最後の文に加えて、途中の return と throw
			// (finally を通ったなら、その出口)。行き先の見つからなかった break と
			// continue は捨てる。正しいソースにはない。
			for (final PendingJump jump : this.pendingJumps) {
				if (JUMP.EXIT == jump.kind()) {
					this.exitNodes.add(jump.from());
				}
			}
			this.pendingJumps.clear();
		}

		else {
			throw new TinyPDGException(
					"CFG を組み立てられない要素です: " + this.core.getClass().getName());
		}

		if (!this.nested) {
			this.removePseudoNodes();
		}
	}

	private void buildDoBlockCFG(final ConditionalStatementInfo statement) {

		final SequentialCFGs sequentialCFGs = new SequentialCFGs(
				statement.getStatements());
		sequentialCFGs.build();
		final ProgramElementInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);

		this.enterNode = sequentialCFGs.enterNode;
		this.absorb(sequentialCFGs);
		this.nodes.add(conditionNode);
		this.exitNodes.add(conditionNode);

		for (final CFGNode<?> exitNode : sequentialCFGs.exitNodes) {
			connect(exitNode, conditionNode);
		}
		connect(conditionNode, sequentialCFGs.enterNode, true);

		this.connectCFGBreakStatementNode(statement, true);
		// do-while の continue は条件の評価へ飛ぶ。以前は本体の先頭へ戻して
		// いて、条件を通らずにもう一周することになっていた。
		this.connectCFGContinueStatementNode(statement, conditionNode);
	}

	private void buildForBlockCFG(final ForStatementInfo statement) {

		final SequentialCFGs sequentialCFGs = new SequentialCFGs(
				statement.getStatements());
		sequentialCFGs.build();

		final List<ProgramElementInfo> initializers = statement
				.getInitializers();
		final ProgramElementInfo condition = statement.getCondition();
		final List<ProgramElementInfo> updaters = statement.getUpdaters();

		final SequentialCFGs initializerCFGs = new SequentialCFGs(initializers);
		initializerCFGs.build();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);
		final SequentialCFGs updaterCFGs = new SequentialCFGs(updaters);
		updaterCFGs.build();

		this.enterNode = initializerCFGs.enterNode;
		// 条件のない for (;;) は条件から抜けることがない。出口は break だけで
		// ある。以前は疑似ノードの条件も出口にしていて、疑似ノードが消える
		// ときにその前のノードが出口として残った。
		if (null != condition) {
			this.exitNodes.add(conditionNode);
		}
		// 初期化式と更新式は式なので、break も continue も持ち込まない。
		this.absorb(sequentialCFGs);
		this.absorb(initializerCFGs);
		this.nodes.add(conditionNode);
		this.absorb(updaterCFGs);

		for (final CFGNode<? extends ProgramElementInfo> initializerExitNode : initializerCFGs.exitNodes) {
			connect(initializerExitNode, conditionNode);
		}
		connect(conditionNode, sequentialCFGs.enterNode, true);

		for (final CFGNode<? extends ProgramElementInfo> sequentialExitNode : sequentialCFGs.exitNodes) {
			connect(sequentialExitNode, updaterCFGs.enterNode);
		}

		for (final CFGNode<? extends ProgramElementInfo> updaterExitNode : updaterCFGs.exitNodes) {
			connect(updaterExitNode, conditionNode);
		}

		this.connectCFGBreakStatementNode(statement, true);
		// continue は更新式を実行してから条件へ戻る。以前は条件へ直接繋いで
		// いて、continue の経路では i++ が実行されないことになっていた。
		// 更新式がなければ enterNode は疑似ノードで、消えるときに条件へ繋がる。
		this.connectCFGContinueStatementNode(statement, updaterCFGs.enterNode);
	}

	private void buildConditionalBlockCFG(
			final ConditionalStatementInfo statement,
			final boolean loop) {

		final List<StatementInfo> substatements = statement.getStatements();
		final SequentialCFGs sequentialCFGs = new SequentialCFGs(substatements);
		sequentialCFGs.build();
		final ProgramElementInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);

		this.enterNode = conditionNode;
		this.absorb(sequentialCFGs);
		this.nodes.add(conditionNode);
		if (loop) {
			this.exitNodes.add(conditionNode);
		} else {
			// 中身が空でも SequentialCFGs は疑似ノードを 1 個作るので、出口は
			// それでよい。以前は条件そのものを出口に足していて、次の文への辺が
			// 条件ノードからの辺の既定である偽になり、真の枝が消えていた。
			this.exitNodes.addAll(sequentialCFGs.exitNodes);
		}

		connect(conditionNode, sequentialCFGs.enterNode, true);

		if (loop) {
			for (final CFGNode<?> exitNode : sequentialCFGs.exitNodes) {
				if (exitNode instanceof CFGBreakStatementNode) {
					this.exitNodes.add(exitNode);
				} else {
					connect(exitNode, conditionNode);
				}
			}

			this.connectCFGBreakStatementNode(statement, true);
			this.connectCFGContinueStatementNode(statement, conditionNode);
		}
	}

	private void buildIfBlockCFG(final IfStatementInfo statement) {

		this.buildConditionalBlockCFG(statement, false);

		final ProgramElementInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);

		// else 節がなければ getElseStatements() は空のリストで (null には
		// ならない)、SequentialCFGs は疑似ノードを 1 個作る。条件から偽の辺で
		// そこへ進み、疑似ノードが消えるときに次の文へ繋がる。以前は中身が
		// 空だと条件そのものを出口にしていた。
		final SequentialCFGs elseCFG = new SequentialCFGs(
				statement.getElseStatements());
		elseCFG.build();

		this.absorb(elseCFG);
		this.exitNodes.addAll(elseCFG.exitNodes);
		connect(conditionNode, elseCFG.enterNode, false);
	}

	private void buildSimpleBlockCFG(final BlockInfo statement) {
		final List<StatementInfo> substatements = statement.getStatements();
		final SequentialCFGs sequentialCFGs = new SequentialCFGs(substatements);
		sequentialCFGs.build();

		this.enterNode = sequentialCFGs.enterNode;
		this.exitNodes.addAll(sequentialCFGs.exitNodes);
		this.absorb(sequentialCFGs);
	}

	private void buildSwitchBlockCFG(final ConditionalStatementInfo statement) {

		final ProgramElementInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);
		this.enterNode = conditionNode;
		this.nodes.add(conditionNode);

		final List<StatementInfo> substatements = statement.getStatements();
		final List<CFG> sequentialCFGs = new ArrayList<>();
		for (final StatementInfo substatement : substatements) {
			final CFG subCFG = new CFG(substatement, this.nodeFactory, true);
			subCFG.build();
			sequentialCFGs.add(subCFG);
			this.absorb(subCFG);

			final boolean exitsTheSwitch = switch (substatement.getCategory()) {
			case Case -> {
				// ラベルには条件から直接繋ぐ。
				connect(conditionNode, subCFG.enterNode, true);
				yield false;
			}
			case Break, Continue, Return, Throw -> true;
			// 直前の文から順に繋がる。ここで足すことはない。
			case Assert, Catch, Do,
					Empty, Expression, If,
					For, Foreach, SimpleBlock,
					Synchronized, Switch, Try,
					TypeDeclaration, VariableDeclaration, While,
					Yield, Unsupported -> false;
			};

			if (exitsTheSwitch) {
				this.exitNodes.addAll(subCFG.exitNodes);
			}
		}

		CFG: for (int index = 1; index < sequentialCFGs.size(); index++) {
			final CFG anteriorCFG = sequentialCFGs.get(index - 1);
			final CFG posteriorCFG = sequentialCFGs.get(index);

			final ProgramElementInfo anteriorCore = anteriorCFG.core;
			if (anteriorCore instanceof StatementInfo anteriorStatement) {
				// break、continue、return、throw は次の文へ流れない。
				final boolean fallsThrough = switch (anteriorStatement
						.getCategory()) {
				case Break, Continue, Return, Throw -> false;
				case Assert, Case, Catch,
						Do, Empty, Expression,
						If, For, Foreach,
						SimpleBlock, Synchronized, Switch,
						Try, TypeDeclaration, VariableDeclaration,
						While, Yield, Unsupported -> true;
				};
				if (!fallsThrough) {
					continue CFG;
				}
			}

			for (final CFGNode<? extends ProgramElementInfo> anteriorExitNode : anteriorCFG.exitNodes) {
				connect(anteriorExitNode, posteriorCFG.enterNode);
			}
		}

		// 中身のない switch (x) { } では条件からそのまま次へ流れる。以前は
		// 最後の文を取ろうとして IndexOutOfBoundsException になっていた。
		if (sequentialCFGs.isEmpty()) {
			this.exitNodes.add(conditionNode);
		} else {
			this.exitNodes.addAll(sequentialCFGs.getLast().exitNodes);
		}

		// default のない switch には、どの case にも合わずに素通りする経路が
		// ある。条件から直接 switch の後ろへ出る。default はラベルの式を持たない
		// case として届く。ただし構文から網羅的だと分かる switch (switch 式と、
		// パターンか null のラベルを持つ switch 文) には素通りの経路がない。
		// 従来型の switch 文が enum の全定数を並べていても、型を見ないここでは
		// 分からず、言語の意味としても素通りしうる。
		final boolean hasDefault = substatements.stream().anyMatch(
				s -> StatementInfo.CATEGORY.Case == s.getCategory()
						&& s.getExpressions().isEmpty());
		if (!hasDefault && !statement.isExhaustive()) {
			this.exitNodes.add(conditionNode);
		}

		this.connectCFGBreakStatementNode(statement, true);
	}

	private void buildTryBlockCFG(final TryStatementInfo statement) {

		final List<StatementInfo> statements = statement.getStatements();
		final SequentialCFGs sequentialCFGs = new SequentialCFGs(statements);
		sequentialCFGs.build();

		final StatementInfo finallyBlock = statement.getFinallyStatement();
		final CFG finallyCFG = new CFG(finallyBlock, this.nodeFactory, true);
		finallyCFG.build();

		this.enterNode = sequentialCFGs.enterNode;
		this.absorb(sequentialCFGs);
		for (final CFGNode<? extends ProgramElementInfo> sequentialExitNode : sequentialCFGs.exitNodes) {
			connect(sequentialExitNode, finallyCFG.enterNode);
		}

		for (final StatementInfo catchStatement : statement
				.getCatchStatements()) {

			final CFG catchCFG = new CFG(catchStatement, this.nodeFactory, true);
			catchCFG.build();

			// catch 節の中の break と continue も、外側のループが行き先を
			// 決める。以前はノードだけを引き取り、ジャンプは捨てていたので、
			// catch 節の中の break はどこにも繋がらなかった。
			this.absorb(catchCFG);
			for (final CFGNode<? extends ProgramElementInfo> catchExitNode : catchCFG.exitNodes) {
				connect(catchExitNode, finallyCFG.enterNode);
			}
		}

		// finally は合流点である。try 本体と catch 節から出る飛び越え (return、
		// throw、break、continue) は、種類を問わずまず finally へ進み、finally の
		// 出口を始点にした同じ種類の飛び越えとして外側へ渡す。経路は区別しない
		// ので、finally の後には通常経路の次の文と飛び越えの行き先の両方が続く。
		// 経路ごとに finally を複製すれば正確になるが、文 1 つにノード 1 つと
		// いう同一性を崩すことになる。
		//
		// 以前は return と throw だけを finally へ通し、その後は次の文へしか
		// 流れず、break と continue は finally を飛ばしてループへ直接届いていた。
		if (null != finallyBlock) {
			final List<PendingJump> jumps = new ArrayList<>(this.pendingJumps);
			this.pendingJumps.clear();
			final Set<PendingJump> passed = new LinkedHashSet<>();
			for (final PendingJump jump : jumps) {
				connect(jump.from(), finallyCFG.enterNode);
				for (final CFGNode<? extends ProgramElementInfo> finallyExitNode : finallyCFG.exitNodes) {
					passed.add(new PendingJump(jump.kind(), jump.label(),
							finallyExitNode));
				}
			}
			this.pendingJumps.addAll(passed);
		}

		this.absorb(finallyCFG);
		this.exitNodes.addAll(finallyCFG.exitNodes);
	}

	/**
	 * 部分グラフのノードと、まだ行き先の決まっていない飛び越えを引き取る。
	 * 入口と出口は文の種類ごとに決め方が違うので、呼ぶ側が扱う。
	 *
	 * <p>private にすると SequentialCFGs から呼べない。private なメソッドは
	 * 継承されないので、サブクラスの this からは見つからない。
	 */
	void absorb(final CFG sub) {
		this.nodes.addAll(sub.nodes);
		this.pendingJumps.addAll(sub.pendingJumps);
	}

	/** return と throw。メソッド (と、あれば finally) の外へ出る文。 */
	private static boolean leavesTheMethod(final StatementInfo statement) {
		return switch (statement.getCategory()) {
		case Return, Throw -> true;
		case Assert, Break, Case,
				Catch, Continue, Do,
				Empty, Expression, For,
				Foreach, If, SimpleBlock,
				Switch, Synchronized, Try,
				TypeDeclaration, VariableDeclaration, While,
				Yield, Unsupported -> false;
		};
	}

	/** from から to へ辺を張る。 */
	private static void connect(final CFGNode<?> from, final CFGNode<?> to) {
		CFGEdge.makeEdge(from, to).connect();
	}

	/** 条件ノードから、条件が control のときに進む to へ辺を張る。 */
	private static void connect(final CFGNode<?> from, final CFGNode<?> to,
			final boolean control) {
		CFGEdge.makeEdge(from, to, control).connect();
	}

	/** パターン変数を束縛する case ラベルか。 */
	private static boolean bindsVariables(final CFGNode<?> node) {
		return !node.core.getAssignedVariables().isEmpty();
	}

	/** node が出口なら、その前のノードたちを代わりの出口にする。 */
	private void replaceExitNode(final CFGNode<? extends ProgramElementInfo> node) {
		if (this.exitNodes.remove(node)) {
			this.exitNodes.addAll(node.getBackwardNodes());
		}
	}

	private void removePseudoNodes() {

		final Iterator<CFGNode<? extends ProgramElementInfo>> iterator = this.nodes
				.iterator();
		while (iterator.hasNext()) {

			final CFGNode<? extends ProgramElementInfo> node = iterator.next();
			if (node instanceof CFGPseudoNode) {

				iterator.remove();

				if (0 == node.compareTo(this.enterNode)) {
					if (0 < this.enterNode.getForwardEdges().size()) {
						this.enterNode = this.enterNode.getForwardNodes()
								.first();
					} else {
						this.enterNode = null;
					}
				}

				this.replaceExitNode(node);

				final SortedSet<CFGEdge> backwardEdges = node.getBackwardEdges();
				final SortedSet<CFGNode<? extends ProgramElementInfo>> forwardNodes = node
						.getForwardNodes();
				node.remove();

				// 条件ノードから来た辺は真偽を持つので、それを引き継いで繋ぎ直す。
				// 以前はノードだけを見て繋いでいたので、条件ノードからの辺は
				// 既定の偽になり、if (c) {} の真の枝が偽の辺として現れていた。
				for (final CFGEdge backwardEdge : backwardEdges) {
					for (final CFGNode<? extends ProgramElementInfo> forwardNode : forwardNodes) {
						if (backwardEdge instanceof CFGControlEdge controlEdge) {
							connect(controlEdge.fromNode, forwardNode,
									controlEdge.control);
						} else {
							connect(backwardEdge.fromNode, forwardNode);
						}
					}
				}
			}
		}
	}

	/**
	 * まだ行き先の決まっていない break のうち、この文で終わるものを出口にする。
	 *
	 * @param acceptsUnlabeled ラベルのない break もこの文で終わるか。ループと
	 *                         switch では真。ラベル付きのブロックは、ラベルで
	 *                         名指しされた break しか受けないので偽
	 */
	private void connectCFGBreakStatementNode(final StatementInfo statement,
			final boolean acceptsUnlabeled) {

		final Iterator<PendingJump> iterator = this.pendingJumps.iterator();
		while (iterator.hasNext()) {
			final PendingJump jump = iterator.next();
			if (JUMP.BREAK != jump.kind()) {
				continue;
			}
			final boolean endsHere = null == jump.label() ? acceptsUnlabeled
					: jump.label().equals(statement.getLabel());
			if (endsHere) {
				this.exitNodes.add(jump.from());
				iterator.remove();
			}
		}
	}

	private void connectCFGContinueStatementNode(final StatementInfo statement,
			final CFGNode<? extends ProgramElementInfo> destinationNode) {

		final Iterator<PendingJump> iterator = this.pendingJumps.iterator();
		while (iterator.hasNext()) {
			final PendingJump jump = iterator.next();
			if (JUMP.CONTINUE != jump.kind()) {
				continue;
			}
			// ラベルのない continue は最も内側のループへ、ラベル付きは名指しの
			// ループへ飛ぶ。
			final boolean endsHere = null == jump.label()
					|| jump.label().equals(statement.getLabel());
			if (endsHere) {
				connect(jump.from(), destinationNode);
				iterator.remove();
			}
		}
	}

	private class SequentialCFGs extends CFG {

		final List<? extends ProgramElementInfo> elements;

		SequentialCFGs(final List<? extends ProgramElementInfo> elements) {

			super(null, CFG.this.nodeFactory, true);
			this.elements = elements;
		}

		@Override
		public void build() {

			assert !this.built : "this CFG has already built.";
			this.built = true;

			final LinkedList<CFG> sequentialCFGs = new LinkedList<>();
			for (final ProgramElementInfo element : this.elements) {
				final CFG blockCFG = new CFG(element, CFG.this.nodeFactory, true);
				blockCFG.build();
				if (!blockCFG.isEmpty()) {
					sequentialCFGs.add(blockCFG);
				}
			}
			for (int index = 1; index < sequentialCFGs.size(); index++) {
				final CFG anteriorCFG = sequentialCFGs.get(index - 1);
				final CFG posteriorCFG = sequentialCFGs.get(index);
				for (final CFGNode<?> exitNode : anteriorCFG.exitNodes) {
					connect(exitNode, posteriorCFG.enterNode);
				}
			}
			if (0 == sequentialCFGs.size()) {
				final CFG pseudoCFG = new CFG(null, CFG.this.nodeFactory, true);
				pseudoCFG.build();
				sequentialCFGs.add(pseudoCFG);
			}

			this.enterNode = sequentialCFGs.getFirst().enterNode;
			this.exitNodes.addAll(sequentialCFGs.getLast().exitNodes);
			for (final CFG cfg : sequentialCFGs) {
				this.absorb(cfg);
			}
		}
	}

	/**
	 * startNode から順方向の辺で到達できるノード。startNode 自身を含む。
	 *
	 * <p>再帰ではなく作業リストで巡る。長いメソッドでは経路の長さのぶんだけ
	 * 再帰が深くなり、StackOverflowError になっていた。
	 */
	public final SortedSet<CFGNode<? extends ProgramElementInfo>> getReachableNodes(
			final CFGNode<? extends ProgramElementInfo> startNode) {
		Objects.requireNonNull(startNode, "\"startNode\" is null.");

		final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes = new TreeSet<>();
		final Deque<CFGNode<? extends ProgramElementInfo>> worklist = new ArrayDeque<>();
		worklist.push(startNode);
		while (!worklist.isEmpty()) {
			final CFGNode<? extends ProgramElementInfo> node = worklist.pop();
			if (!nodes.add(node)) {
				continue;
			}
			for (final CFGNode<? extends ProgramElementInfo> forwardNode : node
					.getForwardNodes()) {
				worklist.push(forwardNode);
			}
		}
		return nodes;
	}
}
