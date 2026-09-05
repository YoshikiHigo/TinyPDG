package yoshikihigo.tinypdg.pdg.node;

import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;

public abstract class PDGNode<T extends ProgramElementInfo> implements
		Comparable<PDGNode<?>> {

	public final T core;
	private final SortedSet<PDGEdge> forwardEdges;
	private final SortedSet<PDGEdge> backwardEdges;

	protected PDGNode(final T core) {
		Objects.requireNonNull(core, "\"core\" is null.");
		this.core = core;
		this.forwardEdges = new TreeSet<>();
		this.backwardEdges = new TreeSet<>();
	}

	public SortedSet<String> getDefinedVariables() {
		return this.core.getAssignedVariables();
	}

	public SortedSet<String> getReferencedVariables() {
		return this.core.getReferencedVariables();
	}

	public boolean addForwardEdge(final PDGEdge edge) {
		Objects.requireNonNull(edge, "\"edge\" is null.");
		assert 0 == this.compareTo(edge.fromNode) : "\"edge.fromNode\" must be the same as this object.";
		return this.forwardEdges.add(edge);
	}

	public boolean addBackwardEdge(final PDGEdge edge) {
		Objects.requireNonNull(edge, "\"edge\" is null.");
		assert 0 == this.compareTo(edge.toNode) : "\"edge.toNode\" must be the same as this object.";
		return this.backwardEdges.add(edge);
	}

	public boolean removeForwardEdge(final PDGEdge edge) {
		Objects.requireNonNull(edge, "\"edge\" is null.");
		assert 0 == this.compareTo(edge.fromNode) : "\"edge.fromNode\" must be the same as this object.";
		return this.forwardEdges.remove(edge);
	}

	public boolean removeBackwardEdge(final PDGEdge edge) {
		Objects.requireNonNull(edge, "\"edge\" is null.");
		assert 0 == this.compareTo(edge.toNode) : "\"edge.toNode\" must be the same as this object.";
		return this.backwardEdges.remove(edge);
	}

	public final SortedSet<PDGEdge> getBackwardEdges() {
		final SortedSet<PDGEdge> edges = new TreeSet<>();
		edges.addAll(this.backwardEdges);
		return edges;
	}

	public final SortedSet<PDGEdge> getForwardEdges() {
		final SortedSet<PDGEdge> edges = new TreeSet<>();
		edges.addAll(this.forwardEdges);
		return edges;
	}

	public void remove() {

		for (final PDGEdge edge : this.getBackwardEdges()) {
			final PDGNode<?> backwardNode = edge.fromNode;
			backwardNode.removeForwardEdge(edge);
		}

		for (final PDGEdge edge : this.getForwardEdges()) {
			final PDGNode<?> forwardNode = edge.toNode;
			forwardNode.removeBackwardEdge(edge);
		}

		this.backwardEdges.clear();
		this.forwardEdges.clear();
	}

	@Override
	public int compareTo(final PDGNode<?> node) {
		Objects.requireNonNull(node, "\"node\" is null.");
		return this.core.compareTo(node.core);
	}

	public String getText() {
		final StringBuilder text = new StringBuilder();
		text.append(this.core.getText());
		text.append(" <");
		if (this.core.startLine == this.core.endLine) {
			text.append(this.core.startLine);
		} else {
			text.append(this.core.startLine);
			text.append("...");
			text.append(this.core.endLine);
		}
		text.append(">");
		return text.toString();
	}
}
