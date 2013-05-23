package yoshikihigo.tinypdg.cfg.node;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.cfg.edge.CFGEdge;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.pe.VariableAssignmentAndReference;

public abstract class CFGNode<T extends ProgramElementInfo> implements
		Comparable<CFGNode<? extends ProgramElementInfo>> {

	protected final SortedSet<CFGEdge> forwardEdges;
	protected final SortedSet<CFGEdge> backwardEdges;

	public final T core;

	protected CFGNode(final T core) {
		this.forwardEdges = new TreeSet<CFGEdge>();
		this.backwardEdges = new TreeSet<CFGEdge>();
		this.core = core;
	}

	public boolean addForwardEdge(final CFGEdge forwardEdge) {

		if (null == forwardEdge) {
			throw new IllegalArgumentException();
		}

		if (!this.equals(forwardEdge.fromNode)) {
			throw new IllegalArgumentException();
		}

		return this.forwardEdges.add(forwardEdge);
	}

	public boolean addBackwardEdge(final CFGEdge backwardEdge) {

		if (null == backwardEdge) {
			throw new IllegalArgumentException();
		}

		if (!this.equals(backwardEdge.toNode)) {
			throw new IllegalArgumentException();
		}

		return this.backwardEdges.add(backwardEdge);
	}

	public boolean removeForwardEdge(final CFGEdge forwardEdge) {

		if (null == forwardEdge) {
			throw new IllegalArgumentException();
		}

		return this.forwardEdges.remove(forwardEdge);
	}

	public boolean removeForwardEdges(final Collection<CFGEdge> forwardEdges) {

		if (null == forwardEdges) {
			throw new IllegalArgumentException();
		}

		return this.forwardEdges.removeAll(forwardEdges);
	}

	public boolean removeBackwardEdge(final CFGEdge backwardEdge) {

		if (null == backwardEdge) {
			throw new IllegalArgumentException();
		}

		return this.backwardEdges.remove(backwardEdge);
	}

	public boolean removeBackwardEdges(final Collection<CFGEdge> backwardEdges) {

		if (null == backwardEdges) {
			throw new IllegalArgumentException();
		}

		return this.backwardEdges.removeAll(backwardEdges);
	}

	public boolean removeForwardNode(
			final CFGNode<? extends ProgramElementInfo> node) {
		assert null != node : "\"node\" is null.";
		final Iterator<CFGEdge> iterator = this.forwardEdges.iterator();
		while (iterator.hasNext()) {
			final CFGEdge edge = iterator.next();
			if (0 == edge.toNode.compareTo(node)) {
				iterator.remove();
				return true;
			}
		}
		return false;
	}

	public boolean removeBackwardNode(
			final CFGNode<? extends ProgramElementInfo> node) {
		assert null != node : "\"node\" is null.";
		final Iterator<CFGEdge> iterator = this.backwardEdges.iterator();
		while (iterator.hasNext()) {
			final CFGEdge edge = iterator.next();
			if (0 == edge.fromNode.compareTo(node)) {
				iterator.remove();
				return true;
			}
		}
		return false;
	}

	public SortedSet<CFGNode<? extends ProgramElementInfo>> getForwardNodes() {
		final SortedSet<CFGNode<? extends ProgramElementInfo>> forwardNodes = new TreeSet<CFGNode<? extends ProgramElementInfo>>();
		for (final CFGEdge forwardEdge : this.getForwardEdges()) {
			forwardNodes.add(forwardEdge.toNode);
		}
		return forwardNodes;
	}

	public SortedSet<CFGEdge> getForwardEdges() {
		return Collections.unmodifiableSortedSet(this.forwardEdges);
	}

	public SortedSet<CFGNode<? extends ProgramElementInfo>> getBackwardNodes() {
		final SortedSet<CFGNode<? extends ProgramElementInfo>> backwardNodes = new TreeSet<CFGNode<? extends ProgramElementInfo>>();
		for (final CFGEdge backwardEdge : this.getBackwardEdges()) {
			backwardNodes.add(backwardEdge.fromNode);
		}
		return backwardNodes;
	}

	public Set<CFGEdge> getBackwardEdges() {
		return Collections.unmodifiableSet(this.backwardEdges);
	}

	@Override
	public int compareTo(final CFGNode<? extends ProgramElementInfo> node) {

		if (null == node) {
			throw new IllegalArgumentException();
		}

		if (this.core.id < node.core.id) {
			return -1;
		} else if (this.core.id > node.core.id) {
			return 1;
		} else {
			return 0;
		}
	}

	public final SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = new TreeSet<String>();
		final SortedSet<String> v = ((VariableAssignmentAndReference) this.core)
				.getAssignedVariables();
		variables.addAll(v);
		return variables;
	}

	public final Set<String> getReferencedVariables() {
		final SortedSet<String> variables = new TreeSet<String>();
		final SortedSet<String> v = ((VariableAssignmentAndReference) this.core)
				.getReferencedVariables();
		variables.addAll(v);
		return variables;
	}

	public final String getText() {
		final StringBuilder text = new StringBuilder();
		text.append(this.core.getText());
		text.append(" <");
		text.append(this.core.startLine);
		text.append(">");
		return text.toString();
	}
}