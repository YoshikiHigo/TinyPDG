package lang21_multideclaration;

public class Lang21MultiDeclaration {

	int chain(final int x) {
		int a = x, b = a + 1;
		return a + b;
	}

	int chainSeparately(final int x) {
		int a = x;
		int b = a + 1;
		return a + b;
	}

	int nestedBlock(final int x) {
		int result = 0;
		{
			int doubled = x * 2;
			result = doubled;
		}
		return result;
	}
}
