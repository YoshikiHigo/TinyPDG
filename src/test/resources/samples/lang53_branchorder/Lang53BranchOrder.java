package lang53_branchorder;

public class Lang53BranchOrder {

	void straight(final boolean c) {
		if (c) {
			log("a");
		} else {
			warn("b");
		}
	}

	void swapped(final boolean c) {
		if (c) {
			warn("b");
		} else {
			log("a");
		}
	}

	void same(final boolean d) {
		if (d) {
			log("a");
		} else {
			warn("b");
		}
	}

	private void log(final String message) {
	}

	private void warn(final String message) {
	}
}
