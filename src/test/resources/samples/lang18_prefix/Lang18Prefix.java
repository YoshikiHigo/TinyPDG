package lang18_prefix;

public class Lang18Prefix {

	int prefixes(final int x, final boolean flag) {
		int i = x;
		++i;
		--i;
		final int negated = -i;
		final boolean inverted = !flag;
		return inverted ? negated : i;
	}
}
