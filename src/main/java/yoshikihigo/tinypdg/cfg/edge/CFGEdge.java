package yoshikihigo.tinypdg.cfg.edge;

import java.util.Objects;
import yoshikihigo.tinypdg.TinyPDGException;
import yoshikihigo.tinypdg.cfg.node.CFGBreakStatementNode;
import yoshikihigo.tinypdg.cfg.node.CFGContinueStatementNode;
import yoshikihigo.tinypdg.cfg.node.CFGControlNode;
import yoshikihigo.tinypdg.cfg.node.CFGNode;
import yoshikihigo.tinypdg.cfg.node.CFGPseudoNode;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;

public abstract class CFGEdge implements Comparable<CFGEdge> {

	static public CFGEdge makeEdge(final CFGNode<?> fromNode,
			final CFGNode<?> toNode, boolean control) {

		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		Objects.requireNonNull(toNode, "\"toNode\" is null.");

		if (fromNode instanceof CFGControlNode) {
			return new CFGControlEdge(fromNode, toNode, control);
		}

		if (fromNode instanceof CFGPseudoNode) {
			return new CFGNormalEdge(fromNode, toNode);
		}

		// 以前は表明の後で null を返していた。表明は既定で無効なので、実際には
		// null が返り、離れた場所で NullPointerException になっていた。
		throw new TinyPDGException("条件つきの辺の始点になれないノードです: "
				+ fromNode.getClass().getName());
	}

	static public CFGEdge makeEdge(final CFGNode<?> fromNode,
			final CFGNode<?> toNode) {

		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		Objects.requireNonNull(toNode, "\"toNode\" is null.");

		if (fromNode instanceof CFGControlNode) {
			return makeEdge(fromNode, toNode, false);
		} else if (fromNode instanceof CFGBreakStatementNode
				|| fromNode instanceof CFGContinueStatementNode) {
			return new CFGJumpEdge(fromNode, toNode);
		} else {
			return new CFGNormalEdge(fromNode, toNode);
		}
	}

	static public CFGEdge makeJumpEdge(final CFGNode<?> fromNode,
			final CFGNode<?> toNode) {

		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		Objects.requireNonNull(toNode, "\"toNode\" is null.");

		return new CFGJumpEdge(fromNode, toNode);
	}

	public final CFGNode<? extends ProgramElementInfo> fromNode;
	public final CFGNode<? extends ProgramElementInfo> toNode;

	CFGEdge(final CFGNode<?> fromNode, final CFGNode<?> toNode) {

		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		Objects.requireNonNull(toNode, "\"toNode\" is null.");

		this.fromNode = fromNode;
		this.toNode = toNode;
	}

	/**
	 * この辺を両端のノードに登録する。
	 *
	 * <p>辺は作っただけでは繋がらない。始点の順方向、終点の逆方向の両方に
	 * 入れて初めてグラフの一部になる。CFG はこれを 20 か所近くで書いていた。
	 */
	public void connect() {
		this.fromNode.addForwardEdge(this);
		this.toNode.addBackwardEdge(this);
	}

	public abstract String getDependenceTypeString();

	public abstract String getDependenceString();

	/**
	 * 同一性は compareTo と同じく、両端のノードと辺の種類で決まる。
	 *
	 * <p>以前は種類をクラスで比べ、両端をノードの equals で比べていた。結果は
	 * 同じだったが、null を渡すと NullPointerException になり、hashCode は
	 * ノードのアドレスから作られていた。
	 */
	@Override
	public boolean equals(final Object o) {
		return o instanceof CFGEdge edge && 0 == this.compareTo(edge);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.fromNode.core.id, this.toNode.core.id,
				this.getDependenceTypeString());
	}

	@Override
	public int compareTo(final CFGEdge edge) {

		if (null == edge) {
			throw new IllegalArgumentException();
		}

		final int fromOrder = this.fromNode.compareTo(edge.fromNode);
		if (0 != fromOrder) {
			return fromOrder;
		}

		final int toOrder = this.toNode.compareTo(edge.toNode);
		if (0 != toOrder) {
			return toOrder;
		}

		return this.getDependenceTypeString().compareTo(
				edge.getDependenceTypeString());
	}
}
