package yoshikihigo.tinypdg.pe;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

/**
 * 初期化式を持つ繰り返し文。for と foreach である。
 *
 * <p>for は条件式と更新式も持つ。foreach は初期化式に「取り出す変数」と
 * 「対象の式」の 2 つを入れ、条件式と更新式は持たない。
 */
public final class ForStatementInfo extends ConditionalStatementInfo {

	static final Set<CATEGORY> CATEGORIES = EnumSet.of(CATEGORY.For,
			CATEGORY.Foreach);

	final private List<ProgramElementInfo> initializers;
	final private List<ProgramElementInfo> updaters;

	public ForStatementInfo(final ProgramElementInfo ownerBlock,
			final CATEGORY category, final int startLine, final int endLine) {
		super(ownerBlock, category, startLine, endLine, CATEGORIES);
		this.initializers = new ArrayList<>();
		this.updaters = new ArrayList<>();
	}

	public void addInitializer(final ProgramElementInfo initializer) {
		Objects.requireNonNull(initializer, "\"initializer\" is null.");
		this.initializers.add(initializer);
	}

	public List<ProgramElementInfo> getInitializers() {
		return List.copyOf(this.initializers);
	}

	public void addUpdater(final ProgramElementInfo updater) {
		Objects.requireNonNull(updater, "\"updater\" is null.");
		this.updaters.add(updater);
	}

	public List<ProgramElementInfo> getUpdaters() {
		return List.copyOf(this.updaters);
	}

	@Override
	public SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = super.getAssignedVariables();
		for (final ProgramElementInfo initializer : this.initializers) {
			variables.addAll(initializer.getAssignedVariables());
		}
		for (final ProgramElementInfo updater : this.updaters) {
			variables.addAll(updater.getAssignedVariables());
		}
		return variables;
	}

	@Override
	public SortedSet<String> getReferencedVariables() {
		final SortedSet<String> variables = super.getReferencedVariables();
		for (final ProgramElementInfo initializer : this.initializers) {
			variables.addAll(initializer.getReferencedVariables());
		}
		for (final ProgramElementInfo updater : this.updaters) {
			variables.addAll(updater.getReferencedVariables());
		}
		return variables;
	}
}
