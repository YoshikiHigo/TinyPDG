package lang30_catchparam;

public class Lang30CatchParam {

	String message(final Runnable r) {
		String m = "ok";
		try {
			r.run();
		} catch (final RuntimeException e) {
			m = e.getMessage();
		}
		return m;
	}
}
