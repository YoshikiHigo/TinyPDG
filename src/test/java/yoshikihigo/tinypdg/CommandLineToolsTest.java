package yoshikihigo.tinypdg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

class CommandLineToolsTest {

	private static CommandLine parse(final String... args) throws ParseException {
		final Options options = new Options();
		options.addOption(CommandLineTools.sizeOption(false));
		return new DefaultParser().parse(options, args);
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
