package yoshikihigo.tinypdg.cfg.node;

import yoshikihigo.tinypdg.pe.StatementInfo;

abstract public class CFGJumpStatementNode extends CFGNormalNode {

	private StatementInfo statement;

	CFGJumpStatementNode(final StatementInfo jumpStatement) {
		super(jumpStatement);
		this.statement = null;
	}
}
