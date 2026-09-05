package yoshikihigo.tinypdg.prelement;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
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
import yoshikihigo.tinypdg.pdg.edge.PDGControlDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGDataDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGExecutionDependenceEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;
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

			final CommandLineParser parser = new DefaultParser();
			final CommandLine cmd = parser.parse(options, args);

			final String database = cmd.getOptionValue("b");

			final File target = CommandLineTools.target(cmd);

			final int SIZE_THRESHOLD = cmd.hasOption("s") ? Integer
					.parseInt(cmd.getOptionValue("s")) : 5;
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
								PDG.Dependences.ALL, SIZE_THRESHOLD,
								NUMBER_OF_THREADS));
				pdgArray = pdgs.toArray(new PDG[0]);
			}
			final long time2 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time2 - time1));

			System.out.print("distilling dependencies ... ");
			final ConcurrentMap<Integer, String> texts = new ConcurrentHashMap<>();
			final ConcurrentMap<Integer, AtomicInteger> fromNodeFrequencies = new ConcurrentHashMap<>();
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeControlFrequencies = new ConcurrentHashMap<>();
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeDataFrequencies = new ConcurrentHashMap<>();
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeExecutionFrequencies = new ConcurrentHashMap<>();
			for (final PDG pdg : pdgArray) {
				final SortedSet<PDGNode<?>> nodes = pdg.getAllNodes();
				for (final PDGNode<?> fromNode : nodes) {

					// generate a hash value from fromNode
					final String fromNodeNormalizedText = NormalizedText
							.normalize(fromNode.core);
					final int fromNodeHash = fromNodeNormalizedText.hashCode();

					// make mapping between hash value and normalized text
					if (!texts.containsKey(fromNodeHash)) {
						texts.put(fromNodeHash, fromNodeNormalizedText);
					}

					AtomicInteger frequencies = fromNodeFrequencies
							.get(fromNodeHash);
					if (null == frequencies) {
						frequencies = new AtomicInteger(0);
						fromNodeFrequencies.put(fromNodeHash, frequencies);
					}
					frequencies.incrementAndGet();

					final SortedSet<PDGEdge> edges = fromNode.getForwardEdges();
					for (final PDGEdge edge : edges) {
						final String toNodeNormalizedText = NormalizedText
								.normalize(edge.toNode.core);
						final int toNodeHash = toNodeNormalizedText.hashCode();
						if (edge instanceof PDGControlDependenceEdge) {
							addToNodeHash(fromNodeHash, toNodeHash,
									toNodeControlFrequencies);
						} else if (edge instanceof PDGDataDependenceEdge) {
							addToNodeHash(fromNodeHash, toNodeHash,
									toNodeDataFrequencies);
						} else if (edge instanceof PDGExecutionDependenceEdge) {
							addToNodeHash(fromNodeHash, toNodeHash,
									toNodeExecutionFrequencies);
						}
					}
				}
			}
			final long time3 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time3 - time2));

			System.out.print("sorting frequencies ... ");
			final ConcurrentMap<Integer, List<Frequency>> frequenciesForControlDependence = new ConcurrentHashMap<>();
			final ConcurrentMap<Integer, List<Frequency>> frequenciesForDataDependence = new ConcurrentHashMap<>();
			final ConcurrentMap<Integer, List<Frequency>> frequenciesForExecutionDependence = new ConcurrentHashMap<>();
			calculateFrequencies(fromNodeFrequencies, toNodeControlFrequencies,
					texts, frequenciesForControlDependence);
			calculateFrequencies(fromNodeFrequencies, toNodeDataFrequencies,
					texts, frequenciesForDataDependence);
			calculateFrequencies(fromNodeFrequencies,
					toNodeExecutionFrequencies, texts,
					frequenciesForExecutionDependence);
			final long time4 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time4 - time3));

			System.out.print("registering to database ... ");
			try (final DAO dao = new DAO(database, true)) {
				registerTextsToDatabase(dao, texts);
				registerFrequenciesToDatabase(dao, DEPENDENCE_TYPE.CONTROL,
						frequenciesForControlDependence);
				registerFrequenciesToDatabase(dao, DEPENDENCE_TYPE.DATA,
						frequenciesForDataDependence);
				registerFrequenciesToDatabase(dao, DEPENDENCE_TYPE.EXECUTION,
						frequenciesForExecutionDependence);
			}
			final long time5 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time5 - time4));

			System.out.println("total elapsed time: "
					+ CommandLineTools.formatElapsed(time5 - time1));

			// printFrequencies("control", texts, frequenciesForControlDependence);
			// printFrequencies("data", texts, frequenciesForDataDependence);
			// printFrequencies("execution", texts,
			// frequenciesForExecutionDependence);

		} catch (final Exception e) {
			// 異常終了なので終了コードは非 0 にする。0 のままでは、
			// シェルや CI から呼んだときに成功と区別が付かない。
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void addToNodeHash(
			final int fromNodeHash,
			final int toNodeHash,
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeFrequencies) {

		ConcurrentMap<Integer, AtomicInteger> toNodeHashes = toNodeFrequencies
				.get(fromNodeHash);
		if (null == toNodeHashes) {
			toNodeHashes = new ConcurrentHashMap<>();
			toNodeFrequencies.put(fromNodeHash, toNodeHashes);
		}
		AtomicInteger frequency = toNodeHashes.get(toNodeHash);
		if (null == frequency) {
			frequency = new AtomicInteger(0);
			toNodeHashes.put(toNodeHash, frequency);
		}
		frequency.incrementAndGet();
	}

	private static void calculateFrequencies(
			final ConcurrentMap<Integer, AtomicInteger> fromNodeAllFrequencies,
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeAllFrequencies,
			final ConcurrentMap<Integer, String> texts,
			final ConcurrentMap<Integer, List<Frequency>> allFrequencies) {

		for (final Entry<Integer, ConcurrentMap<Integer, AtomicInteger>> entry : toNodeAllFrequencies
				.entrySet()) {
			final int fromNodeHash = entry.getKey();
			final int totalTime = fromNodeAllFrequencies.get(fromNodeHash)
					.get();
			final List<Frequency> frequencies = new ArrayList<>();
			final ConcurrentMap<Integer, AtomicInteger> toNodeFrequencies = entry
					.getValue();
			for (final Entry<Integer, AtomicInteger> entry2 : toNodeFrequencies
					.entrySet()) {
				final int toNodeHash = entry2.getKey();
				final int time = entry2.getValue().get();
				final String normalizedText = texts.get(toNodeHash);
				final Frequency frequency = new Frequency((float) time
						/ (float) totalTime, time, toNodeHash, normalizedText);
				frequencies.add(frequency);
			}
			// 確率の高いものから。
			frequencies.sort(Comparator
					.comparingDouble((Frequency f) -> f.probablity).reversed());
			allFrequencies.put(fromNodeHash, frequencies);
		}
	}

	private static void registerTextsToDatabase(final DAO dao,
			final ConcurrentMap<Integer, String> texts) {

		for (final Entry<Integer, String> entry : texts.entrySet()) {
			final int hash = entry.getKey();
			final String text = entry.getValue();
			dao.addToTexts(hash, text);
		}
	}

	private static void registerFrequenciesToDatabase(final DAO dao,
			DEPENDENCE_TYPE type,
			final ConcurrentMap<Integer, List<Frequency>> allFrequencies) {

		for (final Entry<Integer, List<Frequency>> entry : allFrequencies
				.entrySet()) {

			final int fromhash = entry.getKey();
			for (final Frequency frequency : entry.getValue()) {
				dao.addToFrequencies(type, fromhash, frequency);
			}
		}
	}

	private static void printFrequencies(final String type,
			final ConcurrentMap<Integer, String> texts,
			final ConcurrentMap<Integer, List<Frequency>> allFrequencies) {

		for (final Entry<Integer, List<Frequency>> entry : allFrequencies
				.entrySet()) {
			final int fromNodeHash = entry.getKey();
			final String fromNodeText = texts.get(fromNodeHash);
			for (final Frequency frequency : entry.getValue()) {
				System.out.print(type);
				System.out.print(" ");
				System.out.print(Integer.toString(frequency.support));
				System.out.print(" ");
				System.out.print(Float.toString(frequency.probablity));
				System.out.print(" ");
				System.out.print(fromNodeText);
				System.out.print(" -> ");
				System.out.println(frequency.text);
			}
			System.out.println("-------------------------------------------");
		}
	}

}
