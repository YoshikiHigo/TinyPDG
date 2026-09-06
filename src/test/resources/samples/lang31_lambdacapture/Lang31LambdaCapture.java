package lang31_lambdacapture;

import java.util.List;

public class Lang31LambdaCapture {

	int capture(final List<Integer> values) {
		final int[] total = { 0 };
		values.forEach(v -> total[0] += v);
		return total[0];
	}
}
