package lang40_controldep;

public class Lang40ControlDep {

	int guard(final int x) {
		if (x < 0) {
			return -1;
		}
		final int y = x * 2;
		return y;
	}

	int loopBreak(final int[] values) {
		int sum = 0;
		for (final int v : values) {
			if (v < 0) {
				break;
			}
			sum = sum + v;
		}
		return sum;
	}

	int whileLoop(int n) {
		while (n > 0) {
			n = n - 1;
		}
		return n;
	}

	int afterCatch(final int a) {
		int x = 0;
		try {
			x = a * 2;
		} catch (final RuntimeException e) {
			x = -1;
		}
		return x;
	}
}
