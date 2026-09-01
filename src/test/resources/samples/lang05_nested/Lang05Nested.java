package lang05_nested;

public class Lang05Nested {

	static class Inner {
		int value;

		int get() {
			int v = this.value;
			return v;
		}
	}

	int use() {
		final Inner inner = new Inner();
		inner.value = 5;
		final Runnable r = new Runnable() {
			@Override
			public void run() {
				int local = inner.value;
				System.out.println(local);
			}
		};
		r.run();
		return inner.get();
	}
}
