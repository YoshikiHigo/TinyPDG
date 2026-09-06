package yoshikihigo.tinypdg.graphviz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WriterTest {

	@Test
	void leavesPlainTextAlone() {
		assertEquals("int x = 0;", Writer.escapeLabel("int x = 0;"));
	}

	@Test
	void escapesQuotes() {
		assertEquals("s = \\\"a\\\";", Writer.escapeLabel("s = \"a\";"));
	}

	@Test
	void escapesBackslashesBeforeQuotes() {
		// ソースの "a\"b" は、dot のラベルでは \"a\\\"b\" になる。
		assertEquals("\\\"a\\\\\\\"b\\\"", Writer.escapeLabel("\"a\\\"b\""));
	}

	@Test
	void escapesLoneBackslashes() {
		assertEquals("p = \\\"a\\\\\\\\b\\\";", Writer.escapeLabel("p = \"a\\\\b\";"));
	}

	@Test
	void turnsLineBreaksIntoEscapes() {
		assertEquals("{\\nx = 1;\\n}", Writer.escapeLabel("{\r\nx = 1;\n}"));
	}
}
