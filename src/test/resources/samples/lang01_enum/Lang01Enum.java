package lang01_enum;

public class Lang01Enum {

	enum Color {
		RED(1), GREEN(2), BLUE(3);

		private final int code;

		Color(final int code) {
			this.code = code;
		}

		int doubled() {
			int d = this.code * 2;
			return d;
		}
	}

	int use() {
		int total = 0;
		for (Color c : Color.values()) {
			total = total + c.doubled();
		}
		return total;
	}
}
