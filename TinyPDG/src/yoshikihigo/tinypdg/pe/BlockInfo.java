package yoshikihigo.tinypdg.pe;

import java.util.List;

public interface BlockInfo {

	void addStatement(StatementInfo statement);

	List<StatementInfo> getStatements();
}
