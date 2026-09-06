package yoshikihigo.tinypdg.pe;

import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public class VariableInfo extends ProgramElementInfo {

	private CATEGORY category;
	final public TypeInfo type;
	final public String name;

	public VariableInfo(final CATEGORY category, final TypeInfo type,
			final String name, final int startLine, final int endLine) {
		super(startLine, endLine);
		this.category = category;
		this.type = type;
		this.name = name;
	}

	public void setCategory(final CATEGORY category) {
		Objects.requireNonNull(category, "\"category\" is null.");
		this.category = category;
	}

	public CATEGORY getCategory() {
		return this.category;
	}

	/**
	 * 宣言はその変数を定義する。メソッドの引数、catch の例外変数、foreach の
	 * 取り出す変数がこれである。
	 *
	 * <p>以前は空だった。メソッドの引数は PDG が名前を直接見て特別に扱って
	 * いたので困らなかったが、catch (E e) の e は CFG のノードでありながら
	 * 何も定義せず、本体で e を使ってもデータ依存の辺が出なかった。
	 */
	@Override
	public SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = new TreeSet<>();
		variables.add(this.name);
		return variables;
	}

	public enum CATEGORY {
		FIELD, LOCAL, PARAMETER;
	}
}
