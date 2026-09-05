package lang15_jumpincatch;

public class Lang15JumpInCatch {

	int breakInCatch(final int[] values) {
		int sum = 0;
		for (final int value : values) {
			try {
				sum = sum + check(value);
			} catch (final RuntimeException e) {
				break;
			}
		}
		return sum;
	}

	int continueInCatch(final int[] values) {
		int sum = 0;
		for (final int value : values) {
			try {
				sum = sum + check(value);
			} catch (final RuntimeException e) {
				continue;
			}
			sum = sum + 1;
		}
		return sum;
	}

	private int check(final int value) {
		if (value < 0) {
			throw new IllegalArgumentException();
		}
		return value;
	}
}
