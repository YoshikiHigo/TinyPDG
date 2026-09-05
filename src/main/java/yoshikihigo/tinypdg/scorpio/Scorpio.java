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
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import yoshikihigo.tinypdg.CommandLineTools;
import yoshikihigo.tinypdg.ast.JavaAstFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.PDGGeneration;
import yoshikihigo.tinypdg.scorpio.pdg.PDGMergedNode;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.scorpio.data.ClonePairInfo;
import yoshikihigo.tinypdg.scorpio.data.PDGPairInfo;
import yoshikihigo.tinypdg.scorpio.io.BellonWriter;
import yoshikihigo.tinypdg.scorpio.io.ClonePairWriter;

public class Scorpio {

	public static void main(String[] args) {

		try {

			final Options options = new Options();

			options.addOption(CommandLineTools.targetOption());

			{
				final Option o = new Option("o", "output", true, "output file");
				o.setArgName("file");
				o.setArgs(1);
				o.setRequired(true);
				options.addOption(o);
			}

			options.addOption(CommandLineTools.sizeOption(true));

			options.addOption(CommandLineTools.threadsOption());

			options.addOption(CommandLineTools.onOffOption("C", "control",
					"use of control dependency"));

			options.addOption(CommandLineTools.onOffOption("D", "data",
					"use of data dependency"));

			options.addOption(CommandLineTools.onOffOption("E", "execution",
					"use of execution dependency"));

			options.addOption(CommandLineTools.onOffOption("M", "merging",
					"merging consecutive similar nodes"));

			options.addOption(CommandLineTools.javaVersionOption());

			final CommandLineParser parser = new DefaultParser();
			final CommandLine cmd = parser.parse(options, args);

			final File target = CommandLineTools.target(cmd);

			final String output = cmd.getOptionValue("o");
			final int SIZE_THRESHOLD = Integer
					.parseInt(cmd.getOptionValue("s"));
			final int NUMBER_OF_THREADS = CommandLineTools.threads(cmd);

			final boolean useOfControl = CommandLineTools.onOff(cmd, "C");
			final boolean useOfData = CommandLineTools.onOff(cmd, "D");
			final boolean useOfExecution = CommandLineTools.onOff(cmd, "E");
			final boolean mergingRequested = CommandLineTools.onOff(cmd, "M");
			// 併合は実行依存の辺に沿って行うので、実行依存がなければ何もしない。
			final boolean useOfMerging = useOfExecution && mergingRequested;

			final long time1 = System.nanoTime();
			System.out.print("generating PDGs ... ");
			final PDG[] pdgArray;
			{
				final List<MethodInfo> methods = JavaAstFactory
						.collectMethods(target, CommandLineTools.javaVersion(cmd));

				final PDGGeneration.Options generation = new PDGGeneration.Options(
						new PDG.Dependences(useOfControl, useOfData,
								useOfExecution),
						SIZE_THRESHOLD, NUMBER_OF_THREADS);
				// ノードの併合は Scorpio 固有の処理なので、生成側には
				// 「作り終えた PDG に何をするか」として渡す。
				final SortedSet<PDG> pdgs = useOfMerging
						? PDGGeneration.buildInParallel(methods, generation,
								PDGMergedNode::mergeNodes)
						: PDGGeneration.buildInParallel(methods, generation);
				pdgArray = pdgs.toArray(new PDG[0]);
			}
			final long time2 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time2 - time1));

			System.out.print("calculating hash values ... ");
			final SortedMap<PDG, SortedMap<PDGNode<?>, Integer>> mappingPDGToPDGNodes = Collections
					.synchronizedSortedMap(new TreeMap<PDG, SortedMap<PDGNode<?>, Integer>>());
			final SortedMap<PDG, SortedMap<PDGEdge, Integer>> mappingPDGToPDGEdges = Collections
					.synchronizedSortedMap(new TreeMap<>());
			{
				HashCalculation.calculate(pdgArray, mappingPDGToPDGNodes,
						mappingPDGToPDGEdges, NUMBER_OF_THREADS);
			}
			final long time3 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time3 - time2));

			System.out.print("detecting clone pairs ... ");
			final SortedSet<ClonePairInfo> clonepairs = Collections
					.synchronizedSortedSet(new TreeSet<>());
			{
				final List<PDGPairInfo> pdgpairs = new ArrayList<>();
				for (int i = 0; i < pdgArray.length; i++) {
					for (int j = i + 1; j < pdgArray.length; j++) {
						pdgpairs.add(new PDGPairInfo(pdgArray[i], pdgArray[j]));
					}
				}
				final PDGPairInfo[] pdgpairArray = pdgpairs
						.toArray(new PDGPairInfo[0]);
				CloneDetection.detect(pdgpairArray, pdgArray,
						mappingPDGToPDGNodes, mappingPDGToPDGEdges, clonepairs,
						SIZE_THRESHOLD, NUMBER_OF_THREADS);
			}
			final long time4 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time4 - time3));

			System.out.print("writing to a file ... ");
			final ClonePairWriter writer = new BellonWriter(output, clonepairs);
			writer.write();
			final long time5 = System.nanoTime();
			System.out.println("done: " + CommandLineTools.formatElapsed(time5 - time4));

			System.out.println("total elapsed time: "
					+ CommandLineTools.formatElapsed(time5 - time1));

			System.out.print("number of comparisons: ");
			printNumberOfComparison(Slicing.getNumberOfComparison());

		} catch (final Exception e) {
			// 異常終了なので終了コードは非 0 にする。0 のままでは、
			// シェルや CI から呼んだときに成功と区別が付かない。
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void printNumberOfRemoval(final long number) {
		System.out.print("number of removed edges: ");
		System.out.println(String.format("%1$,3d", number));
	}

	private static void printNumberOfComparison(final long number) {
		System.out.println(String.format("%1$,3d", number));
	}

}
