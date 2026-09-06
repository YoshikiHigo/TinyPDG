package lang37_hoistbody;

import java.util.function.IntUnaryOperator;

public class Lang37HoistBody {

	int inIfBody(final boolean c, final int b) {
		int r = 0;
		if (c)
			r = switch (b) {
			case 1 -> 10;
			default -> 20;
			};
		else
			r = switch (b) {
			case 1 -> -10;
			default -> -20;
			};
		return r;
	}

	int inLoopBody(final int n, final int b) {
		int r = 0;
		for (int i = 0; i < n; i++)
			r = r + switch (b) {
			case 1 -> i;
			default -> 2 * i;
			};
		return r;
	}

	IntUnaryOperator inLambda(final int b) {
		return x -> switch (b) {
		case 1 -> x;
		default -> -x;
		};
	}
}
