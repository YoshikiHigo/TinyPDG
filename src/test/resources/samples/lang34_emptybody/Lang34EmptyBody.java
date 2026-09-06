package lang34_emptybody;

public class Lang34EmptyBody {

	int emptyThen(final boolean c) {
		int x = 0;
		if (c) {
		}
		x = x + 1;
		return x;
	}

	int emptyElse(final boolean c) {
		int x = 0;
		if (c) {
			x = 1;
		} else {
		}
		return x;
	}

	int emptyLoop(final boolean c) {
		while (c) {
		}
		return 1;
	}
}
