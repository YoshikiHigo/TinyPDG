package lang26_arrowswitch;

public class Lang26ArrowSwitch {

	int arrows(final int x) {
		int r = 0;
		switch (x) {
		case 1 -> r = 10;
		case 2 -> {
			r = 20;
			r = r + 1;
		}
		case 3 -> throw new IllegalArgumentException();
		default -> r = 30;
		}
		return r;
	}
}
