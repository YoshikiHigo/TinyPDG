package yoshikihigo.tinypdg.scorpio.io;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.SortedSet;

import yoshikihigo.tinypdg.pe.ProgramElementInfo;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.NodePairInfo;

public class CSVWriter extends Writer {

	public CSVWriter(final String path,
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

				for (final NodePairInfo nodepair : clonepair.getNodePairs()) {
					final String pairText = this.generateNodePairText(nodepair);
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

	private String generateNodePairText(final NodePairInfo nodepair) {
		final StringBuilder text = new StringBuilder();
		text.append(", ");

		final ProgramElementInfo elementA = nodepair.nodeA.core;
		if (elementA.startLine == elementA.endLine) {
			text.append(Integer.toString(elementA.startLine));
		} else {
			text.append(Integer.toString(elementA.startLine));
			text.append("-");
			text.append(Integer.toString(elementA.endLine));
		}

		text.append(", ");

		final ProgramElementInfo elementB = nodepair.nodeB.core;
		if (elementB.startLine == elementB.endLine) {
			text.append(Integer.toString(elementB.startLine));
		} else {
			text.append(Integer.toString(elementB.startLine));
			text.append("-");
			text.append(Integer.toString(elementB.endLine));
		}

		return text.toString();
	}
}
