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

import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.PDGPairInfo;
import yoshikihigo.tinypdg.scorpio.io.CSVWriter;
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

			final List<File> files = getFiles(target);
			final List<PDG> pdgs = Collections
					.synchronizedList(new ArrayList<PDG>());
			final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
			final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
			final Thread[] pdgGenerationThreads = new Thread[NUMBER_OF_THREADS];
			for (int i = 0; i < pdgGenerationThreads.length; i++) {
				pdgGenerationThreads[i] = new Thread(new PDGGenerationThread(
						files, pdgs, cfgNodeFactory, pdgNodeFactory));
				pdgGenerationThreads[i].start();
			}
			for (final Thread thread : pdgGenerationThreads) {
				try {
					thread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges = Collections
					.synchronizedSortedMap(new TreeMap<PDG, SortedMap<PDGEdge, Integer>>());
			for (final PDG pdg : pdgs) {
				final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHash = new TreeMap<PDGEdge, Integer>();
				for (final PDGEdge edge : pdg.getAllEdges()) {

					final NormalizedText t1 = new NormalizedText(
							edge.fromNode.core);
					final String fromNodeText = NormalizedText.normalize(t1
							.getText());
					final NormalizedText t2 = new NormalizedText(
							edge.toNode.core);
					final String toNodeText = NormalizedText.normalize(t2
							.getText());
					final StringBuilder edgeText = new StringBuilder();
					edgeText.append(fromNodeText);
					edgeText.append("-");
					edgeText.append(edge.type.toString());
					edgeText.append("->");
					edgeText.append(toNodeText);
					final int hash = edgeText.toString().hashCode();

					mappingPDGEdgeToHash.put(edge, hash);
				}
				mappingPDGToPDGEdges.put(pdg, mappingPDGEdgeToHash);
			}

			final SortedMap<PDGEdge, String> mapPDGEdgeToFilePath = Collections
					.synchronizedSortedMap(new TreeMap<PDGEdge, String>());
			for (final PDG pdg : pdgs) {
				final String filepath = pdg.unit.path;
				for (final PDGEdge edge : pdg.getAllEdges()) {
					mapPDGEdgeToFilePath.put(edge, filepath);
				}
			}

			final List<PDGPairInfo> pdgpairs = new ArrayList<PDGPairInfo>();
			for (int i = 0; i < pdgs.size(); i++) {
				for (int j = i + 1; j < pdgs.size(); j++) {
					pdgpairs.add(new PDGPairInfo(pdgs.get(i), pdgs.get(j)));
				}
			}

			final SortedSet<ClonePairInfo> clonepairs = Collections
					.synchronizedSortedSet(new TreeSet<ClonePairInfo>());
			final PDGPairInfo[] pdgpairArray = pdgpairs
					.toArray(new PDGPairInfo[0]);
			final PDG[] pdgArray = pdgs.toArray(new PDG[0]);
			final Thread[] slicingThreads = new Thread[NUMBER_OF_THREADS];
			for (int i = 0; i < slicingThreads.length; i++) {
				slicingThreads[i] = new Thread(new SlicingThread(pdgpairArray,
						pdgArray, mappingPDGToPDGEdges, mapPDGEdgeToFilePath,
						clonepairs, SIZE_THRESHOLD));
				slicingThreads[i].start();
			}

			for (final Thread thread : slicingThreads) {
				try {
					thread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			final Writer writer = new CSVWriter(output, clonepairs);
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