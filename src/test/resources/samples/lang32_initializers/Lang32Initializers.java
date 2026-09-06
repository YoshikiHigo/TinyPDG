package lang32_initializers;

import java.util.ArrayList;
import java.util.List;

public class Lang32Initializers {

	static int counter = 10;

	int instanceField = counter + 1;

	static {
		counter = counter * 2;
	}

	{
		this.instanceField = this.instanceField + 1;
	}

	enum Op {
		PLUS {
			@Override
			int apply(final int a, final int b) {
				return a + b;
			}
		},
		MINUS {
			@Override
			int apply(final int a, final int b) {
				return a - b;
			}
		};

		abstract int apply(int a, int b);
	}

	List<Integer> doubleBrace() {
		return new ArrayList<Integer>() {
			{
				add(1);
				add(2);
			}
		};
	}
}
