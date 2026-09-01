package yoshikihigo.tinypdg.pe;

/**
 * 型の名前を持つだけの要素。
 *
 * <p>final にしてあるのは、コンストラクタが setText を呼ぶため。
 * 派生を許すと、サブクラスのフィールドが初期化される前に this が
 * 親クラスのコードへ渡ることになる。setText 自体は final なので実際に
 * 上書きされる余地はないが、派生させる理由もないので閉じておく。
 */
final public class TypeInfo extends ProgramElementInfo {

	final public String name;

	public TypeInfo(final String name, final int startLine, final int endLine) {
		super(startLine, endLine);
		this.name = name;
		this.setText(name);
	}
}
