package yoshikihigo.tinypdg.cfg;

import java.util.ArrayList;
import java.util.Iterator;
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

	final protected LinkedList<CFGBreakStatementNode> unhandledBreakStatementNodes;

	final protected LinkedList<CFGContinueStatementNode> unhandledContinueStatementNodes;

	protected boolean built;

	public CFG(final ProgramElementInfo core, final CFGNodeFactory nodeFactory) {
		Objects.requireNonNull(nodeFactory, "\"nodeFactory\" is null.");
		this.core = core;
		this.nodeFactory = nodeFactory;
		this.nodes = new TreeSet<>();
		this.enterNode = null;
		this.exitNodes = new TreeSet<>();
		this.built = false;

		this.unhandledBreakStatementNodes = new LinkedList<>();
		this.unhandledContinueStatementNodes = new LinkedList<>();
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

	public void removeSwitchCases() {
		final Iterator<CFGNode<? extends ProgramElementInfo>> iterator = this.nodes
				.iterator();
		while (iterator.hasNext()) {
			final CFGNode<? extends ProgramElementInfo> node = iterator.next();
			if (node instanceof CFGSwitchCaseNode) {

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

				for (final CFGNode<?> fromNode : node.getBackwardNodes()) {
					for (final CFGNode<?> toNode : node.getForwardNodes()) {

						CFGEdge.makeJumpEdge(fromNode, toNode).connect();
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
				this.buildConditionalBlockCFG((ForStatementInfo) coreStatement,
						true);
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
			// 型宣言は制御フローを持たない。ノードも作らない。
			case TypeDeclaration -> true;
			case Assert, Break, Case,
					Continue, Empty, Expression,
					Return, SimpleBlock, Throw,
					VariableDeclaration, Yield, Unsupported -> false;
			};

			if (!expanded) {
				final CFGNode<? extends ProgramElementInfo> node = this.nodeFactory
						.makeNormalNode(coreStatement);
				this.enterNode = node;
				if (node instanceof CFGBreakStatementNode breakNode) {
					this.unhandledBreakStatementNodes.addFirst(breakNode);
				} else if (node instanceof CFGContinueStatementNode continueNode) {
					this.unhandledContinueStatementNodes
							.addFirst(continueNode);
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

		else if (this.core instanceof MethodInfo) {
			final MethodInfo coreMethod = (MethodInfo) this.core;
			this.buildSimpleBlockCFG(coreMethod);
		}

		else {
			throw new TinyPDGException(
					"CFG を組み立てられない要素です: " + this.core.getClass().getName());
		}

		if (null != this.core) {
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

		this.connectCFGBreakStatementNode(statement);
		this.connectCFGContinueStatementNode(statement, this.enterNode);
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
		this.exitNodes.add(conditionNode);
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

		this.connectCFGBreakStatementNode(statement);
		this.connectCFGContinueStatementNode(statement, conditionNode);
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
			this.exitNodes.addAll(sequentialCFGs.exitNodes);
			if (0 == substatements.size()) {
				this.exitNodes.add(conditionNode);
			}
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

			this.connectCFGBreakStatementNode(statement);
			this.connectCFGContinueStatementNode(statement, conditionNode);
		}
	}

	private void buildIfBlockCFG(final IfStatementInfo statement) {

		this.buildConditionalBlockCFG(statement, false);

		final ProgramElementInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);

		if (null != statement.getElseStatements()) {
			final List<StatementInfo> elseStatements = statement
					.getElseStatements();
			final SequentialCFGs elseCFG = new SequentialCFGs(elseStatements);
			elseCFG.build();

			this.absorb(elseCFG);
			this.exitNodes.addAll(elseCFG.exitNodes);
			if (0 == elseStatements.size()) {
				this.exitNodes.add(conditionNode);
			}

			connect(conditionNode, elseCFG.enterNode, false);
		}

		else {
			this.exitNodes.add(conditionNode);
		}
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
			final CFG subCFG = new CFG(substatement, this.nodeFactory);
			subCFG.build();
			sequentialCFGs.add(subCFG);
			this.absorb(subCFG);

			final boolean exitsTheSwitch = switch (substatement.getCategory()) {
			case Case -> {
				// ラベルには条件から直接繋ぐ。
				connect(conditionNode, subCFG.enterNode, true);
				yield false;
			}
			case Break, Continue -> true;
			// 直前の文から順に繋がる。ここで足すことはない。
			case Assert, Catch, Do,
					Empty, Expression, If,
					For, Foreach, Return,
					SimpleBlock, Synchronized, Switch,
					Throw, Try, TypeDeclaration,
					VariableDeclaration, While, Yield,
					Unsupported -> false;
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
				// break と continue は次の文へ流れない。
				final boolean fallsThrough = switch (anteriorStatement
						.getCategory()) {
				case Break, Continue -> false;
				case Assert, Case, Catch,
						Do, Empty, Expression,
						If, For, Foreach,
						Return, SimpleBlock, Synchronized,
						Switch, Throw, Try,
						TypeDeclaration, VariableDeclaration, While,
						Yield, Unsupported -> true;
				};
				if (!fallsThrough) {
					continue CFG;
				}
			}

			for (final CFGNode<? extends ProgramElementInfo> anteriorExitNode : anteriorCFG.exitNodes) {
				connect(anteriorExitNode, posteriorCFG.enterNode);
			}
		}

		this.exitNodes
				.addAll(sequentialCFGs.get(sequentialCFGs.size() - 1).exitNodes);

		this.connectCFGBreakStatementNode(statement);
	}

	private void buildTryBlockCFG(final TryStatementInfo statement) {

		final List<StatementInfo> statements = statement.getStatements();
		final SequentialCFGs sequentialCFGs = new SequentialCFGs(statements);
		sequentialCFGs.build();

		final StatementInfo finallyBlock = statement.getFinallyStatement();
		final CFG finallyCFG = new CFG(finallyBlock, this.nodeFactory);
		finallyCFG.build();

		this.enterNode = sequentialCFGs.enterNode;
		this.absorb(sequentialCFGs);
		this.nodes.addAll(finallyCFG.nodes);
		this.exitNodes.addAll(finallyCFG.exitNodes);

		for (final CFGNode<? extends ProgramElementInfo> sequentialExitNode : sequentialCFGs.exitNodes) {
			connect(sequentialExitNode, finallyCFG.enterNode);
		}

		for (final StatementInfo catchStatement : statement
				.getCatchStatements()) {

			final CFG catchCFG = new CFG(catchStatement, this.nodeFactory);
			catchCFG.build();

			this.nodes.addAll(catchCFG.nodes);
			for (final CFGNode<? extends ProgramElementInfo> catchExitNode : catchCFG.exitNodes) {
				connect(catchExitNode, finallyCFG.enterNode);
			}
		}
	}

	/**
	 * 部分グラフのノードと、まだ行き先の決まっていない break と continue を
	 * 引き取る。入口と出口は文の種類ごとに決め方が違うので、呼ぶ側が扱う。
	 *
	 * <p>private にすると SequentialCFGs から呼べない。private なメソッドは
	 * 継承されないので、サブクラスの this からは見つからない。
	 */
	void absorb(final CFG sub) {
		this.nodes.addAll(sub.nodes);
		this.unhandledBreakStatementNodes
				.addAll(sub.unhandledBreakStatementNodes);
		this.unhandledContinueStatementNodes
				.addAll(sub.unhandledContinueStatementNodes);
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

				if (this.exitNodes.contains(node)) {
					this.exitNodes.addAll(node.getBackwardNodes());
					this.exitNodes.remove(node);
				}

				final SortedSet<CFGNode<? extends ProgramElementInfo>> backwardNodes = node
						.getBackwardNodes();
				final SortedSet<CFGNode<? extends ProgramElementInfo>> forwardNodes = node
						.getForwardNodes();
				for (final CFGNode<? extends ProgramElementInfo> backwardNode : backwardNodes) {
					backwardNode.removeForwardNode(node);
				}
				for (final CFGNode<? extends ProgramElementInfo> forwardNode : forwardNodes) {
					forwardNode.removeBackwardNode(node);
				}
				for (final CFGNode<? extends ProgramElementInfo> backwardNode : backwardNodes) {
					for (final CFGNode<? extends ProgramElementInfo> forwardNode : forwardNodes) {
						connect(backwardNode, forwardNode);
					}
				}
			}
		}
	}

	private void connectCFGBreakStatementNode(final StatementInfo statement) {

		final Iterator<CFGBreakStatementNode> iterator = this.unhandledBreakStatementNodes
				.iterator();
		while (iterator.hasNext()) {
			final CFGBreakStatementNode node = iterator.next();
			final StatementInfo breakStatement = node.core;
			final String label = breakStatement.getJumpToLabel();

			if (null == label) {
				this.exitNodes.add(node);
				iterator.remove();
			}

			else {

				if (label.equals(statement.getLabel())) {
					this.exitNodes.add(node);
					iterator.remove();
				}
			}
		}
	}

	private void connectCFGContinueStatementNode(final StatementInfo statement,
			final CFGNode<? extends ProgramElementInfo> distinationNode) {

		final Iterator<CFGContinueStatementNode> iterator = this.unhandledContinueStatementNodes
				.iterator();
		while (iterator.hasNext()) {
			final CFGContinueStatementNode node = iterator.next();
			final StatementInfo continueStatement = node.core;
			final String label = continueStatement.getJumpToLabel();

			if (null == label) {
				connect(node, distinationNode);
				iterator.remove();
			}

			else {

				if (label.equals(statement.getLabel())) {
					connect(node, distinationNode);
					iterator.remove();
				}
			}
		}

	}

	private class SequentialCFGs extends CFG {

		final List<? extends ProgramElementInfo> elements;

		SequentialCFGs(final List<? extends ProgramElementInfo> elements) {

			super(null, CFG.this.nodeFactory);
			this.elements = elements;
		}

		@Override
		public void build() {

			assert !this.built : "this CFG has already built.";
			this.built = true;

			final LinkedList<CFG> sequencialCFGs = new LinkedList<>();
			for (final ProgramElementInfo element : this.elements) {
				final CFG blockCFG = new CFG(element, CFG.this.nodeFactory);
				blockCFG.build();
				if (!blockCFG.isEmpty()) {
					sequencialCFGs.add(blockCFG);
				}
			}
			for (int index = 1; index < sequencialCFGs.size(); index++) {
				final CFG anteriorCFG = sequencialCFGs.get(index - 1);
				final CFG posteriorCFG = sequencialCFGs.get(index);
				for (final CFGNode<?> exitNode : anteriorCFG.exitNodes) {
					connect(exitNode, posteriorCFG.enterNode);
				}
			}
			if (0 == sequencialCFGs.size()) {
				final CFG pseudoCFG = new CFG(null, CFG.this.nodeFactory);
				pseudoCFG.build();
				sequencialCFGs.add(pseudoCFG);
			}

			this.enterNode = sequencialCFGs.getFirst().enterNode;
			this.exitNodes.addAll(sequencialCFGs.getLast().exitNodes);
			for (final CFG cfg : sequencialCFGs) {
				this.absorb(cfg);
			}
		}
	}

	public final SortedSet<CFGNode<? extends ProgramElementInfo>> getReachableNodes(
			final CFGNode<? extends ProgramElementInfo> startNode) {
		Objects.requireNonNull(startNode, "\"startNode\" is null.");
		final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes = new TreeSet<>();
		this.getReachableNodes(startNode, nodes);
		return nodes;
	}

	private final void getReachableNodes(
			final CFGNode<? extends ProgramElementInfo> startNode,
			final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes) {
		Objects.requireNonNull(startNode, "\"startNode\" is null.");
		Objects.requireNonNull(nodes, "\"nodes\" is null.");

		if (nodes.contains(startNode)) {
			return;
		}

		nodes.add(startNode);
		for (final CFGNode<? extends ProgramElementInfo> node : startNode
				.getForwardNodes()) {
			this.getReachableNodes(node, nodes);
		}
	}
}
