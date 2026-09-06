package lang38_finallyjumps;

public class Lang38FinallyJumps {

	int breakThroughFinally(final int[] values) {
		int count = 0;
		for (final int v : values) {
			try {
				if (v < 0) {
					break;
				}
				count = count + 1;
			} finally {
				count = count * 2;
			}
		}
		return count;
	}

	int continueThroughFinally(final int[] values) {
		int count = 0;
		for (final int v : values) {
			try {
				if (v < 0) {
					continue;
				}
				count = count + 1;
			} finally {
				count = count * 2;
			}
		}
		return count;
	}

	int nestedFinally(final int x) {
		int r = 0;
		try {
			try {
				if (x > 0) {
					return r;
				}
				r = 1;
			} finally {
				r = r + 1;
			}
		} finally {
			r = r + 2;
		}
		return r;
	}
}
