package yoshikihigo.tinypdg.pdg.edge;

import yoshikihigo.tinypdg.pdg.node.PDGNode;

public abstract class PDGEdge implements Comparable<PDGEdge> {

	final TYPE type;
	final public PDGNode<?> fromNode;
	final public PDGNode<?> toNode;

	protected PDGEdge(final TYPE type, final PDGNode<?> fromNode,
			final PDGNode<?> toNode) {
		assert null != type : "\"type\" is null";
		assert null != fromNode : "\"fromNode\" is null.";
		assert null != toNode : "\"toNode\" is null.";
		this.type = type;
		this.fromNode = fromNode;
		this.toNode = toNode;
	}

	public abstract String getDependenceString();

	@Override
	final public int compareTo(final PDGEdge edge) {
		assert null != edge : "\"edge\" is null.";
		final int fromNodeOrder = this.fromNode.compareTo(edge.fromNode);
		final int toNodeOrder = this.fromNode.compareTo(fromNode);
		if (0 != fromNodeOrder) {
			return fromNodeOrder;
		} else if (0 != toNodeOrder) {
			return toNodeOrder;
		} else {
			return this.type.toString().compareTo(edge.type.toString());
		}
	}

	public enum TYPE {
		CONTROL {
			@Override
			public String toString() {
				return "control";
			}
		},
		DATA {
			@Override
			public String toString() {
				return "date";
			}
		},
		EXECUTION {
			@Override
			public String toString() {
				return "execution";
			}
		};

		abstract public String toString();
	}
}
