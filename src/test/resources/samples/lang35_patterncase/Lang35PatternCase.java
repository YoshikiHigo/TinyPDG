package lang35_patterncase;

public class Lang35PatternCase {

	sealed interface Shape permits Circle, Square {
	}

	record Circle(double radius) implements Shape {
	}

	record Square(double side) implements Shape {
	}

	int guarded(final Object o, final int min) {
		int r = 0;
		switch (o) {
		case String s when s.length() > min -> r = s.length();
		case Integer i -> r = i;
		default -> r = -1;
		}
		return r;
	}

	double area(final Shape shape) {
		double a = 0.0;
		switch (shape) {
		case Circle(double radius) -> a = radius * radius * 3.14;
		case Square(double side) -> a = side * side;
		}
		return a;
	}

	int constants(final int x) {
		int r = 0;
		switch (x) {
		case 1 -> r = 10;
		case 2 -> r = 20;
		default -> r = -1;
		}
		return r;
	}
}
