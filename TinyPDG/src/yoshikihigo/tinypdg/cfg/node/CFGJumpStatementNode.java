package yoshikihigo.tinypdg.cfg.node;

import yoshikihigo.tinypdg.pe.StatementInfo;

abstract public class CFGJumpStatementNode extends CFGNormalNode {

	CFGJumpStatementNode(final StatementInfo jumpStatement) {
		super(jumpStatement);
	}
}
