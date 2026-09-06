package lang44_nestedassign;

public class Lang44NestedAssign {

	int inInitializer(final int y0) {
		int y;
		int x = (y = y0) + 1;
		return x + y;
	}

	int inTernary(final boolean c) {
		int y;
		int x = c ? (y = 1) : (y = 2);
		return x + y;
	}

	int inIndex(final int[] a, int i) {
		a[i++] += 1;
		return i;
	}
}
