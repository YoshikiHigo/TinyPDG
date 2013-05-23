package yoshikihigo.tinypdg.pdg.edge;

import yoshikihigo.tinypdg.pdg.node.PDGControlNode;
import yoshikihigo.tinypdg.pdg.node.PDGNode;

public class PDGControlDependenceEdge extends PDGEdge {

	final public boolean trueDependence;

	public PDGControlDependenceEdge(final PDGControlNode fromNode,
			final PDGNode<?> toNode, final boolean trueDependence) {
		super(PDGEdge.TYPE.CONTROL, fromNode, toNode);
		this.trueDependence = trueDependence;
	}

	public boolean isTrueDependence() {
		return this.trueDependence;
	}

	public boolean isFalseDependence() {
		return !this.trueDependence;
	}

	@Override
	public String getDependenceString() {
		return this.trueDependence ? "true" : "false";
	}
}
