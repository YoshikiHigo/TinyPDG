package yoshikihigo.tinypdg.pe;

/**
 * 演算子の名前を持つだけの要素。final にしている理由は TypeInfo と同じ。
 */
final public class OperatorInfo extends ProgramElementInfo {

	final public String name;

	public OperatorInfo(final String name, final int startLine,
			final int endLine) {
		super(startLine, endLine);
		this.name = name;
		this.setText(name);
	}
}
