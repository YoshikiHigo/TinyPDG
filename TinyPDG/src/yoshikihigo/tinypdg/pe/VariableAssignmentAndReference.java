package yoshikihigo.tinypdg.pe;

import java.util.SortedSet;

public interface VariableAssignmentAndReference {

	SortedSet<String> getAssignedVariables();

	SortedSet<String> getReferencedVariables();
}
