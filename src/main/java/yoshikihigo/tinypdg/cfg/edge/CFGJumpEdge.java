package yoshikihigo.tinypdg.cfg.edge;

import yoshikihigo.tinypdg.cfg.node.CFGNode;

/**
 * break や continue を消したときに、その前後を繋ぐ辺。
 *
 * <p>{@code jump} の印で、この辺が飛び越えから来たことが分かる。消した
 * ノードへ条件ノードから入っていたなら、その真偽も持つ。{@code if (c) break;}
 * は {@code c -> 行き先 [jump:true]} になる。以前は真偽を捨てていて、条件の
 * どちらの枝から飛んだのか分からなくなっていた。
 */
public class CFGJumpEdge extends CFGEdge {

	/** 条件ノードから来た飛び越えなら、その真偽。そうでなければ null。 */
	final public Boolean control;

	CFGJumpEdge(final CFGNode<?> fromNode, final CFGNode<?> toNode) {
		this(fromNode, toNode, null);
	}

	CFGJumpEdge(final CFGNode<?> fromNode, final CFGNode<?> toNode,
			final Boolean control) {
		super(fromNode, toNode);
		this.control = control;
	}

	@Override
	public Boolean getControl() {
		return this.control;
	}

	@Override
	public String getDependenceTypeString() {
		return "jump";
	}

	@Override
	public String getDependenceString() {
		return null == this.control ? "jump" : "jump:" + this.control;
	}
}
