package yoshikihigo.tinypdg.cfg;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.cfg.edge.CFGControlEdge;
import yoshikihigo.tinypdg.cfg.edge.CFGEdge;
import yoshikihigo.tinypdg.cfg.edge.CFGNormalEdge;
import yoshikihigo.tinypdg.cfg.node.CFGNode;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pe.BlockInfo;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;

public class CFG {

	final public ProgramElementInfo core;

	final private CFGNodeFactory nodeFactory;

	final protected SortedSet<CFGNode<? extends ProgramElementInfo>> nodes;

	protected CFGNode<? extends ProgramElementInfo> enterNode;

	final protected Set<CFGNode<? extends ProgramElementInfo>> exitNodes;

	public CFG(final ProgramElementInfo core, final CFGNodeFactory nodeFactory) {
		assert null != nodeFactory : "\"nodeFactory\" is null.";
		this.core = core;
		this.nodeFactory = nodeFactory;
		this.nodes = new TreeSet<CFGNode<? extends ProgramElementInfo>>();
		this.enterNode = null;
		this.exitNodes = new TreeSet<CFGNode<? extends ProgramElementInfo>>();
	}

	public boolean isEmpty() {
		return 0 == this.nodes.size();
	}

	public CFGNode<? extends ProgramElementInfo> getEnterNode() {
		return this.enterNode;
	}

	public SortedSet<CFGNode<? extends ProgramElementInfo>> getExitNodes() {
		final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes = new TreeSet<CFGNode<? extends ProgramElementInfo>>();
		nodes.addAll(this.exitNodes);
		return nodes;
	}

	public SortedSet<CFGNode<? extends ProgramElementInfo>> getAllNodes() {
		final SortedSet<CFGNode<? extends ProgramElementInfo>> nodes = new TreeSet<CFGNode<? extends ProgramElementInfo>>();
		nodes.addAll(this.nodes);
		return nodes;
	}

	public void build() {

		if (null == this.core) {
			final CFGNode<? extends ProgramElementInfo> node = nodeFactory
					.makeNormalNode(null);
			this.nodes.add(node);
			this.enterNode = node;
			this.exitNodes.add(node);
		}

		else if (this.core instanceof StatementInfo) {
			final StatementInfo coreStatement = (StatementInfo) this.core;
			switch (coreStatement.category) {
			case Catch:
				this.buildConditionalBlockCFG(coreStatement, false);
				break;
			case Do:
				this.buildDoBlockCFG(coreStatement);
				break;
			case Finally:
				this.buildSimpleBlockCFG(coreStatement);
				break;
			case For:
				this.buildForBlockCFG(coreStatement);
				break;
			case Foreach:
				this.buildConditionalBlockCFG(coreStatement, true);
				break;
			case If:
				this.buildIfBlockCFG(coreStatement);
				break;
			case Switch:
				this.buildSwitchBlockCFG(coreStatement);
				break;
			case Synchronized:
				this.buildConditionalBlockCFG(coreStatement, false);
				break;
			case Try:
				this.buildTryBlockCFG(coreStatement);
				break;
			case While:
				this.buildConditionalBlockCFG(coreStatement, true);
				break;
			default:
				final CFGNode<? extends ProgramElementInfo> node = this.nodeFactory
						.makeNormalNode(coreStatement);
				this.enterNode = node;
				this.exitNodes.add(node);
				this.nodes.add(node);
				break;
			}
		}

		else if (this.core instanceof ExpressionInfo) {
			final ExpressionInfo coreExpression = (ExpressionInfo) this.core;
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
			assert false : "unexpected state.";
		}
	}

	// private LinkedList<CFG> buildSequencialCFGs(
	// final List<? extends ProgramElementInfo> elements) {
	//
	// final LinkedList<CFG> sequencialCFGs = new LinkedList<CFG>();
	// for (final ProgramElementInfo element : elements) {
	// final CFG blockCFG = new CFG(element, CFG.this.nodeFactory);
	// if (!blockCFG.isEmpty()) {
	// sequencialCFGs.add(blockCFG);
	// }
	// }
	// for (int index = 1; index < sequencialCFGs.size(); index++) {
	// final CFG anteriorCFG = sequencialCFGs.get(index - 1);
	// final CFG posteriorCFG = sequencialCFGs.get(index);
	// for (final CFGNode<?> exitNode : anteriorCFG.exitNodes) {
	// final CFGEdge edge = new CFGNormalEdge(exitNode,
	// posteriorCFG.enterNode);
	// exitNode.addForwardEdge(edge);
	// anteriorCFG.enterNode.addBackwardEdge(edge);
	// }
	// }
	// if (0 == sequencialCFGs.size()) {
	// final CFG pseudoCFG = new CFG(this.nodeFactory);
	// sequencialCFGs.add(pseudoCFG);
	// }
	// return sequencialCFGs;
	// }

	private void buildDoBlockCFG(final StatementInfo statement) {

		final SequentialCFGs sequentialCFGs = new SequentialCFGs(
				statement.getStatements());
		final ExpressionInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);

		this.enterNode = sequentialCFGs.enterNode;
		this.nodes.addAll(sequentialCFGs.nodes);
		this.nodes.add(conditionNode);
		this.exitNodes.add(conditionNode);

		for (final CFGNode<?> exitNode : sequentialCFGs.exitNodes) {
			final CFGEdge edge = new CFGNormalEdge(exitNode, conditionNode);
			exitNode.addForwardEdge(edge);
			conditionNode.addBackwardEdge(edge);
		}
		final CFGEdge edge = new CFGControlEdge(conditionNode,
				sequentialCFGs.enterNode, true);
		conditionNode.addForwardEdge(edge);
		sequentialCFGs.enterNode.addBackwardEdge(edge);
	}

	private void buildForBlockCFG(final StatementInfo statement) {

		final SequentialCFGs sequentialCFGs = new SequentialCFGs(
				statement.getStatements());

		final List<ExpressionInfo> initializers = statement.getInitializers();
		final ExpressionInfo condition = statement.getCondition();
		final List<ExpressionInfo> updaters = statement.getUpdaters();

		final SequentialCFGs initializerCFGs = new SequentialCFGs(initializers);
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);
		final SequentialCFGs updaterCFGs = new SequentialCFGs(updaters);

		this.enterNode = initializerCFGs.enterNode;
		this.exitNodes.add(conditionNode);
		this.nodes.addAll(sequentialCFGs.nodes);
		this.nodes.addAll(initializerCFGs.nodes);
		this.nodes.add(conditionNode);
		this.nodes.addAll(updaterCFGs.nodes);

		for (final CFGNode<? extends ProgramElementInfo> initializerExitNode : initializerCFGs.exitNodes) {
			final CFGEdge edge = new CFGNormalEdge(initializerExitNode,
					conditionNode);
			initializerExitNode.addForwardEdge(edge);
			conditionNode.addBackwardEdge(edge);
		}
		{
			final CFGEdge controlEdge = new CFGControlEdge(conditionNode,
					sequentialCFGs.enterNode, true);
			conditionNode.addForwardEdge(controlEdge);
			sequentialCFGs.enterNode.addBackwardEdge(controlEdge);
		}
		for (final CFGNode<? extends ProgramElementInfo> sequencialExitNode : sequentialCFGs.exitNodes) {
			final CFGEdge edge = new CFGNormalEdge(sequencialExitNode,
					updaterCFGs.enterNode);
			sequencialExitNode.addForwardEdge(edge);
			updaterCFGs.enterNode.addBackwardEdge(edge);
		}
		for (final CFGNode<? extends ProgramElementInfo> updaterExitNode : updaterCFGs.exitNodes) {
			final CFGEdge edge = new CFGNormalEdge(updaterExitNode,
					conditionNode);
			updaterExitNode.addForwardEdge(edge);
			conditionNode.addBackwardEdge(edge);
		}
	}

	private void buildConditionalBlockCFG(final StatementInfo statement,
			final boolean loop) {

		final SequentialCFGs sequentialCFGs = new SequentialCFGs(
				statement.getStatements());
		final ExpressionInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);

		this.enterNode = conditionNode;
		this.nodes.addAll(sequentialCFGs.nodes);
		this.nodes.add(conditionNode);
		this.exitNodes.add(conditionNode);

		{
			final CFGEdge edge = new CFGControlEdge(conditionNode,
					sequentialCFGs.enterNode, true);
			conditionNode.addForwardEdge(edge);
			sequentialCFGs.enterNode.addBackwardEdge(edge);
		}

		if (loop) {
			for (final CFGNode<?> exitNode : sequentialCFGs.exitNodes) {
				final CFGEdge edge = new CFGNormalEdge(exitNode, conditionNode);
				exitNode.addForwardEdge(edge);
				conditionNode.addBackwardEdge(edge);
			}
		}
	}

	private void buildIfBlockCFG(final StatementInfo statement) {

		this.buildConditionalBlockCFG(statement, false);

		final List<StatementInfo> elseStatements = statement.getElseStatement()
				.getStatements();
		final SequentialCFGs elseCFG = new SequentialCFGs(elseStatements);

		final ExpressionInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);

		this.nodes.addAll(elseCFG.nodes);
		this.exitNodes.addAll(elseCFG.exitNodes);

		{
			final CFGControlEdge edge = new CFGControlEdge(conditionNode,
					elseCFG.enterNode, false);
			conditionNode.addForwardEdge(edge);
			elseCFG.enterNode.addBackwardEdge(edge);
		}
	}

	private void buildSimpleBlockCFG(final BlockInfo statement) {
		final List<StatementInfo> substatements = statement.getStatements();
		final SequentialCFGs sequentialCFGs = new SequentialCFGs(substatements);
		sequentialCFGs.build();
		this.enterNode = sequentialCFGs.enterNode;
		this.exitNodes.addAll(sequentialCFGs.exitNodes);
		this.nodes.addAll(sequentialCFGs.nodes);
	}

	private void buildSwitchBlockCFG(final StatementInfo statement) {

		final ExpressionInfo condition = statement.getCondition();
		final CFGNode<? extends ProgramElementInfo> conditionNode = this.nodeFactory
				.makeControlNode(condition);
		this.enterNode = conditionNode;
		this.nodes.add(conditionNode);

		final List<StatementInfo> substatements = statement.getStatements();
		final List<CFG> sequentialCFGs = new ArrayList<CFG>();
		for (final StatementInfo substatement : substatements) {
			final CFG subCFG = new CFG(substatement, this.nodeFactory);
			subCFG.build();
			sequentialCFGs.add(subCFG);
			this.nodes.addAll(subCFG.nodes);

			switch (substatement.category) {
			case Case: {
				final CFGEdge edge = new CFGControlEdge(conditionNode,
						subCFG.enterNode, true);
				conditionNode.addForwardEdge(edge);
				subCFG.enterNode.addBackwardEdge(edge);
				break;
			}
			case Break:
			case Continue: {
				this.exitNodes.addAll(subCFG.exitNodes);
				break;
			}
			default:
			}
		}

		for (int index = 1; index < sequentialCFGs.size(); index++) {
			final CFG anteriorCFG = sequentialCFGs.get(index - 1);
			final CFG posteriorCFG = sequentialCFGs.get(index);

			final ProgramElementInfo anteriorCore = anteriorCFG.core;
			if (anteriorCore instanceof StatementInfo) {
				switch (((StatementInfo) anteriorCore).category) {
				case Break:
				case Continue:
					continue;
				default:
				}
			}

			for (final CFGNode<? extends ProgramElementInfo> anteriorExitNode : anteriorCFG.exitNodes) {
				final CFGEdge edge = new CFGNormalEdge(anteriorExitNode,
						posteriorCFG.enterNode);
				anteriorExitNode.addForwardEdge(edge);
				posteriorCFG.enterNode.addBackwardEdge(edge);
			}
		}
	}

	private void buildTryBlockCFG(final StatementInfo statement) {

		final List<StatementInfo> statements = statement.getStatements();
		final SequentialCFGs sequentialCFGs = new SequentialCFGs(statements);

		final StatementInfo finallyBlock = statement.getFinallyStatement();
		final CFG finallyCFG = new CFG(finallyBlock, this.nodeFactory);
		finallyCFG.build();

		this.enterNode = sequentialCFGs.enterNode;
		this.nodes.addAll(sequentialCFGs.nodes);
		this.nodes.addAll(finallyCFG.exitNodes);
		this.exitNodes.addAll(finallyCFG.exitNodes);

		for (final CFGNode<? extends ProgramElementInfo> sequentialExitNode : sequentialCFGs.exitNodes) {
			final CFGEdge edge = new CFGNormalEdge(sequentialExitNode,
					finallyCFG.enterNode);
			sequentialExitNode.addForwardEdge(edge);
			finallyCFG.enterNode.addBackwardEdge(edge);
		}

		for (final StatementInfo catchStatement : statement
				.getCatchStatements()) {

			final CFG catchCFG = new CFG(catchStatement, this.nodeFactory);
			catchCFG.build();

			this.nodes.addAll(catchCFG.nodes);
			for (final CFGNode<? extends ProgramElementInfo> catchExitNode : catchCFG.exitNodes) {
				final CFGEdge edge = new CFGNormalEdge(catchExitNode,
						finallyCFG.enterNode);
				catchExitNode.addForwardEdge(edge);
				finallyCFG.enterNode.addBackwardEdge(edge);
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
			final LinkedList<CFG> sequencialCFGs = new LinkedList<CFG>();
			for (final ProgramElementInfo element : elements) {
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
					final CFGEdge edge = new CFGNormalEdge(exitNode,
							posteriorCFG.enterNode);
					exitNode.addForwardEdge(edge);
					posteriorCFG.enterNode.addBackwardEdge(edge);
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
				this.nodes.addAll(cfg.nodes);
			}
		}
	}
}
