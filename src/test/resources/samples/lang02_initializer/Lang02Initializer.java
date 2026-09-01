package lang02_initializer;

public class Lang02Initializer {

	static int counter = 10;

	int instanceField = counter + 1;

	static {
		counter = counter * 2;
	}

	{
		this.instanceField = this.instanceField + 1;
	}

	int method() {
		int a = this.instanceField;
		return a;
	}
}
