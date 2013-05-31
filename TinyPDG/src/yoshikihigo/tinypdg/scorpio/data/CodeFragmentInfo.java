package yoshikihigo.tinypdg.scorpio.data;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import yoshikihigo.tinypdg.pe.ProgramElementInfo;

public class CodeFragmentInfo implements Comparable<CodeFragmentInfo> {

	final private SortedSet<ProgramElementInfo> elements;

	public CodeFragmentInfo() {
		this.elements = new TreeSet<ProgramElementInfo>();
	}

	public void addElement(final ProgramElementInfo element) {
		assert null != element : "\"element\" is null.";
		this.elements.add(element);
	}

	public SortedSet<ProgramElementInfo> getElements() {
		final SortedSet<ProgramElementInfo> e = new TreeSet<ProgramElementInfo>();
		e.addAll(this.elements);
		return e;
	}

	@Override
	public int compareTo(final CodeFragmentInfo o) {
		assert null != o : "\"o\" is null.";
		final Iterator<ProgramElementInfo> iterator1 = this.elements.iterator();
		final Iterator<ProgramElementInfo> iterator2 = o.elements.iterator();

		while (true) {

			if (!iterator1.hasNext() && !iterator2.hasNext()) {
				return 0;
			}

			else if (!iterator1.hasNext()) {
				return -1;
			}

			else if (!iterator2.hasNext()) {
				return 1;
			}

			final ProgramElementInfo element1 = iterator1.next();
			final ProgramElementInfo element2 = iterator2.next();
			final int elementOrder = element1.compareTo(element2);
			if (0 != elementOrder) {
				return elementOrder;
			}
		}
	}
}
