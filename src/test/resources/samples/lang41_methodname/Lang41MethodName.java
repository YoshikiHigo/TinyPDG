package lang41_methodname;

public class Lang41MethodName {

	int nameClash(final String s) {
		int length = 3;
		length = length + 1;
		return s.length() + length;
	}

	int superClash(final int size) {
		int hashCode = size;
		hashCode = hashCode + 1;
		return super.hashCode();
	}

	int arguments(final int a, final int b) {
		int max = 0;
		max = Math.max(a, b);
		return max;
	}
}
