package lang52_fieldbodies;

import java.util.Comparator;
import java.util.function.IntBinaryOperator;

public class Lang52FieldBodies {

	static final Comparator<String> BY_LENGTH = new Comparator<>() {
		@Override
		public int compare(final String a, final String b) {
			return Integer.compare(a.length(), b.length());
		}
	};

	private final Runnable task = () -> {
		int n = 0;
		n = n + 1;
		System.out.println(n);
	};

	enum Op {
		PLUS((a, b) -> a + b),
		MINUS(new IntBinaryOperator() {
			@Override
			public int applyAsInt(final int a, final int b) {
				return a - b;
			}
		});

		private final IntBinaryOperator operator;

		Op(final IntBinaryOperator operator) {
			this.operator = operator;
		}

		int apply(final int a, final int b) {
			return this.operator.applyAsInt(a, b);
		}
	}
}
