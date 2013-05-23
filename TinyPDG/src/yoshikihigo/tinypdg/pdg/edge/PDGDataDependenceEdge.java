package yoshikihigo.tinypdg.pdg.edge;

import yoshikihigo.tinypdg.pdg.node.PDGNode;

public class PDGDataDependenceEdge extends PDGEdge {

	final public String data;

	public PDGDataDependenceEdge(final PDGNode<?> fromNode,
			final PDGNode<?> toNode, final String data) {
		super(PDGEdge.TYPE.DATA, fromNode, toNode);
		this.data = data;
	}

	@Override
	public String getDependenceString() {
		return this.data;
	}
}
