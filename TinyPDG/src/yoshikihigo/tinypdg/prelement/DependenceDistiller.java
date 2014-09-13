package yoshikihigo.tinypdg.prelement;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGControlDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGDataDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGExecutionDependenceEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.scorpio.NormalizedText;
import yoshikihigo.tinypdg.scorpio.PDGGenerationThread;

public class DependenceDistiller {

	public static void main(String[] args) {

		try {

			final Options options = new Options();

			{
				final Option d = new Option("d", "directory", true,
						"target directory");
				d.setArgName("directory");
				d.setArgs(1);
				d.setRequired(true);
				options.addOption(d);
			}

			{
				final Option s = new Option("s", "size", true, "size");
				s.setArgName("size");
				s.setArgs(1);
				s.setRequired(false);
				options.addOption(s);
			}

			{
				final Option t = new Option("t", "thread", true,
						"number of threads");
				t.setArgName("thread");
				t.setArgs(1);
				t.setRequired(false);
				options.addOption(t);
			}

			final CommandLineParser parser = new PosixParser();
			final CommandLine cmd = parser.parse(options, args);

			final File target = new File(cmd.getOptionValue("d"));
			if (!target.exists()) {
				System.err
						.println("specified directory or file does not exist.");
				System.exit(0);
			}

			final int SIZE_THRESHOLD = cmd.hasOption("s") ? Integer
					.parseInt(cmd.getOptionValue("s")) : 5;
			final int NUMBER_OF_THREADS = cmd.hasOption("t") ? Integer
					.parseInt(cmd.getOptionValue("t")) : 1;

			final long time1 = System.nanoTime();
			System.out.print("generating PDGs ... ");
			final PDG[] pdgArray;
			{
				final List<File> files = getFiles(target);
				final List<MethodInfo> methods = new ArrayList<MethodInfo>();
				for (final File file : files) {
					final CompilationUnit unit = TinyPDGASTVisitor
							.createAST(file);
					final TinyPDGASTVisitor visitor = new TinyPDGASTVisitor(
							file.getAbsolutePath(), unit, methods);
					unit.accept(visitor);
				}

				final SortedSet<PDG> pdgs = Collections
						.synchronizedSortedSet(new TreeSet<PDG>());
				final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
				final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
				final Thread[] pdgGenerationThreads = new Thread[NUMBER_OF_THREADS];
				for (int i = 0; i < pdgGenerationThreads.length; i++) {
					pdgGenerationThreads[i] = new Thread(
							new PDGGenerationThread(methods, pdgs,
									cfgNodeFactory, pdgNodeFactory, true, true,
									true, false, SIZE_THRESHOLD));
					pdgGenerationThreads[i].start();
				}
				for (final Thread thread : pdgGenerationThreads) {
					try {
						thread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				pdgArray = pdgs.toArray(new PDG[0]);
			}
			System.out.print("done: ");
			final long time2 = System.nanoTime();
			printTime(time2 - time1);

			System.out.print("distilling dependencies ...");
			final ConcurrentMap<Integer, AtomicInteger> fromNodeFrequencies = new ConcurrentHashMap<Integer, AtomicInteger>();
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeControlFrequencies = new ConcurrentHashMap<Integer, ConcurrentMap<Integer, AtomicInteger>>();
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeDataFrequencies = new ConcurrentHashMap<Integer, ConcurrentMap<Integer, AtomicInteger>>();
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeExecutionFrequencies = new ConcurrentHashMap<Integer, ConcurrentMap<Integer, AtomicInteger>>();
			for (final PDG pdg : pdgArray) {
				final SortedSet<PDGNode<?>> nodes = pdg.getAllNodes();
				for (final PDGNode<?> fromNode : nodes) {

					final int fromNodeHash = getHash(fromNode);
					AtomicInteger frequencies = fromNodeFrequencies
							.get(fromNodeHash);
					if (null == frequencies) {
						frequencies = new AtomicInteger(0);
						fromNodeFrequencies.put(fromNodeHash, frequencies);
					}
					frequencies.incrementAndGet();

					final SortedSet<PDGEdge> edges = fromNode.getForwardEdges();
					for (final PDGEdge edge : edges) {
						final int toNodeHash = getHash(edge.toNode);
						if (edge instanceof PDGControlDependenceEdge) {
							addToNodeHash(fromNodeHash, toNodeHash,
									toNodeDataFrequencies);
						} else if (edge instanceof PDGDataDependenceEdge) {
							addToNodeHash(fromNodeHash, toNodeHash,
									toNodeControlFrequencies);
						} else if (edge instanceof PDGExecutionDependenceEdge) {
							addToNodeHash(fromNodeHash, toNodeHash,
									toNodeExecutionFrequencies);
						}
					}
				}
			}
			System.out.print("done: ");
			final long time3 = System.nanoTime();
			printTime(time3 - time2);

			System.out.print("total elapsed time: ");
			printTime(time3 - time1);

		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(0);
		}
	}

	private static List<File> getFiles(final File file) {

		final List<File> files = new ArrayList<File>();

		if (file.isFile()) {
			if (file.getName().endsWith(".java")) {
				files.add(file);
			}
		}

		else if (file.isDirectory()) {
			for (final File child : file.listFiles()) {
				files.addAll(getFiles(child));
			}
		}

		else {
			assert false : "\"file\" is invalid.";
		}

		return files;
	}

	private static void addToNodeHash(
			final int fromNodeHash,
			final int toNodeHash,
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeFrequencies) {

		ConcurrentMap<Integer, AtomicInteger> toNodeHashes = toNodeFrequencies
				.get(fromNodeHash);
		if (null == toNodeHashes) {
			toNodeHashes = new ConcurrentHashMap<Integer, AtomicInteger>();
			toNodeFrequencies.put(fromNodeHash, toNodeHashes);
		}
		AtomicInteger frequency = toNodeHashes.get(toNodeHash);
		if (null == frequency) {
			frequency = new AtomicInteger(0);
			toNodeHashes.put(toNodeHash, frequency);
		}
		frequency.incrementAndGet();
	}

	private static int getHash(final PDGNode<?> node) {
		final NormalizedText fromNodeNormalizedText1 = new NormalizedText(
				node.core);
		final String fromNodeNormalizedText2 = NormalizedText
				.normalize(fromNodeNormalizedText1.getText());
		final int fromNodeHash = fromNodeNormalizedText2.hashCode();
		return fromNodeHash;
	}

	private static void printTime(final long time) {
		final long micro = time / 1000;
		final long mili = micro / 1000;
		final long sec = mili / 1000;

		final long hour = sec / 3600;
		final long minute = (sec % 3600) / 60;
		final long second = (sec % 3600) % 60;

		if (1l == hour) {
			System.out.print(hour);
			System.out.print(" hour ");
		} else if (1l < hour) {
			System.out.print(hour);
			System.out.print(" hours ");
		}

		if (1l == minute) {
			System.out.print(minute);
			System.out.print(" minute ");
		} else if (1l < minute) {
			System.out.print(minute);
			System.out.print(" minutes ");
		} else if ((0l == minute) && (1l <= hour)) {
			System.out.print(" 0 minute ");
		}

		if (2 <= second) {
			System.out.print(second);
			System.out.println(" seconds.");
		} else {
			System.out.print(second);
			System.out.println(" second.");
		}
	}
}
