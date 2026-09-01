package yoshikihigo.tinypdg.pdg.node;

import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import yoshikihigo.tinypdg.TinyPDGException;
import yoshikihigo.tinypdg.cfg.node.CFGControlNode;
import yoshikihigo.tinypdg.cfg.node.CFGNode;
import yoshikihigo.tinypdg.cfg.node.CFGNormalNode;
import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.VariableInfo;

public class PDGNodeFactory {

	private final ConcurrentMap<ProgramElementInfo, PDGNode<?>> elementToNodeMap;

	public PDGNodeFactory() {
		this.elementToNodeMap = new ConcurrentHashMap<>();
	}

	public PDGNode<?> makeNode(final CFGNode<?> node) {

		Objects.requireNonNull(node, "\"node\" is null.");

		if (node instanceof CFGControlNode) {
			return this.makeControlNode(node.core);
		}

		else if (node instanceof CFGNormalNode) {
			return this.makeNormalNode(node.core);
		}

		else {
			// 以前は表明を置いて null を返していた。表明は既定で無効なので
			// 実際には null が返り、離れた場所で NullPointerException になっていた。
			throw new TinyPDGException(
					"PDG ノードを作れない CFG ノードです: " + node.getClass().getName());
		}
	}

	public synchronized PDGNode<?> makeControlNode(
			final ProgramElementInfo element) {

		Objects.requireNonNull(element, "\"element\" is null.");

		PDGNode<?> node = this.elementToNodeMap.get(element);
		if (null != node) {
			return node;
		}

		if (element instanceof ExpressionInfo) {
			node = new PDGControlNode(element);
		}

		else if (element instanceof VariableInfo) {
			node = new PDGControlNode(element);
		}

		else if (element instanceof MethodInfo) {
			node = PDGMethodEnterNode.getInstance((MethodInfo) element);
		}

		else {
			throw new TinyPDGException(
					"制御ノードを作れない要素です: " + element.getClass().getName());
		}

		this.elementToNodeMap.put(element, node);

		return node;
	}

	public synchronized PDGNode<?> makeNormalNode(
			final ProgramElementInfo element) {

		Objects.requireNonNull(element, "\"element\" is null.");

		PDGNode<?> node = this.elementToNodeMap.get(element);
		if (null != node) {
			return node;
		}

		if (element instanceof ExpressionInfo) {
			node = new PDGExpressionNode((ExpressionInfo) element);
		}

		else if (element instanceof StatementInfo) {
			node = new PDGStatementNode((StatementInfo) element);
		}

		else if (element instanceof VariableInfo) {
			node = new PDGParameterNode((VariableInfo) element);
		}

		else {
			throw new TinyPDGException(
					"通常ノードを作れない要素です: " + element.getClass().getName());
		}

		this.elementToNodeMap.put(element, node);

		return node;
	}

	public SortedSet<PDGNode<?>> getAllNodes() {
		final SortedSet<PDGNode<?>> nodes = new TreeSet<>();
		nodes.addAll(this.elementToNodeMap.values());
		return nodes;
	}

	public int size() {
		return this.elementToNodeMap.size();
	}
}
