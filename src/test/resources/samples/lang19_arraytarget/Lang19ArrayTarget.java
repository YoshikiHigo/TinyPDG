package lang19_arraytarget;

public class Lang19ArrayTarget {

	static final class Box {
		int value;
	}

	int[] fill(final int[] a, final int i, final int x) {
		a[i] = x;
		a[i + 1] += x;
		a[i]++;
		return a;
	}

	int store(final Box box, final int x) {
		box.value = x;
		return box.value;
	}
}
