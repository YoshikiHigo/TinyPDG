package yoshikihigo.tinypdg.pdg.edge;

import yoshikihigo.tinypdg.pdg.node.PDGNode;

public class PDGExecutionDependenceEdge extends PDGEdge {

	public PDGExecutionDependenceEdge(final PDGNode<?> fromNode,
			final PDGNode<?> toNode) {
		super(PDGEdge.TYPE.EXECUTION, fromNode, toNode);
	}

	@Override
	public String getDependenceString() {
		return "";
	}
}
