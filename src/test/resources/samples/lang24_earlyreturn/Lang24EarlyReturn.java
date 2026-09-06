package lang24_earlyreturn;

public class Lang24EarlyReturn {

	int guard(final boolean c, final int a) {
		int b = 0;
		if (c) {
			return a;
		}
		b = a + 1;
		return b;
	}

	int inLoop(final int[] xs) {
		int sum = 0;
		for (final int x : xs) {
			if (x < 0) {
				return -1;
			}
			sum = sum + x;
		}
		return sum;
	}

	int thrower(final int a) {
		if (a < 0) {
			throw new IllegalArgumentException();
		}
		return a;
	}

	int viaFinally(final int a) {
		int r = 0;
		try {
			r = a + 1;
			return r;
		} finally {
			r = 0;
		}
	}
}
