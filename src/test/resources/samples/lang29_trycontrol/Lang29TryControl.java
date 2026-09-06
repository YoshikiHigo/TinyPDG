package lang29_trycontrol;

public class Lang29TryControl {

	int tryInElse(final boolean c, final int x) {
		int r = 0;
		if (c) {
			r = 1;
		} else {
			try {
				r = x / 2;
			} catch (final ArithmeticException e) {
				r = -1;
			} finally {
				r = r + 1;
			}
		}
		return r;
	}
}
