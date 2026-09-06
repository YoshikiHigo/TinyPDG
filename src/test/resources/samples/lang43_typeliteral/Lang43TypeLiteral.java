package lang43_typeliteral;

public class Lang43TypeLiteral {

	int literal(final Object o) {
		final Class<?> c = String.class;
		int r = 0;
		if (c.isInstance(o)) {
			r = 1;
		}
		return r;
	}

	String name() {
		return describe(int[].class);
	}

	private String describe(final Class<?> c) {
		return c.getName();
	}
}
