package yoshikihigo.tinypdg.scorpio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.dom.CompilationUnit;

import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;

public class PDGGenerationThread implements Runnable {

	final static private AtomicInteger INDEX = new AtomicInteger(0);

	final private List<File> files;
	final private List<PDG> pdgs;
	final private CFGNodeFactory cfgNodeFactory;
	final private PDGNodeFactory pdgNodeFactory;

	public PDGGenerationThread(final List<File> files, final List<PDG> pdgs,
			final CFGNodeFactory cfgNodeFactory,
			final PDGNodeFactory pdgNodeFactory) {
		assert null != files : "\"files\" is null.";
		assert null != pdgs : "\"pdgs\" is null.";
		assert null != cfgNodeFactory : "\"cfgNodeFactory\" is null.";
		assert null != pdgNodeFactory : "\"pdgNodeFactory\" is null.";
		this.files = files;
		this.pdgs = pdgs;
		this.cfgNodeFactory = cfgNodeFactory;
		this.pdgNodeFactory = pdgNodeFactory;
	}

	@Override
	public void run() {
		for (int index = INDEX.getAndIncrement(); index < this.files.size(); index = INDEX
				.getAndIncrement()) {
			final File file = this.files.get(index);
			final CompilationUnit unit = TinyPDGASTVisitor.createAST(file);
			final List<MethodInfo> methods = new ArrayList<MethodInfo>();
			final TinyPDGASTVisitor visitor = new TinyPDGASTVisitor(
					file.getAbsolutePath(), unit, methods);
			unit.accept(visitor);
			for (final MethodInfo method : methods) {
				final PDG pdg = new PDG(method, this.pdgNodeFactory,
						this.cfgNodeFactory, true, true, true,
						Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
				pdg.build();
				this.pdgs.add(pdg);
			}
		}
	}

}
