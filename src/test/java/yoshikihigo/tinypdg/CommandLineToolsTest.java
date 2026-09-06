package yoshikihigo.tinypdg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

import yoshikihigo.tinypdg.ast.JavaAstFactory;

class CommandLineToolsTest {

	private static CommandLine parse(final String... args) throws ParseException {
		final Options options = new Options();
		options.addOption(CommandLineTools.sizeOption(false));
		options.addOption(CommandLineTools.javaVersionOption());
		options.addOption(CommandLineTools.threadsOption());
		return new DefaultParser().parse(options, args);
	}

	@Test
	void javaVersionFallsBackToTheDefault() throws ParseException {
		assertEquals(JavaAstFactory.DEFAULT_JAVA_VERSION,
				CommandLineTools.javaVersion(parse()));
	}

	@Test
	void javaVersionAcceptsAVersionJdtKnows() throws ParseException {
		assertEquals("21", CommandLineTools.javaVersion(parse("-j", "21")));
		assertEquals("1.8", CommandLineTools.javaVersion(parse("-j", "1.8")));
	}

	@Test
	void javaVersionRejectsAnUnknownVersion() throws ParseException {
		final CommandLine cmd = parse("-j", "abc");
		assertThrows(TinyPDGException.class, () -> CommandLineTools.javaVersion(cmd));
	}

	@Test
	void threadsFallsBackToOne() throws ParseException {
		assertEquals(1, CommandLineTools.threads(parse()));
	}

	@Test
	void threadsReadsThePositiveValue() throws ParseException {
		assertEquals(4, CommandLineTools.threads(parse("-t", "4")));
	}

	@Test
	void threadsRejectsZeroAndNonNumbers() throws ParseException {
		final CommandLine zero = parse("-t", "0");
		final CommandLine text = parse("-t", "abc");
		assertThrows(TinyPDGException.class, () -> CommandLineTools.threads(zero));
		assertThrows(TinyPDGException.class, () -> CommandLineTools.threads(text));
	}

	@Test
	void sizeFallsBackToTheDefault() throws ParseException {
		assertEquals(5, CommandLineTools.size(parse(), 5));
	}

	@Test
	void sizeReadsThePositiveValue() throws ParseException {
		assertEquals(3, CommandLineTools.size(parse("-s", "3"), 5));
	}

	@Test
	void sizeRejectsZero() throws ParseException {
		final CommandLine cmd = parse("-s", "0");
		assertThrows(TinyPDGException.class, () -> CommandLineTools.size(cmd, 5));
	}

	@Test
	void sizeRejectsNegativeValues() throws ParseException {
		final CommandLine cmd = parse("-s", "-2");
		assertThrows(TinyPDGException.class, () -> CommandLineTools.size(cmd, 5));
	}

	@Test
	void sizeRejectsNonNumbers() throws ParseException {
		final CommandLine cmd = parse("-s", "many");
		assertThrows(TinyPDGException.class, () -> CommandLineTools.size(cmd, 5));
	}
}
