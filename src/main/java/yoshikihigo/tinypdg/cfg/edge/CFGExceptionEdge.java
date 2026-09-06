package yoshikihigo.tinypdg.cfg.edge;

import yoshikihigo.tinypdg.cfg.node.CFGNode;

/**
 * 例外による制御の移動を表す辺。try の入口と本体のノードから catch 節や
 * finally の入口へ引く。
 *
 * <p>「try 本体のどの文も例外を投げうる」という近似で引くので、may の辺
 * である。型を解決しないため、どの catch が受けるかは分からない。PDG は
 * この辺をデータ依存と到達可能性にだけ使い、実行依存と制御依存には使わない。
 */
public class CFGExceptionEdge extends CFGEdge {

	CFGExceptionEdge(final CFGNode<?> fromNode, final CFGNode<?> toNode) {
		super(fromNode, toNode);
	}

	@Override
	public String getDependenceTypeString() {
		return "exception";
	}

	@Override
	public String getDependenceString() {
		return "exception";
	}
}
