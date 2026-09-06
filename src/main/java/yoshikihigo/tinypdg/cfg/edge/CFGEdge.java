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

	/**
	 * break や continue のノードを消したときに、その前後を繋ぐ辺。
	 *
	 * @param control 消したノードへ入っていた辺の真偽。条件ノードから来て
	 *                いなければ null
	 */
	static public CFGEdge makeJumpEdge(final CFGNode<?> fromNode,
			final CFGNode<?> toNode, final Boolean control) {

		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		Objects.requireNonNull(toNode, "\"toNode\" is null.");

		return new CFGJumpEdge(fromNode, toNode, control);
	}

	/** 例外による移動の辺。try のノードから catch 節や finally の入口へ。 */
	static public CFGEdge makeExceptionEdge(final CFGNode<?> fromNode,
			final CFGNode<?> toNode) {

		Objects.requireNonNull(fromNode, "\"fromNode\" is null.");
		Objects.requireNonNull(toNode, "\"toNode\" is null.");

		return new CFGExceptionEdge(fromNode, toNode);
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
	 * 始点が条件ノードなら、この辺へ進む条件の真偽。そうでなければ null。
	 *
	 * <p>条件つきの辺のほか、break や continue のノードを消したときに条件
	 * ノードから張り直した jump の辺も持つ。後支配から制御依存を計算するには、
	 * 条件ノードから出る全ての辺の真偽が要る。
	 */
	public Boolean getControl() {
		return null;
	}

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
				this.getDependenceTypeString(), this.getDependenceString());
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

		final int typeOrder = this.getDependenceTypeString().compareTo(
				edge.getDependenceTypeString());
		if (0 != typeOrder) {
			return typeOrder;
		}

		// 条件つきの辺は真偽も同一性に入る。if (c) {} のように、条件から同じ
		// ノードへ真と偽の両方で進むことがある。以前は種類までしか見ておらず、
		// 後から入れた方が集合から落ちていた。
		return this.getDependenceString().compareTo(edge.getDependenceString());
	}
}
