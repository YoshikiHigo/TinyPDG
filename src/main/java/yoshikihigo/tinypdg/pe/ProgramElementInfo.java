package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

abstract public class ProgramElementInfo implements
		Comparable<ProgramElementInfo> {

	final static private AtomicInteger ID_GENERATOR = new AtomicInteger(0);

	/**
	 * ソース上の位置順。開始行、次に終了行で比べる。
	 *
	 * <p>要素の自然順序は id、つまり作られた順である。出力を行の順に並べたい
	 * ところが 2 か所あり、それぞれが同じ Comparator を手書きしていた。
	 */
	public static final Comparator<ProgramElementInfo> BY_LOCATION = Comparator
			.comparingInt((ProgramElementInfo e) -> e.startLine)
			.thenComparingInt(e -> e.endLine);

	final public int startLine;
	final public int endLine;
	final public int id;
	private String text;

	final private List<String> modifiers;

	protected BlockInfo ownerConditionalBlock;

	public ProgramElementInfo(final int startLine, final int endLine) {
		this.startLine = startLine;
		this.endLine = endLine;
		this.id = ID_GENERATOR.getAndIncrement();
		this.text = "";

		this.modifiers = new ArrayList<>();

		this.ownerConditionalBlock = null;
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
		Objects.requireNonNull(text, "\"text\" is null.");
		this.text = text;
	}

	@Override
	final public int compareTo(final ProgramElementInfo element) {
		Objects.requireNonNull(element, "\"element\" is null.");
		return Integer.compare(this.id, element.id);
	}

	final public void addModifier(final String modifier) {
		Objects.requireNonNull(modifier, "\"modifier\" is null.");
		this.modifiers.add(modifier);
	}

	final public List<String> getModifiers() {
		final List<String> modifiers = new ArrayList<>();
		modifiers.addAll(this.modifiers);
		return modifiers;
	}

	public SortedSet<String> getAssignedVariables() {
		return new TreeSet<>();
	}

	public SortedSet<String> getReferencedVariables() {
		return new TreeSet<>();
	}

	public void setOwnerConditinalBlock(final BlockInfo ownerConditionalBlock) {
		Objects.requireNonNull(ownerConditionalBlock, "\"ownerConditionalBlock\" is null.");
		this.ownerConditionalBlock = ownerConditionalBlock;
	}

	public BlockInfo getOwnerConditionalBlock() {
		return this.ownerConditionalBlock;
	}
}
