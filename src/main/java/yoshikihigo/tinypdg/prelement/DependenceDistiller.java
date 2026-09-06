package yoshikihigo.tinypdg.prelement;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import yoshikihigo.tinypdg.CommandLineTools;
import yoshikihigo.tinypdg.ast.JavaAstFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.PDGGeneration;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.prelement.data.Frequency;
import yoshikihigo.tinypdg.prelement.db.DAO;
import yoshikihigo.tinypdg.scorpio.NormalizedText;

public class DependenceDistiller {

	public static void main(String[] args) {

		try {

			final Options options = new Options();

			options.addOption(CommandLineTools.databaseOption());

			options.addOption(CommandLineTools.targetOption());

			options.addOption(CommandLineTools.sizeOption(false));

			options.addOption(CommandLineTools.threadsOption());

			options.addOption(CommandLineTools.javaVersionOption());

			options.addOption(CommandLineTools.structuralOption());

			final CommandLineParser parser = new DefaultParser();
			final CommandLine cmd = parser.parse(options, args);

			final String database = cmd.getOptionValue("b");

			final File target = CommandLineTools.target(cmd);

			final int SIZE_THRESHOLD = CommandLineTools.size(cmd, 5);
			final int NUMBER_OF_THREADS = CommandLineTools.threads(cmd);

			final long time1 = System.nanoTime();
			System.out.print("generating PDGs ... ");
			final PDG[] pdgArray;
			{
				final List<MethodInfo> methods = JavaAstFactory
						.collectMethods(target, CommandLineTools.javaVersion(cmd));

				// ノードの併合はしない。
				final SortedSet<PDG> pdgs = PDGGeneration.buildInParallel(
						methods, new PDGGeneration.Options(
								CommandLineTools.withControlOption(cmd,
										PDG.Dependences.ALL),
								SIZE_THRESHOLD, NUMBER_OF_THREADS));
				pdgArray = pdgs.toArray(new PDG[0]);
			}
			final long time2 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time2 - time1));

			System.out.print("distilling dependencies ... ");
			// 要素の鍵は正規化テキストそのもの。以前は int のハッシュで、衝突した
			// 別のテキスト同士が 1 つの要素に合算されていた (issue #24)。
			final ConcurrentMap<String, AtomicInteger> fromNodeFrequencies = new ConcurrentHashMap<>();
			// 依存の種類ごとに、始点のテキストから終点のテキストごとの回数へ。
			// 以前は種類ごとに別の変数で、辺のクラスを instanceof で振り分けていた。
			final Map<PDGEdge.TYPE, ConcurrentMap<String, ConcurrentMap<String, AtomicInteger>>> toNodeFrequencies = new EnumMap<>(
					PDGEdge.TYPE.class);
			for (final PDGEdge.TYPE type : PDGEdge.TYPE.values()) {
				toNodeFrequencies.put(type, new ConcurrentHashMap<>());
			}
			for (final PDG pdg : pdgArray) {
				final SortedSet<PDGNode<?>> nodes = pdg.getAllNodes();
				for (final PDGNode<?> fromNode : nodes) {

					final String fromNodeText = NormalizedText
							.normalize(fromNode.core);
					fromNodeFrequencies
							.computeIfAbsent(fromNodeText, t -> new AtomicInteger(0))
							.incrementAndGet();

					// 同じ出現から同じ正規化テキストへ同じ種類の辺が複数あっても
					// 1 回と数える。辺の数で数えると、出現回数で割った「確率」が
					// 1 を超えていた (issue #28)。
					final Map<PDGEdge.TYPE, Set<String>> reached = new EnumMap<>(
							PDGEdge.TYPE.class);
					for (final PDGEdge edge : fromNode.getForwardEdges()) {
						reached.computeIfAbsent(edge.type, t -> new HashSet<>())
								.add(NormalizedText.normalize(edge.toNode.core));
					}
					for (final Entry<PDGEdge.TYPE, Set<String>> reachedOfType : reached
							.entrySet()) {
						for (final String toNodeText : reachedOfType.getValue()) {
							addToNodeText(fromNodeText, toNodeText,
									toNodeFrequencies.get(reachedOfType.getKey()));
						}
					}
				}
			}
			final long time3 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time3 - time2));

			System.out.print("sorting frequencies ... ");
			final Map<PDGEdge.TYPE, ConcurrentMap<String, List<Frequency>>> frequencies = new EnumMap<>(
					PDGEdge.TYPE.class);
			for (final PDGEdge.TYPE type : PDGEdge.TYPE.values()) {
				final ConcurrentMap<String, List<Frequency>> ofType = new ConcurrentHashMap<>();
				calculateFrequencies(fromNodeFrequencies,
						toNodeFrequencies.get(type), ofType);
				frequencies.put(type, ofType);
			}
			final long time4 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time4 - time3));

			System.out.print("registering to database ... ");
			try (final DAO dao = new DAO(database, true)) {
				for (final PDGEdge.TYPE type : PDGEdge.TYPE.values()) {
					registerFrequenciesToDatabase(dao, type,
							frequencies.get(type));
				}
			}
			final long time5 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time5 - time4));

			System.out.println("total elapsed time: "
					+ CommandLineTools.formatElapsed(time5 - time1));

		} catch (final Exception e) {
			// 異常終了なので終了コードは非 0 にする。0 のままでは、
			// シェルや CI から呼んだときに成功と区別が付かない。
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void addToNodeText(
			final String fromNodeText,
			final String toNodeText,
			final ConcurrentMap<String, ConcurrentMap<String, AtomicInteger>> toNodeFrequencies) {

		toNodeFrequencies
				.computeIfAbsent(fromNodeText, t -> new ConcurrentHashMap<>())
				.computeIfAbsent(toNodeText, t -> new AtomicInteger(0))
				.incrementAndGet();
	}

	private static void calculateFrequencies(
			final ConcurrentMap<String, AtomicInteger> fromNodeAllFrequencies,
			final ConcurrentMap<String, ConcurrentMap<String, AtomicInteger>> toNodeAllFrequencies,
			final ConcurrentMap<String, List<Frequency>> allFrequencies) {

		for (final Entry<String, ConcurrentMap<String, AtomicInteger>> entry : toNodeAllFrequencies
				.entrySet()) {
			final String fromNodeText = entry.getKey();
			final int totalTime = fromNodeAllFrequencies.get(fromNodeText).get();
			final List<Frequency> frequencies = new ArrayList<>();
			for (final Entry<String, AtomicInteger> entry2 : entry.getValue()
					.entrySet()) {
				final String toNodeText = entry2.getKey();
				final int time = entry2.getValue().get();
				frequencies.add(new Frequency((float) time / (float) totalTime,
						time, toNodeText));
			}
			// 確率の高いものから。
			frequencies.sort(Comparator
					.comparingDouble((Frequency f) -> f.probability).reversed());
			allFrequencies.put(fromNodeText, frequencies);
		}
	}

	private static void registerFrequenciesToDatabase(final DAO dao,
			final PDGEdge.TYPE type,
			final ConcurrentMap<String, List<Frequency>> allFrequencies) {

		for (final Entry<String, List<Frequency>> entry : allFrequencies
				.entrySet()) {
			final String fromText = entry.getKey();
			for (final Frequency frequency : entry.getValue()) {
				dao.addToFrequencies(type, fromText, frequency);
			}
		}
	}
}
