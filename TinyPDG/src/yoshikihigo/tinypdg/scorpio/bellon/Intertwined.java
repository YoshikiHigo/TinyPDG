package yoshikihigo.tinypdg.scorpio.bellon;

import java.io.File;
import java.util.List;

public class Intertwined {

	public static void main(final String[] args) {

		if (1 != args.length) {
			System.err.println("the number of command opetions must be one.");
			System.err.println("the first one is an output file of SCORPIO");
			System.exit(0);
		}

		final String input = args[0];

		final List<ClonePairInfo> clonepairs = ClonePairInfo.getClonepairs(
				new File(input), 6, false);

		for (final ClonePairInfo pair : clonepairs) {

			final CodeFragmentInfo left = pair.left;
			final CodeFragmentInfo right = pair.right;

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
			}

			System.out.println(pair.toString());
		}
	}
}
