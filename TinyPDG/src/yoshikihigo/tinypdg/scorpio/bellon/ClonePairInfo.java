package yoshikihigo.tinypdg.scorpio.bellon;

public class ClonePairInfo {

	final CodeFragmentInfo left;
	final CodeFragmentInfo right;
	final int type;

	ClonePairInfo(final CodeFragmentInfo left, final CodeFragmentInfo right,
			final int type) {
		this.left = left;
		this.right = right;
		this.type = type;
	}

	ClonePairInfo(final CodeFragmentInfo left, final CodeFragmentInfo right) {
		this(left, right, 0);
	}
}
