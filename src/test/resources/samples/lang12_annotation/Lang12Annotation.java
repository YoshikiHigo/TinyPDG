package lang12_annotation;

public class Lang12Annotation {

	@interface Marker {

		String value();

		int weight() default 1;

		class Helper {
			int twice(final int n) {
				return n * 2;
			}
		}
	}

	@Marker("m")
	int increment(final int a) {
		return a + 1;
	}
}

@interface Lang12TopLevel {
	String value();
}
