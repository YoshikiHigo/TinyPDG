package yoshikihigo.tinypdg.pe;

abstract public class ProgramElementInfo {

	final public int startLine;
	final public int endLine;

	public ProgramElementInfo(final int startLine, final int endLine) {
		this.startLine = startLine;
		this.endLine = endLine;
	}
}
