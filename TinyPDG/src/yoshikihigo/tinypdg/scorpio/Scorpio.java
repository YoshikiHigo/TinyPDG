package yoshikihigo.tinypdg.scorpio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
import yoshikihigo.tinypdg.pdg.node.PDGMethodEnterNode;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;

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

			final CommandLineParser parser = new PosixParser();
			final CommandLine cmd = parser.parse(options, args);

			final File target = new File(cmd.getOptionValue("d"));
			if (!target.exists()) {
				System.err
						.println("specified directory or file does not exist.");
				System.exit(0);
			}

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
					pdgs.add(pdg);
				}
			}

			final ConcurrentMap<Integer, List<PDGNode<?>>> mapHashToPDGNodelists = new ConcurrentHashMap<Integer, List<PDGNode<?>>>();
			for (final PDG pdg : pdgs) {
				for (final PDGNode<?> node : pdg.getAllNodes()) {
					if (node instanceof PDGMethodEnterNode) {

					} else {
						final NormalizedText t = new NormalizedText(node.core);
						final String normalizedText = t.getText();
						final int hash = normalizedText.hashCode();
						List<PDGNode<?>> nodeList = mapHashToPDGNodelists
								.get(hash);
						if (null == nodeList) {
							nodeList = new ArrayList<PDGNode<?>>();
							mapHashToPDGNodelists.put(hash, nodeList);
						}
						nodeList.add(node);
					}
				}
			}

			final ConcurrentMap<PDGNode<?>, List<PDGNode<?>>> mapPDGNodeToPDGNodelists = new ConcurrentHashMap<PDGNode<?>, List<PDGNode<?>>>();
			for (final List<PDGNode<?>> list : mapHashToPDGNodelists.values()) {
				if (1 < list.size()) {
					for (final PDGNode<?> node : list) {
						mapPDGNodeToPDGNodelists.put(node, list);
					}
				}
			}

			System.out.println("done.");

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
}