package lang36_exhaustive;

public class Lang36Exhaustive {

	enum Color {
		RED, GREEN
	}

	sealed interface Shape permits Circle, Square {
	}

	record Circle(double radius) implements Shape {
	}

	record Square(double side) implements Shape {
	}

	int expression(final Color c) {
		final int r = switch (c) {
		case RED -> 1;
		case GREEN -> 2;
		};
		return r;
	}

	double patternStatement(final Shape shape) {
		double a = 0.0;
		switch (shape) {
		case Circle circle -> a = circle.radius();
		case Square square -> a = square.side();
		}
		return a;
	}

	int classicStatement(final Color c) {
		int r = 0;
		switch (c) {
		case RED:
			r = 1;
			break;
		case GREEN:
			r = 2;
			break;
		}
		return r;
	}
}
