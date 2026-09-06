package lang28_labeledblock;

public class Lang28LabeledBlock {

	int labeledBlock(final boolean c) {
		int x = 0;
		L: {
			if (c) {
				break L;
			}
			x = 1;
		}
		return x;
	}

	int finallyWithTwoStatements(final int x) {
		int r = 0;
		try {
			r = x + 1;
		} finally {
			r = r * 2;
			r = r + 1;
		}
		return r;
	}
}
