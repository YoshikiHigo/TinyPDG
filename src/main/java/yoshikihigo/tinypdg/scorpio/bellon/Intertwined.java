package yoshikihigo.tinypdg.scorpio.bellon;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Intertwined {

	public static void main(final String[] args) {

		if (2 != args.length) {
			System.err.println("the number of command options must be two.");
			System.err.println("the first one is an output file of SCORPIO.");
			System.err
					.println("the second one is a file for storing intertwined clones.");
			System.exit(1);
		}

		final String input = args[0];
		final String output = args[1];

		final List<BellonClonePair> clonepairs = BellonClonePair.getClonepairs(
				new File(input), 6, false);

		try (final BufferedWriter writer = new BufferedWriter(new FileWriter(
				output, StandardCharsets.UTF_8))) {

			for (final BellonClonePair pair : clonepairs) {

				final BellonCodeFragment left = pair.left;
				final BellonCodeFragment right = pair.right;

				if (left.path.equals(right.path)) {

					final int leftStart = left.first();
					final int leftEnd = left.last();
					final int rightStart = right.first();
					final int rightEnd = right.last();

					if ((leftStart < rightStart) && (leftEnd < rightStart)) {
						continue;
					}

					if ((rightStart < leftStart) && (rightEnd < leftStart)) {
						continue;
					}

					System.out.println(pair.toString());
					writer.write(pair.toString());
					writer.newLine();
				}
			}

			writer.flush();

		} catch (final IOException e) {
			e.printStackTrace();
		}
	}
}
