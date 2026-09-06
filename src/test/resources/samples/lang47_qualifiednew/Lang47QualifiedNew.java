package lang47_qualifiednew;

public class Lang47QualifiedNew {

	int x = 1;

	class Inner {
		int v;

		int outerX() {
			return Lang47QualifiedNew.this.x + this.v;
		}
	}

	Inner create(final Lang47QualifiedNew outer, final int seed) {
		final Inner in = outer.new Inner();
		in.v = seed;
		return in;
	}
}
