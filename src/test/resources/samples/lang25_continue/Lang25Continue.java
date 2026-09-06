package lang25_continue;

public class Lang25Continue {

	int forContinue(final int n) {
		int sum = 0;
		for (int i = 0; i < n; i++) {
			if (i % 2 == 0) {
				continue;
			}
			sum = sum + i;
		}
		return sum;
	}
}
