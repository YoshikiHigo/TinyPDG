package yoshikihigo.tinypdg.pdg.node;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import yoshikihigo.tinypdg.pe.ExpressionInfo;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.StatementInfo;
import yoshikihigo.tinypdg.pe.VariableInfo;

public class PDGNodeFactory {

	private final SortedMap<ProgramElementInfo, PDGNode<?>> elementToNodeMap;

	public PDGNodeFactory() {
		this.elementToNodeMap = new TreeMap<ProgramElementInfo, PDGNode<?>>();
	}

	public synchronized PDGNode<?> makeNode(final ProgramElementInfo element) {

		assert null != element : "\"element\" is null.";

		PDGNode<?> node = this.elementToNodeMap.get(element);
		if (null != node) {
			return node;
		}

		if (element instanceof ExpressionInfo) {
			node = new PDGControlNode((ExpressionInfo) element);
		}

		else if (element instanceof StatementInfo) {
			node = new PDGNormalNode((StatementInfo) element);
		}

		else if (element instanceof VariableInfo) {
			node = new PDGParameterNode((VariableInfo) element);
		}

		else if (element instanceof MethodInfo) {
			node = PDGMethodEnterNode.getInstance((MethodInfo) element);
		}

		this.elementToNodeMap.put(element, node);

		return node;
	}

	public SortedSet<PDGNode<?>> getAllNodes() {
		final SortedSet<PDGNode<?>> nodes = new TreeSet<PDGNode<?>>();
		nodes.addAll(this.elementToNodeMap.values());
		return nodes;
	}

	public int size() {
		return this.elementToNodeMap.size();
	}
}
