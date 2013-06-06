package yoshikihigo.tinypdg.scorpio.io;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.SortedSet;

import yoshikihigo.tinypdg.pdg.edge.PDGControlDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGDataDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGExecutionDependenceEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.EdgePairInfo;
import yoshikihigo.tinypdg.scorpio.pdg.PDGMergedNode;

public class CSVEdgeWriter extends Writer {

	public CSVEdgeWriter(final String path,
			final SortedSet<ClonePairInfo> clonepairs) {
		super(path, clonepairs);
	}

	@Override
	public void write() {

		try {

			final BufferedWriter writer = new BufferedWriter(new FileWriter(
					this.path));

			int identifier = 0;
			for (final ClonePairInfo clonepair : this.clonepairs) {
				writer.write(Integer.toString(identifier++));
				writer.write(", ");
				writer.write(clonepair.pathA);
				writer.write(", ");
				writer.write(clonepair.pathB);
				writer.newLine();

				for (final EdgePairInfo edgepair : clonepair.getEdgePairs()) {
					final String pairText = this.generateEdgePairText(edgepair);
					writer.write(pairText);
					writer.newLine();
				}
			}

			writer.close();

		} catch (final IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private String generateEdgePairText(final EdgePairInfo edgepair) {
		final StringBuilder text = new StringBuilder();
		text.append(",");
		text.append(this.generateEdgeText(edgepair.edgeA));
		text.append(",");
		text.append(this.generateEdgeText(edgepair.edgeB));
		return text.toString();
	}

	private String generateEdgeText(final PDGEdge edge) {
		final StringBuilder text = new StringBuilder();

		if (edge instanceof PDGDataDependenceEdge) {
			text.append("D:");
		} else if (edge instanceof PDGControlDependenceEdge) {
			text.append("C:");
		} else if (edge instanceof PDGExecutionDependenceEdge) {
			text.append("E:");
		} else {
			assert false : "invalid status.";
		}

		text.append(this.generateNodeText(edge.fromNode));
		text.append("->");
		text.append(this.generateNodeText(edge.toNode));

		return text.toString();
	}

	private String generateNodeText(final PDGNode<?> node) {

		final StringBuilder text = new StringBuilder();
		if (node instanceof PDGMergedNode) {
			for (final PDGNode<?> originalNode : ((PDGMergedNode) node)
					.getOriginalNodes()) {
				text.append(this.generateProgramElementText(originalNode.core));
			}
		} else {
			text.append(this.generateProgramElementText(node.core));
		}
		return text.toString();
	}

	private String generateProgramElementText(final ProgramElementInfo element) {

		final StringBuilder text = new StringBuilder();
		text.append("[");
		if (element.startLine == element.endLine) {
			text.append(Integer.toString(element.startLine));
		} else {
			text.append(Integer.toString(element.startLine));
			text.append("-");
			text.append(Integer.toString(element.endLine));
		}
		text.append("]");
		return text.toString();
	}
}
