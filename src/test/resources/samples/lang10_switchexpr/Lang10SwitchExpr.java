package lang10_switchexpr;

public class Lang10SwitchExpr {

	int arrowArms(final int x) {
		final int base = 100;
		int y = switch (x) {
			case 1, 2 -> base + 10;
			case 3 -> base + 20;
			default -> 0;
		};
		return y;
	}

	int blockArmWithYield(final int x) {
		int a = 0;
		int y = switch (x) {
			case 1 -> {
				a = 5;
				yield a * 2;
			}
			default -> 20;
		};
		return a + y;
	}

	int insideLoopCondition(final int limit) {
		int count = 0;
		while (count < (switch (limit) { case 0 -> 1; default -> limit; })) {
			count = count + 1;
		}
		return count;
	}

	int insideShortCircuit(final int x, final boolean flag) {
		int r = 0;
		if (flag && 0 < (switch (x) { case 1 -> 5; default -> 0; })) {
			r = 1;
		}
		return r;
	}
}
