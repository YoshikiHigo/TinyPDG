package yoshikihigo.tinypdg.scorpio;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.PDGPairInfo;
import yoshikihigo.tinypdg.scorpio.io.CSVEdgeWriter;
import yoshikihigo.tinypdg.scorpio.io.Writer;

public class Scorpio {

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
				final Option o = new Option("o", "output", true, "output file");
				o.setArgName("file");
				o.setArgs(1);
				o.setRequired(true);
				options.addOption(o);
			}

			{
				final Option s = new Option("s", "size", true, "size");
				s.setArgName("size");
				s.setArgs(1);
				s.setRequired(true);
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

			final String output = cmd.getOptionValue("o");
			final int SIZE_THRESHOLD = Integer
					.parseInt(cmd.getOptionValue("s"));
			final int NUMBER_OF_THREADS = cmd.hasOption("t") ? Integer
					.parseInt(cmd.getOptionValue("t")) : 1;

			final long startTime = System.nanoTime();

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
									cfgNodeFactory, pdgNodeFactory));
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
			System.out.println("done.");

			System.out.print("calculating hash values for edges ... ");
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges = Collections
					.synchronizedSortedMap(new TreeMap<PDG, SortedMap<PDGEdge, Integer>>());
			{
				final Thread[] hashCalculationThreads = new Thread[NUMBER_OF_THREADS];
				for (int i = 0; i < hashCalculationThreads.length; i++) {
					hashCalculationThreads[i] = new Thread(
							new HashCalculationThread(pdgArray,
									mappingPDGToPDGEdges));
					hashCalculationThreads[i].start();
				}
				for (final Thread thread : hashCalculationThreads) {
					try {
						thread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			System.out.println("done.");

			System.out.print("deteting clone pairs by pairwised slicing ... ");
			final SortedMap<PDGEdge, String> mapPDGEdgeToFilePath = Collections
					.synchronizedSortedMap(new TreeMap<PDGEdge, String>());
			{
				for (final PDG pdg : pdgArray) {
					final String filepath = pdg.unit.path;
					for (final PDGEdge edge : pdg.getAllEdges()) {
						mapPDGEdgeToFilePath.put(edge, filepath);
					}
				}
			}

			final SortedSet<ClonePairInfo> clonepairs = Collections
					.synchronizedSortedSet(new TreeSet<ClonePairInfo>());
			{
				final List<PDGPairInfo> pdgpairs = new ArrayList<PDGPairInfo>();
				for (int i = 0; i < pdgArray.length; i++) {
					for (int j = i + 1; j < pdgArray.length; j++) {
						pdgpairs.add(new PDGPairInfo(pdgArray[i], pdgArray[j]));
					}
				}
				final PDGPairInfo[] pdgpairArray = pdgpairs
						.toArray(new PDGPairInfo[0]);
				final Thread[] slicingThreads = new Thread[NUMBER_OF_THREADS];
				for (int i = 0; i < slicingThreads.length; i++) {
					slicingThreads[i] = new Thread(new SlicingThread(
							pdgpairArray, pdgArray, mappingPDGToPDGEdges,
							mapPDGEdgeToFilePath, clonepairs, SIZE_THRESHOLD));
					slicingThreads[i].start();
				}
				for (final Thread thread : slicingThreads) {
					try {
						thread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			System.out.println("done.");

			final Writer writer = new CSVEdgeWriter(output, clonepairs);
			writer.write();

			printNumberOfComparison(Slicing.getNumberOfComparison());

			final long endTime = System.nanoTime();
			printTime(endTime - startTime);

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

	private static void printNumberOfRemoval(final long number) {
		System.out.print("number of removed edges: ");
		System.out.println(String.format("%1$,3d", number));
	}

	private static void printNumberOfComparison(final long number) {
		System.out.print("number of comparisons: ");
		System.out.println(String.format("%1$,3d", number));
	}

	private static void printTime(final long time) {
		final long micro = time / 1000;
		final long mili = micro / 1000;
		final long sec = mili / 1000;

		final long hour = sec / 3600;
		final long minute = (sec % 3600) / 60;
		final long second = (sec % 3600) % 60;

		System.out.print("elapsed time: ");

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