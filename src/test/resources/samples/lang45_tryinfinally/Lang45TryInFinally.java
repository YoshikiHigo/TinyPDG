package lang45_tryinfinally;

public class Lang45TryInFinally {

	int nestedTryInFinally(final int a) {
		int r = 0;
		try {
			r = risky(a);
		} finally {
			try {
				r = r + 1;
			} catch (final RuntimeException e) {
				r = -2;
			}
		}
		return r;
	}

	private int risky(final int a) {
		if (a < 0) {
			throw new IllegalStateException();
		}
		return a;
	}
}
