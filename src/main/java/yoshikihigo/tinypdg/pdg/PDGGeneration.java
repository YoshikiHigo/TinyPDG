package yoshikihigo.tinypdg.pdg;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;

/**
 * メソッドの並びから PDG をまとめて作る。
 *
 * <p>Scorpio と DependenceDistiller が同じ 30 行を書いていた。違いは
 * PDG に含める依存の種類だけである。
 */
public final class PDGGeneration {

	private PDGGeneration() {
	}

	/**
	 * 何をどう作るか。
	 *
	 * @param dependences 各 PDG に何を含めるか
	 * @param minimumSize これ未満のノード数のグラフは捨てる
	 * @param threads     並列度
	 */
	public record Options(PDG.Dependences dependences, int minimumSize,
			int threads) {

		public Options {
			Objects.requireNonNull(dependences, "\"dependences\" is null.");
			if (threads < 1) {
				throw new IllegalArgumentException(
						"threads は 1 以上でなければならない: " + threads);
			}
		}
	}

	public static SortedSet<PDG> buildInParallel(final List<MethodInfo> methods,
			final Options options) {
		return buildInParallel(methods, options, pdg -> {
		});
	}

	/**
	 * @param afterBuild 出来上がった各 PDG に対して、集合へ入れる前に行う処理。
	 *                   Scorpio がノードの併合に使う。併合は Scorpio 固有の
	 *                   話なので、ここでは何をするか知らないままにしてある
	 */
	public static SortedSet<PDG> buildInParallel(final List<MethodInfo> methods,
			final Options options, final Consumer<PDG> afterBuild) {

		Objects.requireNonNull(methods, "\"methods\" is null.");
		Objects.requireNonNull(options, "\"options\" is null.");
		Objects.requireNonNull(afterBuild, "\"afterBuild\" is null.");

		final SortedSet<PDG> pdgs = Collections
				.synchronizedSortedSet(new TreeSet<>());
		final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
		final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();

		// 取り出し位置は 1 回の生成ごとに用意する。以前はこれが static で、
		// 全ての生成で共有され、しかも巻き戻らなかった。同じ JVM で 2 回目を
		// 走らせると、1 回目が数え終わった位置から始まるので 1 つも作られず、
		// それでも正常終了しているように見えていた。
		final AtomicInteger next = new AtomicInteger(0);

		// close() が全タスクの終了を待つ。以前は Thread を並べて join し、
		// InterruptedException はスタックトレースを出すだけで割り込み状態を
		// 戻していなかった。
		try (final ExecutorService pool = Executors
				.newFixedThreadPool(options.threads())) {
			for (int i = 0; i < options.threads(); i++) {
				pool.execute(() -> buildFrom(methods, next, pdgs,
						cfgNodeFactory, pdgNodeFactory, options, afterBuild));
			}
		}

		return pdgs;
	}

	private static void buildFrom(final List<MethodInfo> methods,
			final AtomicInteger next, final SortedSet<PDG> pdgs,
			final CFGNodeFactory cfgNodeFactory,
			final PDGNodeFactory pdgNodeFactory, final Options options,
			final Consumer<PDG> afterBuild) {

		for (int index = next.getAndIncrement(); index < methods.size(); index = next
				.getAndIncrement()) {

			final MethodInfo method = methods.get(index);

			try {
				final PDG pdg = new PDG(method, pdgNodeFactory, cfgNodeFactory,
						options.dependences());
				pdg.build();

				if (pdg.getAllNodes().size() < options.minimumSize()) {
					continue;
				}

				afterBuild.accept(pdg);
				pdgs.add(pdg);

			} catch (final Exception e) {
				e.printStackTrace();
				System.err.println("ERROR: failed to process the method "
						+ method.name + " in " + method.path);
			}
		}
	}
}
