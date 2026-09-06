package lang39_exceptionedges;

public class Lang39ExceptionEdges {

	int flowsIntoCatch(final int a) {
		int x = 0;
		try {
			x = risky(a);
			x = x + 1;
		} catch (final IllegalStateException e) {
			x = x - 1;
		}
		return x;
	}

	int flowsIntoFinally(final int a) {
		int x = 0;
		try {
			x = risky(a);
		} finally {
			x = x + 10;
		}
		return x;
	}

	private int risky(final int a) {
		if (a < 0) {
			throw new IllegalStateException();
		}
		return a;
	}
}
