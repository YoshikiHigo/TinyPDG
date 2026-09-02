package yoshikihigo.tinypdg.pdg.edge;

import java.util.Objects;
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
	public PDGEdge replaceFromNode(final PDGNode<?> fromNode) {
		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		// 条件が反転していた。直後で PDGControlNode へキャストしているのだから、
		// 求めているのは「制御ノードであること」である。
		assert fromNode instanceof PDGControlNode : "\"fromNode\" must be an instance of PDGControlNode.";
		return new PDGControlDependenceEdge((PDGControlNode) fromNode,
				this.toNode, this.trueDependence);
	}

	@Override
	public PDGEdge replaceToNode(final PDGNode<?> toNode) {
		Objects.requireNonNull(toNode, "\"toNode\" is null.");
		return new PDGControlDependenceEdge((PDGControlNode) this.fromNode,
				toNode, this.trueDependence);
	}

	@Override
	public String getDependenceString() {
		return this.trueDependence ? "true" : "false";
	}
}
