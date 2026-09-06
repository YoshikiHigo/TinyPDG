package lang49_quotechar;

public class Lang49QuoteChar {

	boolean isQuote(final char c) {
		return c == '"' || c == "x".charAt(0);
	}

	boolean isOther(final char c, final char d) {
		return c == '"' || d == "y".charAt(0);
	}
}
