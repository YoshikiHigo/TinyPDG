package lang13_super;

import java.util.ArrayList;

public class Lang13Super extends ArrayList<String> {

	int count() {
		return super.size();
	}

	boolean append(final String s, final int n) {
		return super.add(s + n);
	}
}
