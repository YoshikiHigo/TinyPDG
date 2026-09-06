package lang48_yieldinloop;

public class Lang48YieldInLoop {

	int firstLarge(final int[] xs) {
		final int r = switch (xs.length) {
		case 0 -> -1;
		default -> {
			for (final int x : xs) {
				if (x > 10) {
					yield x;
				}
			}
			yield 0;
		}
		};
		return r;
	}

	int nestedSwitch(final int a, final int b) {
		final int r = switch (a) {
		case 1 -> {
			switch (b) {
			case 2:
				yield 20;
			default:
				break;
			}
			yield 10;
		}
		default -> 0;
		};
		return r;
	}
}
