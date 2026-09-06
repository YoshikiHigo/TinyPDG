package yoshikihigo.tinypdg.pdg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.cfg.CFG;
import yoshikihigo.tinypdg.cfg.edge.CFGEdge;
import yoshikihigo.tinypdg.cfg.edge.CFGExceptionEdge;
import yoshikihigo.tinypdg.cfg.node.CFGControlNode;
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
import yoshikihigo.tinypdg.pe.TryStatementInfo;
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
	 * CFG のノードの集合。build で CFG を作り終えたときに 1 回だけ写す。
	 *
	 * <p>CFG.getAllNodes() は呼ぶたびに TreeSet を作り直す。制御依存の
	 * 構築は文ごとに「この文のノードは CFG にあるか」を問うので、以前は
	 * その写しが文の数だけ作られていた。
	 */
	private SortedSet<CFGNode<?>> cfgNodes;

	/**
	 * 構文による制御依存を、まだ制御依存を持たないノードにだけ張るか。
	 * 後支配の計算が届かなかったノードを補うときに真にする。
	 */
	private boolean onlyOrphans;

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
	 * @param controlDependence 制御依存の決め方
	 */
	public record Dependences(boolean control, boolean data, boolean execution,
			int dataDistance, int executionDistance,
			ControlDependence controlDependence) {

		/** 3 種類すべてを、距離の制限なしで作る。制御依存は後支配から。 */
		public static final Dependences ALL = new Dependences(true, true, true);

		public Dependences {
			Objects.requireNonNull(controlDependence,
					"\"controlDependence\" is null.");
			if (dataDistance < 1 || executionDistance < 1) {
				throw new IllegalArgumentException(
						"距離は 1 以上でなければならない: data=" + dataDistance
								+ " execution=" + executionDistance);
			}
		}

		/** 距離を制限せずに作る。制御依存は後支配から。 */
		public Dependences(final boolean control, final boolean data,
				final boolean execution) {
			this(control, data, execution, Integer.MAX_VALUE,
					Integer.MAX_VALUE);
		}

		/** 制御依存は後支配から。 */
		public Dependences(final boolean control, final boolean data,
				final boolean execution, final int dataDistance,
				final int executionDistance) {
			this(control, data, execution, dataDistance, executionDistance,
					ControlDependence.POST_DOMINANCE);
		}

		/** 制御依存を文の入れ子で決める設定にしたもの。 */
		public Dependences withStructuralControl() {
			return new Dependences(this.control, this.data, this.execution,
					this.dataDistance, this.executionDistance,
					ControlDependence.STRUCTURAL);
		}
	}

	/** 制御依存の決め方。 */
	public enum ControlDependence {
		/**
		 * CFG の後支配から計算する。Ferrante らの古典的な定義で、break や
		 * return が作る依存も拾う。{@code if (c) return; x = 1;} の
		 * {@code x = 1} は c に偽で依存する。既定。
		 */
		POST_DOMINANCE,
		/**
		 * 文の入れ子で決める。条件の本体は条件に、最上位の文は入口に依存し、
		 * ジャンプは考慮しない。以前の唯一の決め方で、過去の結果との比較の
		 * ために残してある。
		 */
		STRUCTURAL
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
		if (null != this.cfgNodes) {
			for (final CFGNode<?> cfgNode : this.cfgNodes) {
				this.collectFrom(this.pdgNodeFactory.makeNode(cfgNode), nodes);
			}
		}

		return nodes;
	}

	/**
	 * startNode と、辺で繋がっているノードを全て nodes に集める。
	 *
	 * <p>再帰ではなく作業リストで巡る。長いメソッドではグラフの経路の長さの
	 * ぶんだけ再帰が深くなり、StackOverflowError になっていた。このクラスの
	 * 他の巡回も同じ形にしてある。
	 */
	private void collectFrom(final PDGNode<?> startNode,
			final SortedSet<PDGNode<?>> nodes) {

		Objects.requireNonNull(startNode, "\"startNode\" is null.");

		final Deque<PDGNode<?>> worklist = new ArrayDeque<>();
		worklist.push(startNode);
		while (!worklist.isEmpty()) {
			final PDGNode<?> node = worklist.pop();
			if (!nodes.add(node)) {
				continue;
			}
			for (final PDGEdge edge : node.getBackwardEdges()) {
				worklist.push(edge.fromNode);
			}
			for (final PDGEdge edge : node.getForwardEdges()) {
				worklist.push(edge.toNode);
			}
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
		// 以後 CFG は変わらない。
		this.cfgNodes = this.cfg.getAllNodes();

		if (this.dependences.control()) {
			switch (this.dependences.controlDependence()) {
			case POST_DOMINANCE -> {
				this.buildPostDominanceControlDependence();
				// 入口から辿れないノード (catch 節の条件と、その本体の先頭、
				// return の後の到達不能な文) には後支配の計算が辺を張らない。
				// それらだけ文の入れ子で決める。
				this.buildStructuralControlDependence(true);
			}
			case STRUCTURAL -> this.buildStructuralControlDependence(false);
			}
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
							parameterNode, parameterNode.core.name);
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
			unreachableNodes.addAll(this.cfgNodes);
			unreachableNodes.removeAll(this.cfg.getReachableNodes(this.cfg
					.getEnterNode()));
			for (final CFGNode<?> unreachableNode : unreachableNodes) {
				this.buildDependence(unreachableNode, checkedNodes);
			}
		}
	}

	/** startNode から到達できる CFG のノードそれぞれについて、依存の辺を張る。 */
	private void buildDependence(final CFGNode<?> startNode,
			final Set<CFGNode<?>> checkedNodes) {

		Objects.requireNonNull(startNode, "\"startNode\" is null.");
		Objects.requireNonNull(checkedNodes, "\"checkedNodes\" is null.");

		final Deque<CFGNode<?>> worklist = new ArrayDeque<>();
		worklist.push(startNode);
		while (!worklist.isEmpty()) {
			final CFGNode<?> cfgNode = worklist.pop();
			if (!checkedNodes.add(cfgNode)) {
				continue;
			}
			this.buildDependenceOf(cfgNode);
			pushReversed(worklist, cfgNode.getForwardNodes());
		}
	}

	/** 1 つのノードから出る依存の辺を張る。 */
	private void buildDependenceOf(final CFGNode<?> cfgNode) {

		final PDGNode<?> pdgNode = this.pdgNodeFactory.makeNode(cfgNode);
		if (this.dependences.data()) {
			for (final String variable : pdgNode.core.getAssignedVariables()) {
				for (final CFGNode<?> forwardNode : cfgNode.getForwardNodes()) {
					this.buildDataDependence(forwardNode, pdgNode, variable);
				}
			}
		}
		if (this.dependences.execution()) {
			// 例外辺は「直後に実行される」とは読まない。may の辺なので、実行依存
			// に使うと try 本体の各文に後続が増え、連続するノードの併合も切れる。
			// データ依存は例外辺も辿る (buildDataDependence)。
			final SortedSet<CFGNode<? extends ProgramElementInfo>> successors = new TreeSet<>();
			for (final CFGEdge edge : cfgNode.getForwardEdges()) {
				if (!(edge instanceof CFGExceptionEdge)) {
					successors.add(edge.toNode);
				}
			}
			for (final CFGNode<?> toCFGNode : successors) {
				final PDGNode<?> toPDGNode = this.pdgNodeFactory
						.makeNode(toCFGNode);
				final int distance = Math.abs(toPDGNode.core.startLine
						- pdgNode.core.startLine) + 1;
				if (distance <= this.dependences.executionDistance()) {
					new PDGExecutionDependenceEdge(pdgNode, toPDGNode).connect();
				}

			}
		}
	}

	/** 先頭の要素が最初に取り出されるように、逆順に積む。 */
	private static void pushReversed(final Deque<CFGNode<?>> worklist,
			final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes) {
		final List<CFGNode<?>> list = new ArrayList<>(nodes);
		for (int index = list.size() - 1; 0 <= index; index--) {
			worklist.push(list.get(index));
		}
	}

	/**
	 * fromPDGNode が variable を定義した後、startNode から先でその値を読む
	 * ノードへデータ依存の辺を張る。variable を定義し直すノードで止まる。
	 */
	private void buildDataDependence(final CFGNode<?> startNode,
			final PDGNode<?> fromPDGNode, final String variable) {

		Objects.requireNonNull(startNode, "\"startNode\" is null.");
		Objects.requireNonNull(fromPDGNode, "\"fromPDGNode\" is null.");
		Objects.requireNonNull(variable, "\"variable\" is null.");

		final Set<CFGNode<?>> checkedCFGNodes = new HashSet<>();
		final Deque<CFGNode<?>> worklist = new ArrayDeque<>();
		worklist.push(startNode);
		while (!worklist.isEmpty()) {
			final CFGNode<?> cfgNode = worklist.pop();
			if (!checkedCFGNodes.add(cfgNode)) {
				continue;
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
				continue;
			}

			pushReversed(worklist, cfgNode.getForwardNodes());
		}
	}

	/** 後支配から制御依存を張る。 */
	private void buildPostDominanceControlDependence() {
		for (final PostDominanceControlDependence.Dependence dependence : PostDominanceControlDependence
				.compute(this.cfg)) {
			final PDGControlNode fromPDGNode = null == dependence.from()
					? this.enterNode
					: (PDGControlNode) this.pdgNodeFactory.makeNode(dependence.from());
			final PDGNode<?> toPDGNode = this.pdgNodeFactory.makeNode(dependence.to());
			new PDGControlDependenceEdge(fromPDGNode, toPDGNode,
					dependence.control()).connect();
		}
	}

	/**
	 * 文の入れ子から制御依存を張る。入口から本体の最上位の文へ、各条件ノード
	 * からその本体の文へ。
	 *
	 * @param onlyOrphans 真なら、まだ制御依存を持たないノードにだけ辺を張る。
	 *                    後支配の計算が届かなかったノードを補うときに使う
	 */
	private void buildStructuralControlDependence(final boolean onlyOrphans) {
		this.onlyOrphans = onlyOrphans;
		this.buildControlDependenceInside(this.enterNode, this.unit, true);
		for (final CFGNode<?> cfgNode : this.cfgNodes) {
			if (cfgNode instanceof CFGControlNode) {
				final PDGControlNode controlNode = (PDGControlNode) this.pdgNodeFactory
						.makeNode(cfgNode);
				this.buildControlDependenceInside(controlNode,
						controlNode.core.getOwnerConditionalBlock(), true);
			}
		}
		this.onlyOrphans = false;
	}

	/**
	 * 構文による制御依存の辺を張る。onlyOrphans なら、既に制御依存を持つ相手
	 * には張らない。
	 */
	private void connectControl(final PDGControlNode fromPDGNode,
			final PDGNode<?> toPDGNode, final boolean type) {
		if (this.onlyOrphans && toPDGNode.getBackwardEdges().stream()
				.anyMatch(edge -> edge instanceof PDGControlDependenceEdge)) {
			return;
		}
		new PDGControlDependenceEdge(fromPDGNode, toPDGNode, type).connect();
	}

	/**
	 * ブロックの中身を fromPDGNode に制御依存させる。
	 *
	 * @param type fromPDGNode の条件がこの値のときに中身が実行される。条件
	 *             ノード自身の本体なら真、if の else の中なら偽。条件を持た
	 *             ないブロック (try、synchronized、ラベル付きブロック) が else
	 *             の中にあれば、その中身も偽で依存する。以前はここで真に
	 *             戻していた
	 */
	private void buildControlDependenceInside(final PDGControlNode fromPDGNode,
			final BlockInfo block, final boolean type) {

		for (final StatementInfo statement : block.getStatements()) {
			this.buildControlDependence(fromPDGNode, statement, type);
		}

		if (block instanceof IfStatementInfo ifStatement) {
			for (final StatementInfo statement : ifStatement
					.getElseStatements()) {
				this.buildControlDependence(fromPDGNode, statement, false);
			}
		}

		// catch 節と finally ブロックは、try 本体と同じ相手に依存する。catch
		// の条件ノード (例外の宣言) が相手になり、その本体は条件ノードの側で
		// 張る。以前はどちらも見ておらず、catch の条件ノードにも finally の中
		// の文にも制御依存の辺が 1 本もなかった。
		if (block instanceof TryStatementInfo tryStatement) {
			for (final StatementInfo catchStatement : tryStatement
					.getCatchStatements()) {
				this.buildControlDependence(fromPDGNode, catchStatement, type);
			}
			final StatementInfo finallyStatement = tryStatement.getFinallyStatement();
			if (null != finallyStatement) {
				this.buildControlDependence(fromPDGNode, finallyStatement, type);
			}
		}

		if (block instanceof ForStatementInfo forStatement) {
			for (final ProgramElementInfo updater : forStatement.getUpdaters()) {
				final PDGNode<?> toPDGNode = this.pdgNodeFactory
						.makeNormalNode(updater);
				this.connectControl(fromPDGNode, toPDGNode, type);
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
				this.connectControl(fromPDGNode, toPDGNode, type);
			} else {
				this.buildControlDependenceInside(fromPDGNode, block, type);
			}

			if (block instanceof ForStatementInfo forStatement) {
				for (final ProgramElementInfo initializer : forStatement
						.getInitializers()) {
					final PDGNode<?> toPDGNode = this.pdgNodeFactory
							.makeNormalNode(initializer);
					this.connectControl(fromPDGNode, toPDGNode, type);
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
		if ((null != cfgNode) && (this.cfgNodes.contains(cfgNode))) {
			final PDGNode<?> toPDGNode = this.pdgNodeFactory
					.makeNormalNode(statement);
			this.connectControl(fromPDGNode, toPDGNode, type);
		}
	}
}
