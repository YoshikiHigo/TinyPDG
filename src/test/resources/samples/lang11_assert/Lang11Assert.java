package lang11_assert;

public class Lang11Assert {

	int withoutMessage(final int x) {
		assert 0 < x;
		return x;
	}

	int withMessage(final int x, final String label) {
		assert 0 < x : "x must be positive: " + label;
		return x * 2;
	}
}
