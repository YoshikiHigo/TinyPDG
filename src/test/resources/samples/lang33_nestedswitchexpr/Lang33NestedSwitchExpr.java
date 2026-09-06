package lang33_nestedswitchexpr;

import java.util.function.IntUnaryOperator;

public class Lang33NestedSwitchExpr {

	int inArm(final int a, final int b) {
		final int r = switch (a) {
		case 1 -> switch (b) {
			case 1 -> 10;
			default -> 20;
			};
		default -> 0;
		};
		return r;
	}

	int inTernary(final int a, final int b, final boolean c) {
		final int r = switch (a) {
		case 1 -> c ? switch (b) {
			case 1 -> 10;
			default -> 20;
			} : 30;
		default -> 0;
		};
		return r;
	}

	int inStatementArm(final int a, final int b) {
		int r = 0;
		switch (a) {
		case 1 -> r = switch (b) {
			case 1 -> 10;
			default -> 20;
			};
		default -> r = -1;
		}
		return r;
	}

	int inLambda(final int a, final int b) {
		final int r = switch (a) {
		case 1 -> {
			final IntUnaryOperator f = x -> switch (b) {
				case 1 -> x;
				default -> -x;
				};
			yield f.applyAsInt(10);
		}
		default -> 0;
		};
		return r;
	}
}
