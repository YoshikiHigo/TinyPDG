package yoshikihigo.tinypdg.pdg.node;

import yoshikihigo.tinypdg.pe.ProgramElementInfo;

public abstract class PDGNormalNode<T extends ProgramElementInfo> extends
		PDGNode<T> {

	PDGNormalNode(final T element) {
		super(element);
	}
}
