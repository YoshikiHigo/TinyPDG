package lang42_instanceof;

import java.util.List;

public class Lang42Instanceof {

	int plain(final Object o) {
		int r = 0;
		if (o instanceof String) {
			r = 1;
		}
		return r;
	}

	int generic(final Object o) {
		return o instanceof List<?> ? 1 : 0;
	}

	boolean array(final Object o) {
		return o instanceof int[];
	}

	boolean qualified(final Object o) {
		return o instanceof java.util.Map;
	}
}
