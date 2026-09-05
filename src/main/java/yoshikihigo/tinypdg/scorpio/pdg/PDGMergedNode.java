package yoshikihigo.tinypdg.scorpio.pdg;

import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGExecutionDependenceEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNormalNode;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.scorpio.NormalizedText;

public class PDGMergedNode extends PDGNormalNode<ProgramElementInfo> {

	public static void mergeNodes(final PDG pdg) {

		Objects.requireNonNull(pdg, "\"pdg\" is null.");

		MERGE: while (true) {

			final SortedSet<PDGEdge> edges = pdg.getAllEdges();
			for (final PDGEdge edge : edges) {

				if (!(edge instanceof PDGExecutionDependenceEdge)) {
					continue;
				}

				final PDGNode<?> fromNode = edge.fromNode;
				final PDGNode<?> toNode = edge.toNode;

				if (!(fromNode instanceof PDGNormalNode<?>)
						|| !(toNode instanceof PDGNormalNode<?>)) {
					continue;
				}

				if (NormalizedText.normalize(fromNode.core).equals(
						NormalizedText.normalize(toNode.core))) {

					final PDGMergedNode mergedNode = new PDGMergedNode(
							(PDGNormalNode<?>) fromNode,
							(PDGNormalNode<?>) toNode);

					fromNode.removeForwardEdge(edge);
					toNode.removeBackwardEdge(edge);

					replace(fromNode, mergedNode);
					replace(toNode, mergedNode);

					// 併合した元のノードは辺を全て移されて宙に浮く。要素から
					// ノードを引く対応表を直しておかないと、PDG がノードを
					// 数え直したときに、その宙に浮いたノードが出てくる。
					// getOriginalNodes は入れ子の併合も平らにして返すので、
					// 併合が重なっても取りこぼさない。
					for (final PDGNormalNode<?> original : mergedNode
							.getOriginalNodes()) {
						pdg.getPDGNodeFactory().replaceNode(original.core,
								mergedNode);
					}

					continue MERGE;
				}
			}

			break;
		}
	}

	private static void replace(final PDGNode<?> replacedNode,
			final PDGNode<?> replacingNode) {

		Objects.requireNonNull(replacedNode, "\"replacedNode\" is null.");
		Objects.requireNonNull(replacingNode, "\"replacingNode\" is null.");

		final SortedSet<PDGEdge> backwardEdges = replacedNode
				.getBackwardEdges();
		for (final PDGEdge backwardEdge : backwardEdges) {
			final boolean b1 = backwardEdge.fromNode
					.removeForwardEdge(backwardEdge);
			final boolean b2 = replacedNode.removeBackwardEdge(backwardEdge);

			assert b1 : "invalid status.";
			assert b2 : "invalid status.";

			backwardEdge.replaceToNode(replacingNode).connect();
		}

		final SortedSet<PDGEdge> forwardEdges = replacedNode.getForwardEdges();
		for (final PDGEdge forwardEdge : forwardEdges) {
			final boolean b1 = forwardEdge.toNode
					.removeBackwardEdge(forwardEdge);
			final boolean b2 = replacedNode.removeForwardEdge(forwardEdge);

			assert b1 : "invalid status.";
			assert b2 : "invalid status.";

			forwardEdge.replaceFromNode(replacingNode).connect();
		}
	}

	final private SortedSet<PDGNormalNode<?>> originalNodes;

	public PDGMergedNode(final PDGNormalNode<?> node1,
			final PDGNormalNode<?> node2) {
		super(node1.core);
		this.originalNodes = new TreeSet<>(
				new LocationalComparator());
		this.add(node1);
		this.add(node2);
	}

	public SortedSet<PDGNormalNode<?>> getOriginalNodes() {
		final SortedSet<PDGNormalNode<?>> nodes = new TreeSet<>(
				new LocationalComparator());
		nodes.addAll(this.originalNodes);
		return nodes;
	}

	@Override
	public String getText() {
		final StringBuilder text = new StringBuilder();
		for (final PDGNode<?> node : this.originalNodes) {
			text.append(node.getText());
			text.append(System.lineSeparator());
		}
		return text.toString();
	}

	private void add(final PDGNormalNode<?> node) {
		Objects.requireNonNull(node, "\"node\" is null.");

		if (node instanceof PDGMergedNode) {
			final SortedSet<PDGNormalNode<?>> originalNodes = ((PDGMergedNode) node)
					.getOriginalNodes();
			for (final PDGNormalNode<?> originalNode : originalNodes) {
				this.add(originalNode);
			}
		}

		else {
			this.originalNodes.add(node);
		}
	}

	class LocationalComparator implements Comparator<PDGNormalNode<?>> {

		@Override
		public int compare(PDGNormalNode<?> o1, PDGNormalNode<?> o2) {

			Objects.requireNonNull(o1, "\"o1\" is null.");
			Objects.requireNonNull(o2, "\"o2\" is null.");

			if (o1.core.startLine < o2.core.startLine) {
				return -1;
			} else if (o1.core.startLine > o2.core.startLine) {
				return 1;
			} else if (o1.core.endLine < o2.core.endLine) {
				return -1;
			} else if (o1.core.endLine > o2.core.endLine) {
				return 1;
			} else {
				return 0;
			}
		}
	}
}
