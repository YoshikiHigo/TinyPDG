package yoshikihigo.tinypdg.pdg;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

import yoshikihigo.tinypdg.Parallel;
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

		Parallel.forEach(methods.size(), options.threads(),
				index -> build(methods.get(index), cfgNodeFactory,
						pdgNodeFactory, options, afterBuild, pdgs));

		return pdgs;
	}

	private static void build(final MethodInfo method,
			final CFGNodeFactory cfgNodeFactory,
			final PDGNodeFactory pdgNodeFactory, final Options options,
			final Consumer<PDG> afterBuild, final SortedSet<PDG> pdgs) {

		try {
			final PDG pdg = new PDG(method, pdgNodeFactory, cfgNodeFactory,
					options.dependences());
			pdg.build();

			if (pdg.getAllNodes().size() < options.minimumSize()) {
				return;
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
