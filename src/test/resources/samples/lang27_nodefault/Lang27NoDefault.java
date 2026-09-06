package lang27_nodefault;

public class Lang27NoDefault {

	int noDefault(final int x) {
		int r = 0;
		switch (x) {
		case 1:
			r = 10;
			break;
		}
		return r;
	}

	int nullAndDefault(final Object o) {
		int r = 0;
		switch (o) {
		case String s -> r = s.length();
		case null, default -> r = -1;
		}
		return r;
	}
}
