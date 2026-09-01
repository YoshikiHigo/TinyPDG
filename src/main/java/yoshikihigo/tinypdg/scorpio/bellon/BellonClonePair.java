package yoshikihigo.tinypdg.scorpio.bellon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import yoshikihigo.tinypdg.TinyPDGException;

/**
 * Bellon のベンチマークが使う形式のクローンペア。
 *
 * <p>scorpio.data.ClonePairInfo とは別物で、あちらは Scorpio が検出した
 * クローンペアを表す。以前は両方とも ClonePairInfo という名前だった。
 */
public class BellonClonePair {

	final BellonCodeFragment left;
	final BellonCodeFragment right;
	final int type;

	BellonClonePair(final BellonCodeFragment left, final BellonCodeFragment right,
			final int type) {
		this.left = left;
		this.right = right;
		this.type = type;
	}

	BellonClonePair(final BellonCodeFragment left, final BellonCodeFragment right) {
		this(left, right, 0);
	}

	int size() {
		return Math.min(this.left.size(), this.right.size());
	}
	
	@Override
	public String toString(){
		final StringBuilder text = new StringBuilder();
		text.append(this.left.toString());
		text.append("\t");
		text.append(this.right.toString());
		return text.toString();
}

	static public List<BellonClonePair> getClonepairs(final File file,
			final int minimum, final boolean oracle) {

		final List<BellonClonePair> clonepairs = new ArrayList<>();

		try (final BufferedReader reader = new BufferedReader(new FileReader(
				file, StandardCharsets.UTF_8))) {
			String line;
			while (null != (line = reader.readLine())) {
				final BellonClonePair pair = BellonClonePair.getClonepair(line,
						oracle);
				if (minimum <= pair.size()) {
					clonepairs.add(pair);
				}
			}

		} catch (final Exception e) {
			throw new TinyPDGException(
					"クローンペアを読み込めませんでした: " + file, e);
		}

		return clonepairs;
	}

	static private BellonClonePair getClonepair(final String line,
			final boolean oracle) {

		final StringTokenizer lineTokenizer = new StringTokenizer(line, "\t");
		final String leftPath = lineTokenizer.nextToken();
		final String leftStartLine = lineTokenizer.nextToken();
		final String leftEndLine = lineTokenizer.nextToken();
		final String rightPath = lineTokenizer.nextToken();
		final String rightStartLine = lineTokenizer.nextToken();
		final String rightEndLine = lineTokenizer.nextToken();
		final int type;
		if (oracle) {
			type = Integer.parseInt(lineTokenizer.nextToken());
		} else {
			type = 0;
		}
		final String leftGaps = lineTokenizer.nextToken();
		final String rightGaps = lineTokenizer.nextToken();

		final BellonCodeFragment leftFragment = new BellonCodeFragment(leftPath,
				Integer.parseInt(leftStartLine), Integer.parseInt(leftEndLine));
		final BellonCodeFragment rightFragment = new BellonCodeFragment(rightPath,
				Integer.parseInt(rightStartLine),
				Integer.parseInt(rightEndLine));

		if (!leftGaps.equals("-")) {
			final StringTokenizer gapTokenizer = new StringTokenizer(leftGaps,
					", ");
			while (gapTokenizer.hasMoreTokens()) {
				final String gap = gapTokenizer.nextToken();
				leftFragment.remove(Integer.parseInt(gap));
			}
		}

		if (!rightGaps.equals("-")) {
			final StringTokenizer gapTokenizer = new StringTokenizer(rightGaps,
					", ");
			while (gapTokenizer.hasMoreTokens()) {
				final String gap = gapTokenizer.nextToken();
				rightFragment.remove(Integer.parseInt(gap));
			}
		}

		return new BellonClonePair(leftFragment, rightFragment, type);
	}
}
