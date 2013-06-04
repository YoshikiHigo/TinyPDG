package yoshikihigo.tinypdg.scorpio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
import yoshikihigo.tinypdg.scorpio.data.EdgePairInfo;
import yoshikihigo.tinypdg.scorpio.io.CSVWriter;
import yoshikihigo.tinypdg.scorpio.io.Writer;

public class Scorpio2 {

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
			final List<PDG> pdgs = new ArrayList<PDG>();
			final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
			final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
			for (final File file : files) {
				final CompilationUnit unit = TinyPDGASTVisitor.createAST(file);
				final List<MethodInfo> methods = new ArrayList<MethodInfo>();
				final TinyPDGASTVisitor visitor = new TinyPDGASTVisitor(
						file.getAbsolutePath(), unit, methods);
				unit.accept(visitor);
				for (final MethodInfo method : methods) {
					final PDG pdg = new PDG(method, pdgNodeFactory,
							cfgNodeFactory, true, true, true, 100, 100, 100);
					pdg.build();
					pdgs.add(pdg);
				}
			}

			final ConcurrentMap<PDGEdge, String> mapPDGEdgeToFilePath = new ConcurrentHashMap<PDGEdge, String>();
			for (final PDG pdg : pdgs) {
				final String filepath = pdg.unit.path;
				for (final PDGEdge edge : pdg.getAllEdges()) {
					mapPDGEdgeToFilePath.put(edge, filepath);
				}
			}

			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges = new TreeMap<PDG, SortedMap<PDGEdge, Integer>>();
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

			final SortedSet<ClonePairInfo> clonepairs = new TreeSet<ClonePairInfo>();
			for (int i = 0; i < pdgs.size(); i++) {

				final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHashI = mappingPDGToPDGEdges
						.get(pdgs.get(i));

				for (int j = i + 1; j < pdgs.size(); j++) {

					final SortedMap<PDGEdge, Integer> mappingPDGEdgeToHashJ = mappingPDGToPDGEdges
							.get(pdgs.get(j));

					final SortedMap<Integer, List<PDGEdge>> mappingHashToPDGEdges = new TreeMap<Integer, List<PDGEdge>>();
					for (final Entry<PDGEdge, Integer> entry : mappingPDGEdgeToHashI
							.entrySet()) {
						final Integer hash = entry.getValue();
						final PDGEdge edge = entry.getKey();
						List<PDGEdge> edges = mappingHashToPDGEdges.get(hash);
						if (null == edges) {
							edges = new ArrayList<PDGEdge>();
							mappingHashToPDGEdges.put(hash, edges);
						}
						edges.add(edge);
					}

					for (final Entry<PDGEdge, Integer> entry : mappingPDGEdgeToHashJ
							.entrySet()) {
						final Integer hash = entry.getValue();
						final PDGEdge edge = entry.getKey();
						List<PDGEdge> edges = mappingHashToPDGEdges.get(hash);
						if (null == edges) {
							edges = new ArrayList<PDGEdge>();
							mappingHashToPDGEdges.put(hash, edges);
						}
						edges.add(edge);
					}

					final ConcurrentMap<PDGEdge, List<PDGEdge>> mappingPDGEdgeToPDGEdges = new ConcurrentHashMap<PDGEdge, List<PDGEdge>>();
					for (final List<PDGEdge> list : mappingHashToPDGEdges
							.values()) {
						if (1 < list.size()) {
							for (final PDGEdge edge : list) {
								mappingPDGEdgeToPDGEdges.put(edge, list);
							}
						}
					}

					final SortedSet<EdgePairInfo> edgepairsInClonepairs = new TreeSet<EdgePairInfo>();

					for (final List<PDGEdge> edges : mappingHashToPDGEdges
							.values()) {
						for (int x = 0; x < edges.size(); x++) {
							for (int y = x + 1; y < edges.size(); y++) {

								final PDGEdge edgeA = edges.get(x);
								final PDGEdge edgeB = edges.get(y);

								final EdgePairInfo edgepair = new EdgePairInfo(
										edgeA, edgeB);
								if (edgepairsInClonepairs.contains(edgepair)) {
									continue;
								}

								if (edgeA.connectedWith(edgeB)) {
									continue;
								}

								final String path1 = mapPDGEdgeToFilePath
										.get(edgeA);
								final String path2 = mapPDGEdgeToFilePath
										.get(edgeB);
								final Slicing slicing = new Slicing(path1,
										path2, edgeA, edgeB,
										mappingPDGEdgeToPDGEdges);
								final ClonePairInfo clonepair = slicing
										.perform();
								edgepairsInClonepairs.addAll(clonepair
										.getEdgePairs());
								if (SIZE_THRESHOLD <= clonepair.size()) {
									clonepairs.add(clonepair);
								}
							}
						}
					}
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