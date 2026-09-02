package lang07_lambda;

import java.util.List;
import java.util.function.Function;

public class Lang07Lambda {

	int expressionBody(final List<String> values) {
		final Function<String, Integer> length = s -> s.length();
		int total = 0;
		for (final String v : values) {
			total = total + length.apply(v);
		}
		return total;
	}

	Runnable blockBody(final int seed) {
		final Runnable r = () -> {
			int local = seed * 2;
			System.out.println(local);
		};
		return r;
	}

	int methodReference(final List<String> values) {
		final int sum = values.stream().map(String::length).reduce(0, Integer::sum);
		return sum;
	}

	Function<Integer, int[]> typedParameter() {
		final Function<Integer, int[]> f = (Integer n) -> new int[n];
		return f;
	}
}
