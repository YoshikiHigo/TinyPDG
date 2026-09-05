package lang14_arraycreation;

public class Lang14ArrayCreation {

	int[] withInitializer() {
		final int[] a = new int[] { 1, 2 };
		return a;
	}

	int[] emptyInitializer() {
		final int[] a = new int[] {};
		return a;
	}

	int[][] nested() {
		final int[][] m = new int[][] { { 1 }, { 2, 3 } };
		return m;
	}
}
