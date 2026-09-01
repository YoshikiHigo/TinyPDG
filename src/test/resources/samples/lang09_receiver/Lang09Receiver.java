package lang09_receiver;

import java.util.List;

public class Lang09Receiver {

	int methodInvocationReceiver(final String s) {
		final int n = s.length();
		return n;
	}

	int chainedReceiver(final List<String> values) {
		final String first = values.get(0);
		final int n = first.length();
		return n;
	}

	int qualifiedName(final Holder holder) {
		final int v = holder.field;
		return v;
	}

	int reassignedReceiver(String s) {
		int n = s.length();
		s = "replaced";
		n = n + s.length();
		return n;
	}

	static class Holder {
		int field;
	}
}
