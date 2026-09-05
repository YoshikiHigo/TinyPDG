package yoshikihigo.tinypdg.pdg.edge;

import java.util.Objects;
import yoshikihigo.tinypdg.pdg.node.PDGNode;

public abstract class PDGEdge implements Comparable<PDGEdge> {

	final public TYPE type;
	final public PDGNode<?> fromNode;
	final public PDGNode<?> toNode;

	protected PDGEdge(final TYPE type, final PDGNode<?> fromNode,
			final PDGNode<?> toNode) {
		Objects.requireNonNull(type, "\"type\" is null");
		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		Objects.requireNonNull(toNode, "\"toNode\" is null.");
		this.type = type;
		this.fromNode = fromNode;
		this.toNode = toNode;
	}

	public abstract PDGEdge replaceFromNode(PDGNode<?> fromNode);

	public abstract PDGEdge replaceToNode(PDGNode<?> toNode);

	public abstract String getDependenceString();

	@Override
	public boolean equals(final Object o) {

		if (null == o) {
			return false;
		}
		if (!(o instanceof PDGEdge)) {
			return false;
		}

		return 0 == this.compareTo((PDGEdge) o);
	}

	@Override
	public int hashCode() {
		return fromNode.core.id + toNode.core.id + this.type.hashCode()
				+ this.getDependenceString().hashCode();
	}

	/**
	 * 辺の同一性は、両端のノードと種別に加えて「何についての依存か」で決まる。
	 *
	 * <p>依存対象を見ないと、同じノード対を結ぶデータ依存が変数ごとに 1 本ずつ
	 * あっても区別できない。辺は TreeSet に入るので、区別できないものは
	 * 先に入った 1 本だけが残り、どれが残るかは挿入順まかせになる。PDG の
	 * 通常の定義ではデータ依存辺は (定義, 使用, 変数) の組ごとに存在する。
	 *
	 * <p>CFGEdge.compareTo は以前からこの比較を行っていた。ここだけ漏れていた。
	 */
	@Override
	final public int compareTo(final PDGEdge edge) {
		Objects.requireNonNull(edge, "\"edge\" is null.");

		final int fromNodeOrder = this.fromNode.compareTo(edge.fromNode);
		if (0 != fromNodeOrder) {
			return fromNodeOrder;
		}

		final int toNodeOrder = this.toNode.compareTo(edge.toNode);
		if (0 != toNodeOrder) {
			return toNodeOrder;
		}

		final int typeOrder = this.type.toString().compareTo(edge.type.toString());
		if (0 != typeOrder) {
			return typeOrder;
		}

		return this.getDependenceString().compareTo(edge.getDependenceString());
	}

	public boolean connectedWith(final PDGEdge edge) {
		Objects.requireNonNull(edge, "\"edge\" is null.");
		return (0 == this.fromNode.compareTo(edge.fromNode))
				|| (0 == this.fromNode.compareTo(edge.toNode))
				|| (0 == this.toNode.compareTo(edge.fromNode))
				|| (0 == this.toNode.compareTo(edge.toNode));
	}

	/** この辺を両端のノードに登録する。remove の逆である。 */
	public void connect() {
		this.fromNode.addForwardEdge(this);
		this.toNode.addBackwardEdge(this);
	}

	public void remove() {
		final boolean f = this.fromNode.removeForwardEdge(this);
		final boolean b = this.toNode.removeBackwardEdge(this);
		assert f : "invalid status.";
		assert b : "invalid status.";
	}

	/**
	 * 依存の種類。
	 *
	 * <p>prelement パッケージにも同じ 3 値の DEPENDENCE_TYPE があり、
	 * データベースに書く文字列を持っていた。文字列は toString と同じもの
	 * だったので、こちらに寄せた。
	 */
	public enum TYPE {
		CONTROL("control"), DATA("data"), EXECUTION("execution");

		private final String text;

		TYPE(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return this.text;
		}
	}
}
