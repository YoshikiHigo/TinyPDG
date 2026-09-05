package yoshikihigo.tinypdg.pdg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.cfg.CFG;
import yoshikihigo.tinypdg.cfg.edge.CFGEdge;
import yoshikihigo.tinypdg.cfg.node.CFGNode;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.edge.PDGControlDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGDataDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGExecutionDependenceEdge;
import yoshikihigo.tinypdg.pdg.node.PDGControlNode;
import yoshikihigo.tinypdg.pdg.node.PDGMethodEnterNode;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pdg.node.PDGParameterNode;
import yoshikihigo.tinypdg.pe.BlockInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.BlockStatementInfo;
import yoshikihigo.tinypdg.pe.ConditionalStatementInfo;
import yoshikihigo.tinypdg.pe.ForStatementInfo;
import yoshikihigo.tinypdg.pe.IfStatementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.VariableInfo;

public class PDG implements Comparable<PDG> {

	final private PDGNodeFactory pdgNodeFactory;
	final private CFGNodeFactory cfgNodeFactory;

	final public PDGMethodEnterNode enterNode;
	final private SortedSet<PDGNode<?>> exitNodes;
	final private List<PDGParameterNode> parameterNodes;

	final public MethodInfo unit;

	final public Dependences dependences;

	private CFG cfg;

	/**
	 * PDG に何を含めるか。
	 *
	 * <p>以前はコンストラクタの引数として並んでいた。真偽値が 3 つ続くので、
	 * 呼び出し側は {@code new PDG(method, f1, f2, true, true, true)} となり、
	 * どれがどの依存か読み取れなかった。
	 *
	 * <p>制御依存にも距離の引数があったが、比較に使われている場所がなく、
	 * 渡しても何も起こらなかった。距離を見て辺を落としているのはデータ依存と
	 * 実行依存だけなので、その 2 つだけを残してある。
	 *
	 * @param control           制御依存の辺を作るか
	 * @param data              データ依存の辺を作るか
	 * @param execution         実行依存の辺を作るか
	 * @param dataDistance      データ依存を作る行数の上限
	 * @param executionDistance 実行依存を作る行数の上限
	 */
	public record Dependences(boolean control, boolean data, boolean execution,
			int dataDistance, int executionDistance) {

		/** 3 種類すべてを、距離の制限なしで作る。 */
		public static final Dependences ALL = new Dependences(true, true, true);

		public Dependences {
			if (dataDistance < 1 || executionDistance < 1) {
				throw new IllegalArgumentException(
						"距離は 1 以上でなければならない: data=" + dataDistance
								+ " execution=" + executionDistance);
			}
		}

		/** 距離を制限せずに作る。 */
		public Dependences(final boolean control, final boolean data,
				final boolean execution) {
			this(control, data, execution, Integer.MAX_VALUE,
					Integer.MAX_VALUE);
		}
	}

	public PDG(final MethodInfo unit, final PDGNodeFactory pdgNodeFactory,
			final CFGNodeFactory cfgNodeFactory,
			final Dependences dependences) {

		Objects.requireNonNull(unit, "\"unit\" is null");
		Objects.requireNonNull(pdgNodeFactory, "\"pdgNodeFactory\" is null");
		Objects.requireNonNull(cfgNodeFactory, "\"cfgNodeFactory\" is null");
		Objects.requireNonNull(dependences, "\"dependences\" is null");

		this.unit = unit;
		this.pdgNodeFactory = pdgNodeFactory;
		this.cfgNodeFactory = cfgNodeFactory;
		this.dependences = dependences;

		this.enterNode = (PDGMethodEnterNode) this.pdgNodeFactory
				.makeControlNode(unit);
		this.exitNodes = new TreeSet<>();
		this.parameterNodes = new ArrayList<>();
		for (final VariableInfo variable : unit.getParameters()) {
			final PDGParameterNode parameterNode = (PDGParameterNode) this.pdgNodeFactory
					.makeNormalNode(variable);
			this.parameterNodes.add(parameterNode);
		}
	}

	public PDG(final MethodInfo unit, final PDGNodeFactory pdgNodeFactory,
			final CFGNodeFactory cfgNodeFactory) {
		this(unit, pdgNodeFactory, cfgNodeFactory, Dependences.ALL);
	}

	public PDG(final MethodInfo unit) {
		this(unit, new PDGNodeFactory(), new CFGNodeFactory(), Dependences.ALL);
	}

	@Override
	public int compareTo(final PDG o) {
		Objects.requireNonNull(o, "\"o\" is null.");
		return this.unit.compareTo(o.unit);
	}

	public final SortedSet<PDGNode<?>> getExitNodes() {
		final SortedSet<PDGNode<?>> nodes = new TreeSet<>();
		nodes.addAll(this.exitNodes);
		return nodes;
	}

	public final List<PDGParameterNode> getParameterNodes() {
		final List<PDGParameterNode> parameters = new ArrayList<>();
		parameters.addAll(this.parameterNodes);
		return parameters;
	}

	/** ノードを作るときに使っているファクトリ。ノードの併合が対応表を直すのに使う。 */
	public final PDGNodeFactory getPDGNodeFactory() {
		return this.pdgNodeFactory;
	}

	/**
	 * このグラフのノードを全て返す。
	 *
	 * <p>入口ノード、パラメータのノード、CFG の各ノードに対応する PDG ノード、
	 * そしてそれらから辺を辿って届くノードである。
	 *
	 * <p>以前は入口から辺を辿るだけだった。入口に繋がるのは制御依存の辺なので、
	 * 制御依存を作らない設定にすると入口が孤立し、データ依存の辺が正しく
	 * 作られていてもグラフが空に見えていた。Scorpio の -C off が何も返さな
	 * かったのはこれである。CFG のノードを種に加えることで、入口から切れて
	 * いても本体のノードが見つかる。
	 *
	 * <p>辺を辿る walk は残してある。PDG には CFG のノードに対応しない
	 * ノードもあるからである。for の更新式や foreach が取り出す変数は、
	 * CFG のノードではなく制御依存を組み立てる過程で作られる。種を CFG だけに
	 * すると、それらが落ちる。
	 */
	public final SortedSet<PDGNode<?>> getAllNodes() {

		final SortedSet<PDGNode<?>> nodes = new TreeSet<>();

		this.collectFrom(this.enterNode, nodes);
		for (final PDGParameterNode parameterNode : this.parameterNodes) {
			this.collectFrom(parameterNode, nodes);
		}

		// build する前は CFG がまだない。
		if (null != this.cfg) {
			for (final CFGNode<?> cfgNode : this.cfg.getAllNodes()) {
				this.collectFrom(this.pdgNodeFactory.makeNode(cfgNode), nodes);
			}
		}

		return nodes;
	}

	private void collectFrom(final PDGNode<?> node,
			final SortedSet<PDGNode<?>> nodes) {

		Objects.requireNonNull(node, "\"node\" is null.");

		if (!nodes.add(node)) {
			return;
		}

		for (final PDGEdge edge : node.getBackwardEdges()) {
			this.collectFrom(edge.fromNode, nodes);
		}
		for (final PDGEdge edge : node.getForwardEdges()) {
			this.collectFrom(edge.toNode, nodes);
		}
	}

	/**
	 * このグラフの辺を全て返す。
	 *
	 * <p>{@link #getAllNodes()} が返す各ノードが持つ辺の総和である。
	 */
	public final SortedSet<PDGEdge> getAllEdges() {
		final SortedSet<PDGEdge> edges = new TreeSet<>();
		for (final PDGNode<?> node : this.getAllNodes()) {
			edges.addAll(node.getForwardEdges());
			edges.addAll(node.getBackwardEdges());
		}
		return edges;
	}

	public void build() {

		this.cfg = new CFG(this.unit, this.cfgNodeFactory);
		this.cfg.build();
		this.cfg.removeSwitchCases();
		this.cfg.removeJumpStatements();

		if (this.dependences.control()) {
			this.buildControlDependence(this.enterNode, unit);
			for (final PDGParameterNode parameterNode : this.parameterNodes) {
				new PDGControlDependenceEdge(this.enterNode, parameterNode, true).connect();
			}
		}

		if (this.dependences.execution()) {
			if (!this.cfg.isEmpty()) {
				final PDGNode<?> node = this.pdgNodeFactory.makeNode(this.cfg
						.getEnterNode());
				new PDGExecutionDependenceEdge(this.enterNode, node).connect();
			}
		}

		if (this.dependences.data()) {
			for (final PDGParameterNode parameterNode : this.parameterNodes) {
				if (!this.cfg.isEmpty()) {
					this.buildDataDependence(this.cfg.getEnterNode(),
							parameterNode, parameterNode.core.name,
							new HashSet<>());
				}
			}
		}

		final Set<CFGNode<?>> checkedNodes = new HashSet<>();
		if (!this.cfg.isEmpty()) {
			this.buildDependence(this.cfg.getEnterNode(), checkedNodes);
		}

		for (final CFGNode<?> cfgExitNode : this.cfg.getExitNodes()) {
			final PDGNode<?> pdgExitNode = this.pdgNodeFactory
					.makeNode(cfgExitNode);
			this.exitNodes.add(pdgExitNode);
		}

		if (!this.cfg.isEmpty()) {
			final Set<CFGNode<?>> unreachableNodes = new HashSet<>();
			unreachableNodes.addAll(this.cfg.getAllNodes());
			unreachableNodes.removeAll(this.cfg.getReachableNodes(this.cfg
					.getEnterNode()));
			for (final CFGNode<?> unreachableNode : unreachableNodes) {
				this.buildDependence(unreachableNode, checkedNodes);
			}
		}
	}

	private void buildDependence(final CFGNode<?> cfgNode,
			final Set<CFGNode<?>> checkedNodes) {

		Objects.requireNonNull(cfgNode, "\"cfgNode\" is null.");
		Objects.requireNonNull(checkedNodes, "\"checkedNodes\" is null.");

		if (checkedNodes.contains(cfgNode)) {
			return;
		} else {
			checkedNodes.add(cfgNode);
		}

		final PDGNode<?> pdgNode = this.pdgNodeFactory.makeNode(cfgNode);
		if (this.dependences.data()) {
			for (final String variable : pdgNode.core.getAssignedVariables()) {
				for (final CFGEdge edge : cfgNode.getForwardEdges()) {
					final Set<CFGNode<?>> checkedNodesForDefinedVariables = new HashSet<>();
					this.buildDataDependence(edge.toNode, pdgNode, variable,
							checkedNodesForDefinedVariables);
				}
			}
		}
		if (this.dependences.control()) {
			if (pdgNode instanceof PDGControlNode) {
				final ProgramElementInfo condition = ((PDGControlNode) pdgNode).core;
				this.buildControlDependence((PDGControlNode) pdgNode,
						condition.getOwnerConditionalBlock());
			}
		}

		if (this.dependences.execution()) {
			for (final CFGNode<?> toCFGNode : cfgNode.getForwardNodes()) {
				final PDGNode<?> toPDGNode = this.pdgNodeFactory
						.makeNode(toCFGNode);
				final int distance = Math.abs(toPDGNode.core.startLine
						- pdgNode.core.startLine) + 1;
				if (distance <= this.dependences.executionDistance()) {
					new PDGExecutionDependenceEdge(pdgNode, toPDGNode).connect();
				}

			}
		}

		for (final CFGNode<?> forwardNode : cfgNode.getForwardNodes()) {
			this.buildDependence(forwardNode, checkedNodes);
		}
	}

	private void buildDataDependence(final CFGNode<?> cfgNode,
			final PDGNode<?> fromPDGNode, final String variable,
			final Set<CFGNode<?>> checkedCFGNodes) {

		Objects.requireNonNull(cfgNode, "\"cfgNode\" is null.");
		Objects.requireNonNull(fromPDGNode, "\"fromPDGNode\" is null.");
		Objects.requireNonNull(variable, "\"variable\" is null.");
		Objects.requireNonNull(checkedCFGNodes, "\"checkedCFGNodes\" is null.");

		if (checkedCFGNodes.contains(cfgNode)) {
			return;
		} else {
			checkedCFGNodes.add(cfgNode);
		}

		if (cfgNode.core.getReferencedVariables().contains(variable)) {

			final PDGNode<?> toPDGNode = this.pdgNodeFactory.makeNode(cfgNode);
			final int distance = Math.abs(toPDGNode.core.startLine
					- fromPDGNode.core.startLine) + 1;
			if (distance <= this.dependences.dataDistance()) {
				new PDGDataDependenceEdge(fromPDGNode, toPDGNode, variable).connect();
			}
		}

		if (cfgNode.core.getAssignedVariables().contains(variable)) {
			return;
		}

		for (final CFGNode<?> forwardNode : cfgNode.getForwardNodes()) {
			this.buildDataDependence(forwardNode, fromPDGNode, variable,
					checkedCFGNodes);
		}
	}

	private void buildControlDependence(final PDGControlNode fromPDGNode,
			final BlockInfo block) {

		for (final StatementInfo statement : block.getStatements()) {
			this.buildControlDependence(fromPDGNode, statement, true);
		}

		if (block instanceof IfStatementInfo ifStatement) {
			for (final StatementInfo statement : ifStatement
					.getElseStatements()) {
				this.buildControlDependence(fromPDGNode, statement, false);
			}
		}

		if (block instanceof ForStatementInfo forStatement) {
			for (final ProgramElementInfo updater : forStatement.getUpdaters()) {
				final PDGNode<?> toPDGNode = this.pdgNodeFactory
						.makeNormalNode(updater);
				new PDGControlDependenceEdge(fromPDGNode, toPDGNode, true).connect();
			}
		}
	}

	private void buildControlDependence(final PDGControlNode fromPDGNode,
			final StatementInfo statement, final boolean type) {

		// 文を抱えられるものかどうかは型が答える。以前はこれを 10 個の case を
		// 並べて表していた。
		if (statement instanceof BlockStatementInfo block) {

			// 条件式を持たない種別もある。SimpleBlock と try、それに
			// for (;;) がそうである。
			final ProgramElementInfo condition = block instanceof ConditionalStatementInfo conditional
					? conditional.getCondition()
					: null;

			if (null != condition) {
				final PDGNode<?> toPDGNode = this.pdgNodeFactory
						.makeControlNode(condition);
				new PDGControlDependenceEdge(fromPDGNode, toPDGNode, type).connect();
			} else {
				this.buildControlDependence(fromPDGNode, block);
			}

			if (block instanceof ForStatementInfo forStatement) {
				for (final ProgramElementInfo initializer : forStatement
						.getInitializers()) {
					final PDGNode<?> toPDGNode = this.pdgNodeFactory
							.makeNormalNode(initializer);
					new PDGControlDependenceEdge(fromPDGNode, toPDGNode, type).connect();
				}
			}

			return;
		}

		// 抱えないものは、自分自身が制御依存の相手になる。
		//
		// switch 文ではなく式なのは網羅性を検査してもらうためである。種別を
		// 足すとここでコンパイルが止まり、辺を張る側か張らない側かを決める
		// ことになる。文のままだと黙って「張らない」に倒れる。
		final boolean dependsOnItself = switch (statement.getCategory()) {

		case Assert, Break, Case,
				Continue, Expression, Return,
				Throw, VariableDeclaration -> true;

		// 空文と型宣言は制御フロー上の意味を持たない。yield と未対応の構文は
		// 元からこの一覧に入っていない。
		case Empty, TypeDeclaration, Yield, Unsupported -> false;

		case Catch, Do, For,
				Foreach, If, SimpleBlock,
				Switch, Synchronized, Try,
				While -> throw new IllegalStateException(
						"文を抱える種別はここへ来ない: " + statement.getCategory());
		};

		if (!dependsOnItself) {
			return;
		}

		final CFGNode<?> cfgNode = this.cfgNodeFactory.getNode(statement);
		if ((null != cfgNode) && (this.cfg.getAllNodes().contains(cfgNode))) {
			final PDGNode<?> toPDGNode = this.pdgNodeFactory
					.makeNormalNode(statement);
			new PDGControlDependenceEdge(fromPDGNode, toPDGNode, type).connect();
		}
	}
}
