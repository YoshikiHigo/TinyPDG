package lang08_patterns;

public class Lang08Patterns {

	sealed interface Shape permits Circle, Square {
	}

	record Circle(double radius) implements Shape {
	}

	record Square(double side) implements Shape {
	}

	String patternInstanceof(final Object o) {
		String result = "other";
		if (o instanceof String s) {
			result = s;
		}
		return result;
	}

	double recordPattern(final Shape shape) {
		double area = 0.0;
		if (shape instanceof Circle(double radius)) {
			area = radius * radius;
		}
		return area;
	}

	String textBlock(final String name) {
		final String template = """
				hello
				world""";
		final String greeting = template + name;
		return greeting;
	}

	int tryWithResources(final java.io.Reader source) {
		int total = 0;
		try (final java.io.BufferedReader reader = new java.io.BufferedReader(source)) {
			total = reader.read();
		} catch (final java.io.IOException e) {
			total = -1;
		}
		return total;
	}
}
