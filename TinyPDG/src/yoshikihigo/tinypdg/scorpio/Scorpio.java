package yoshikihigo.tinypdg.scorpio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
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

			final ConcurrentMap<Integer, List<PDGEdge>> mapHashToPDGEdgelists = new ConcurrentHashMap<Integer, List<PDGEdge>>();
			for (final PDG pdg : pdgs) {
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

					System.out.println(edge.fromNode.core.getText());
					System.out.println(fromNodeText);
					System.out.println();
					System.out.println(edge.toNode.core.getText());
					System.out.println(toNodeText);
					System.out.println();

					List<PDGEdge> edgeList = mapHashToPDGEdgelists.get(hash);
					if (null == edgeList) {
						edgeList = new ArrayList<PDGEdge>();
						mapHashToPDGEdgelists.put(hash, edgeList);
					}
					edgeList.add(edge);
				}
			}

			final ConcurrentMap<PDGEdge, String> mapPDGEdgeToFilePath = new ConcurrentHashMap<PDGEdge, String>();
			for (final PDG pdg : pdgs) {
				final String filepath = pdg.unit.path;
				for (final PDGEdge edge : pdg.getAllEdges()) {
					mapPDGEdgeToFilePath.put(edge, filepath);
				}
			}

			final ConcurrentMap<PDGEdge, List<PDGEdge>> mapPDGEdgeToPDGEdgelists = new ConcurrentHashMap<PDGEdge, List<PDGEdge>>();
			for (final List<PDGEdge> list : mapHashToPDGEdgelists.values()) {
				if (1 < list.size()) {
					for (final PDGEdge edge : list) {
						mapPDGEdgeToPDGEdgelists.put(edge, list);
					}
				}
			}

			final SortedSet<ClonePairInfo> clonepairs = new TreeSet<ClonePairInfo>();
			final SortedSet<EdgePairInfo> edgepairsInClonepairs = new TreeSet<EdgePairInfo>();
			for (final List<PDGEdge> list : mapPDGEdgeToPDGEdgelists.values()) {
				for (int i = 0; i < list.size(); i++) {
					for (int j = i + 1; j < list.size(); j++) {

						final PDGEdge edgeA = list.get(i);
						final PDGEdge edgeB = list.get(j);

						final EdgePairInfo edgepair = new EdgePairInfo(edgeA,
								edgeB);
						if (edgepairsInClonepairs.contains(edgepair)) {
							continue;
						}

						if (edgeA.connectedWith(edgeB)) {
							continue;
						}

						final String path1 = mapPDGEdgeToFilePath.get(edgeA);
						final String path2 = mapPDGEdgeToFilePath.get(edgeB);
						final Slicing slicing = new Slicing(path1, path2,
								edgeA, edgeB, mapPDGEdgeToPDGEdgelists);
						final ClonePairInfo clonepair = slicing.perform();
						if (SIZE_THRESHOLD <= clonepair.size()) {
							clonepairs.add(clonepair);
							edgepairsInClonepairs.addAll(clonepair
									.getEdgePairs());
						}
					}
				}
			}

			final Writer writer = new CSVWriter(output, clonepairs);
			writer.write();

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