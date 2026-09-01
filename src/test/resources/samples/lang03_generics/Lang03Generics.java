package lang03_generics;

import java.util.ArrayList;
import java.util.List;

public class Lang03Generics {

	@Deprecated
	@SuppressWarnings("unchecked")
	<T extends Comparable<T>> T largest(final List<? extends T> values) {
		T best = null;
		for (final T value : values) {
			if (null == best || 0 < value.compareTo(best)) {
				best = value;
			}
		}
		return best;
	}

	int sum(final int... numbers) {
		int total = 0;
		for (final int n : numbers) {
			total += n;
		}
		return total;
	}

	List<String> make() {
		final List<String> list = new ArrayList<>();
		list.add("a");
		return list;
	}
}
