package yoshikihigo.tinypdg.pdg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import yoshikihigo.tinypdg.cfg.CFG;
import yoshikihigo.tinypdg.cfg.edge.CFGEdge;
import yoshikihigo.tinypdg.cfg.edge.CFGExceptionEdge;
import yoshikihigo.tinypdg.cfg.node.CFGControlNode;
import yoshikihigo.tinypdg.cfg.node.CFGNode;
import yoshikihigo.tinypdg.pe.ProgramElementInfo;

/**
 * 後支配 (post-dominance) に基づく制御依存の計算。
 *
 * <p>Ferrante, Ottenstein, Warren (1987) の定義である。CFG に仮想の入口
 * START と出口 EXIT を足し、START → 入口ノード、START → EXIT、各出口ノード →
 * EXIT の辺を引く。辺 x → s について、s が x を後支配しないとき、s から x の
 * 直接後支配者の手前までの後支配木の経路上のノードが、x にその辺の真偽で
 * 制御依存する。START に依存するノードは、PDG では入口ノードに依存する。
 *
 * <p>この定義では、ジャンプが作る依存も拾える。{@code if (c) return; x = 1;}
 * の {@code x = 1} は c に偽で依存する。文の入れ子で決める方法では入口に
 * 依存していた。
 *
 * <p>手当てが 3 つある。
 * <ul>
 * <li>例外辺は使わない。may の辺なので、使うと try 本体の全ての文が「catch
 * へ飛ぶかもしれない条件」になり、後続の文がそれぞれに依存してしまう。
 * <li>後支配は全てのノードが EXIT へ届くことを前提にする。後継のないノードは
 * EXIT へ繋ぎ、出口へ届かない領域 (break のない {@code for (;;)}) は、その
 * 領域の入口から抜けると見なして EXIT へ繋ぐ。
 * <li>制御依存の始点にできるのは条件ノードと START だけである。それ以外の
 * ノード (finally の出口のように後継が複数ある通常のノード) が始点になる
 * 依存は、そのノード自身の依存先を引き継がせる。
 * </ul>
 *
 * <p>入口から辿れないノード (catch 節の条件と、その本体の先頭、return の後の
 * 到達不能な文) には依存が付かない。呼ぶ側が文の入れ子で補う。
 */
final class PostDominanceControlDependence {

	/**
	 * 制御依存 1 本。
	 *
	 * @param from    条件ノード。null なら START (PDG の入口ノード)
	 * @param to      依存するノード
	 * @param control from の条件がこの値のときに to が実行される
	 */
	record Dependence(CFGNode<? extends ProgramElementInfo> from,
			CFGNode<? extends ProgramElementInfo> to, boolean control) {
	}

	/** 辺の終点と真偽。真偽は条件ノードから出た辺だけが持つ。 */
	private record Successor(int to, Boolean control) {
	}

	/** 依存元。from は条件ノードの添字か start。 */
	private record Source(int from, boolean control) {
	}

	private final List<CFGNode<? extends ProgramElementInfo>> nodes;
	private final int start;
	private final int exit;
	private final int size;
	private final List<List<Successor>> successors;

	/** cfg のノードの制御依存を全て計算する。 */
	static List<Dependence> compute(final CFG cfg) {

		Objects.requireNonNull(cfg, "\"cfg\" is null.");

		final PostDominanceControlDependence graph = new PostDominanceControlDependence(
				cfg);
		if (graph.nodes.isEmpty()) {
			return List.of();
		}

		final BitSet[] postDominators = graph.postDominators();
		final int[] immediate = graph.immediatePostDominators(postDominators);
		final Map<Integer, List<Source>> raw = graph.rawDependences(
				postDominators, immediate);

		final List<Dependence> dependences = new ArrayList<>();
		final Map<Integer, Set<Source>> resolved = new HashMap<>();
		for (int to = 0; to < graph.nodes.size(); to++) {
			for (final Source source : graph.resolve(to, raw, resolved,
					new HashSet<>())) {
				final CFGNode<? extends ProgramElementInfo> from = source
						.from() == graph.start ? null
								: graph.nodes.get(source.from());
				dependences.add(new Dependence(from, graph.nodes.get(to),
						source.control()));
			}
		}
		return dependences;
	}

	private PostDominanceControlDependence(final CFG cfg) {

		this.nodes = new ArrayList<>(cfg.getAllNodes());
		this.start = this.nodes.size();
		this.exit = this.nodes.size() + 1;
		this.size = this.nodes.size() + 2;
		this.successors = new ArrayList<>(this.size);
		for (int index = 0; index < this.size; index++) {
			this.successors.add(new ArrayList<>());
		}

		final Map<CFGNode<? extends ProgramElementInfo>, Integer> indices = new HashMap<>();
		for (int index = 0; index < this.nodes.size(); index++) {
			indices.put(this.nodes.get(index), index);
		}

		for (final CFGNode<? extends ProgramElementInfo> node : this.nodes) {
			final int from = indices.get(node);
			for (final CFGEdge edge : node.getForwardEdges()) {
				if (edge instanceof CFGExceptionEdge) {
					continue;
				}
				final Integer to = indices.get(edge.toNode);
				if (null != to) {
					this.addEdge(from, to, edge.getControl());
				}
			}
		}

		// START → 入口は、PDG の入口ノードからの辺と同じく真。START → EXIT は
		// 「何も実行されない経路」で、これがあるから最上位の文が START に依存する。
		final Integer enter = null == cfg.getEnterNode() ? null
				: indices.get(cfg.getEnterNode());
		if (null != enter) {
			this.addEdge(this.start, enter, Boolean.TRUE);
		}
		this.addEdge(this.start, this.exit, null);
		for (final CFGNode<? extends ProgramElementInfo> exitNode : cfg
				.getExitNodes()) {
			final Integer index = indices.get(exitNode);
			if (null != index) {
				this.addEdge(index, this.exit, null);
			}
		}

		this.ensureEveryNodeReachesExit();
	}

	private void addEdge(final int from, final int to, final Boolean control) {
		this.successors.get(from).add(new Successor(to, control));
	}

	/** 全てのノードが EXIT へ届くようにする。 */
	private void ensureEveryNodeReachesExit() {

		// 後継のないノードは、そこでメソッドが終わる。
		for (int vertex = 0; vertex < this.size; vertex++) {
			if (vertex != this.exit && this.successors.get(vertex).isEmpty()) {
				this.addEdge(vertex, this.exit, null);
			}
		}

		// 出口へ届かない領域は、その入口から抜けると見なす。領域は複数ありうる。
		while (true) {
			final BitSet reaching = this.reachingExit();
			if (reaching.cardinality() == this.size) {
				return;
			}
			final Set<Integer> entries = new LinkedHashSet<>();
			for (int vertex = 0; vertex < this.size; vertex++) {
				if (!reaching.get(vertex)) {
					continue;
				}
				for (final Successor successor : this.successors.get(vertex)) {
					if (!reaching.get(successor.to())) {
						entries.add(successor.to());
					}
				}
			}
			if (entries.isEmpty()) {
				// 入口からも辿れない無限ループ。最初のノードを入口と見なす。
				entries.add(reaching.nextClearBit(0));
			}
			for (final int entry : entries) {
				this.addEdge(entry, this.exit, null);
			}
		}
	}

	/** EXIT へ届くノードの集合。辺を逆向きに辿る。 */
	private BitSet reachingExit() {

		final List<List<Integer>> predecessors = new ArrayList<>(this.size);
		for (int vertex = 0; vertex < this.size; vertex++) {
			predecessors.add(new ArrayList<>());
		}
		for (int vertex = 0; vertex < this.size; vertex++) {
			for (final Successor successor : this.successors.get(vertex)) {
				predecessors.get(successor.to()).add(vertex);
			}
		}

		final BitSet reaching = new BitSet(this.size);
		final Deque<Integer> worklist = new ArrayDeque<>();
		worklist.push(this.exit);
		while (!worklist.isEmpty()) {
			final int vertex = worklist.pop();
			if (reaching.get(vertex)) {
				continue;
			}
			reaching.set(vertex);
			for (final int predecessor : predecessors.get(vertex)) {
				worklist.push(predecessor);
			}
		}
		return reaching;
	}

	/**
	 * 各ノードの後支配者の集合。自分自身を含む。
	 *
	 * <p>pdom(n) = {n} ∪ ⋂ pdom(s) (s は n の後継) の不動点を、全集合から
	 * 始めて求める。メソッド 1 つ分のノード数なら、この素朴な反復で足りる。
	 */
	private BitSet[] postDominators() {

		final BitSet[] postDominators = new BitSet[this.size];
		for (int vertex = 0; vertex < this.size; vertex++) {
			postDominators[vertex] = new BitSet(this.size);
			postDominators[vertex].set(0, this.size);
		}
		postDominators[this.exit].clear();
		postDominators[this.exit].set(this.exit);

		boolean changed = true;
		while (changed) {
			changed = false;
			for (int vertex = 0; vertex < this.size; vertex++) {
				if (vertex == this.exit) {
					continue;
				}
				final BitSet next = new BitSet(this.size);
				next.set(0, this.size);
				for (final Successor successor : this.successors.get(vertex)) {
					next.and(postDominators[successor.to()]);
				}
				next.set(vertex);
				if (!next.equals(postDominators[vertex])) {
					postDominators[vertex] = next;
					changed = true;
				}
			}
		}
		return postDominators;
	}

	/**
	 * 直接後支配者。EXIT には -1。
	 *
	 * <p>自分以外の後支配者のうち最も近いものは、後支配者の集合が最も大きい
	 * ものである。後支配者は EXIT へ向かう 1 本の鎖を成し、近いものほど
	 * 多くの後支配者を持つ。
	 */
	private int[] immediatePostDominators(final BitSet[] postDominators) {

		final int[] immediate = new int[this.size];
		for (int vertex = 0; vertex < this.size; vertex++) {
			int nearest = -1;
			final BitSet dominators = postDominators[vertex];
			for (int candidate = dominators.nextSetBit(0); 0 <= candidate; candidate = dominators
					.nextSetBit(candidate + 1)) {
				if (candidate == vertex) {
					continue;
				}
				if (nearest < 0 || postDominators[nearest]
						.cardinality() < postDominators[candidate].cardinality()) {
					nearest = candidate;
				}
			}
			immediate[vertex] = nearest;
		}
		return immediate;
	}

	/**
	 * 辺から直接読める依存。依存先のノードから、依存元と真偽の一覧へ。
	 *
	 * <p>辺 x → s について、s が x を後支配しなければ、s から x の直接後支配者
	 * の手前までのノードが x に依存する。x が条件ノードでなくても記録し、
	 * 後で解決する。
	 */
	private Map<Integer, List<Source>> rawDependences(
			final BitSet[] postDominators, final int[] immediate) {

		final Map<Integer, List<Source>> raw = new HashMap<>();
		for (int from = 0; from < this.size; from++) {
			if (from == this.exit) {
				continue;
			}
			for (final Successor successor : this.successors.get(from)) {
				if (postDominators[from].get(successor.to())) {
					continue;
				}
				final boolean control = null == successor.control()
						|| successor.control();
				for (int to = successor.to(); 0 <= to
						&& to != immediate[from]; to = immediate[to]) {
					raw.computeIfAbsent(to, key -> new ArrayList<>())
							.add(new Source(from, control));
				}
			}
		}
		return raw;
	}

	/**
	 * to の依存元を、条件ノードと START だけにする。
	 *
	 * <p>通常のノードが依存元になっているなら、そのノード自身の依存元を
	 * 引き継ぐ。to が実行されるのは、その通常のノードが実行されるときの
	 * 一部だからである。
	 */
	private Set<Source> resolve(final int to, final Map<Integer, List<Source>> raw,
			final Map<Integer, Set<Source>> resolved, final Set<Integer> visiting) {

		final Set<Source> known = resolved.get(to);
		if (null != known) {
			return known;
		}
		if (!visiting.add(to)) {
			// 通常のノード同士の循環。理屈の上では起きないが、起きても止まる。
			return Set.of();
		}

		final Set<Source> sources = new LinkedHashSet<>();
		for (final Source source : raw.getOrDefault(to, List.of())) {
			if (source.from() == this.start
					|| this.nodes.get(source.from()) instanceof CFGControlNode) {
				sources.add(source);
			} else {
				sources.addAll(this.resolve(source.from(), raw, resolved, visiting));
			}
		}

		visiting.remove(to);
		resolved.put(to, sources);
		return sources;
	}
}
