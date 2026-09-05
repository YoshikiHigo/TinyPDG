package lang16_forinit;

public class Lang16ForInit {

	int sumPairs(final int n) {
		int sum = 0;
		for (int i = 0, j = n; i < j; i++, j--) {
			sum = sum + i * j;
		}
		return sum;
	}
}
