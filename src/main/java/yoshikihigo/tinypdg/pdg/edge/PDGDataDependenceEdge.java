package yoshikihigo.tinypdg.pdg.edge;

import java.util.Objects;
import yoshikihigo.tinypdg.pdg.node.PDGNode;

public class PDGDataDependenceEdge extends PDGEdge {

	final public String data;

	public PDGDataDependenceEdge(final PDGNode<?> fromNode,
			final PDGNode<?> toNode, final String data) {
		super(PDGEdge.TYPE.DATA, fromNode, toNode);
		this.data = data;
	}

	@Override
	public PDGEdge replaceFromNode(final PDGNode<?> fromNode) {
		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		return new PDGDataDependenceEdge(fromNode, this.toNode, this.data);
	}

	@Override
	public PDGEdge replaceToNode(final PDGNode<?> toNode) {
		Objects.requireNonNull(toNode, "\"toNode\" is null.");
		return new PDGDataDependenceEdge(this.fromNode, toNode, this.data);
	}

	@Override
	public String getDependenceString() {
		return this.data;
	}
}
