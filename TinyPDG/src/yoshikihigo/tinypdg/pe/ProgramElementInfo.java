package yoshikihigo.tinypdg.pe;

import java.util.concurrent.atomic.AtomicInteger;

abstract public class ProgramElementInfo implements
		Comparable<ProgramElementInfo> {

	final static private AtomicInteger ID_GENERATOR = new AtomicInteger(0);

	final public int startLine;
	final public int endLine;
	final public int id;
	private String text;

	public ProgramElementInfo(final int startLine, final int endLine) {
		this.startLine = startLine;
		this.endLine = endLine;
		this.id = ID_GENERATOR.getAndIncrement();
		this.text = "";
	}

	@Override
	final public int hashCode() {
		return this.id;
	}

	@Override
	final public boolean equals(final Object o) {

		if (!(o instanceof ProgramElementInfo)) {
			return false;
		}

		final ProgramElementInfo target = (ProgramElementInfo) o;
		return this.id == target.id;
	}

	final public String getText() {
		return this.text;
	}

	final public void setText(final String text) {
		assert null != text : "\"text\" is null.";
		this.text = text;
	}

	@Override
	public int compareTo(final ProgramElementInfo element) {
		assert null != element : "\"element\" is null.";
		if (this.id < element.id) {
			return -1;
		} else if (this.id > element.id) {
			return 1;
		} else {
			return 0;
		}
	}
}
