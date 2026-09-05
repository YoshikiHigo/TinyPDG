package lang20_cstylearray;

public class Lang20CStyleArray {

	int first(final int values[]) {
		int copy[] = values;
		return copy[0];
	}

	int firstOfModern(final int[] values) {
		int[] copy = values;
		return copy[0];
	}

	int mixed(final int values[]) {
		int copy[] = values, count = 0;
		return copy[count];
	}
}
