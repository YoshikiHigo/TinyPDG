package lang06_record_interface;

public record Lang06RecordInterface(int x, int y) implements Summable {

	int sum() {
		int s = this.x + this.y;
		return s;
	}

	static Lang06RecordInterface origin() {
		final Lang06RecordInterface p = new Lang06RecordInterface(0, 0);
		return p;
	}
}

interface Summable {

	int sum();

	default int doubledSum() {
		int d = this.sum() * 2;
		return d;
	}
}

enum TopLevelEnum {
	A, B;

	int index() {
		int i = this.ordinal();
		return i;
	}
}
