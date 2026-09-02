package yoshikihigo.tinypdg.scorpio.bellon;

import java.util.TreeSet;

/**
 * Bellon のベンチマークが使う形式のコード片。行番号の集合として持つ。
 *
 * <p>scorpio.data.CodeFragmentInfo とは別物。以前は両方とも
 * CodeFragmentInfo という名前だった。
 */
class BellonCodeFragment extends TreeSet<Integer> {

	private static final long serialVersionUID = 1L;

	final String path;
	
	BellonCodeFragment(final String path, final int startLine, final int endLine){
		this.path = path;
		for(int line = startLine ; line <= endLine ; line++){
			this.add(line);
		}
	}
	
	@Override
	public String toString(){
		final StringBuilder text = new StringBuilder();
		text.append(this.path);
		text.append("\t");
		for(final Integer line : this){
			text.append(line);
			text.append(",");
		}
		return text.toString();
	}
}
