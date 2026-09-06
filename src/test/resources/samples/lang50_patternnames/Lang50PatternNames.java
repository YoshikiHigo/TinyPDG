package lang50_patternnames;

public class Lang50PatternNames {

	sealed interface Shape permits Circle, Square {
	}

	record Circle(double radius) implements Shape {
	}

	record Square(double side) implements Shape {
	}

	int withS(final Object o) {
		if (o instanceof String s && !s.isEmpty()) {
			return s.length();
		}
		return 0;
	}

	int withT(final Object o) {
		if (o instanceof String t && !t.isEmpty()) {
			return t.length();
		}
		return 0;
	}

	double caseC(final Shape shape, final double min) {
		double a = 0.0;
		switch (shape) {
		case Circle(double r) when r > min -> a = r * r;
		case Circle c -> a = c.radius();
		case Square s -> a = s.side();
		}
		return a;
	}

	double caseD(final Shape shape, final double min) {
		double a = 0.0;
		switch (shape) {
		case Circle(double q) when q > min -> a = q * q;
		case Circle d -> a = d.radius();
		case Square t -> a = t.side();
		}
		return a;
	}
}
