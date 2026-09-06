package yoshikihigo.tinypdg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ConcurrentSkipListSet;

import org.junit.jupiter.api.Test;

class ParallelTest {

	@Test
	void processesEveryIndexOnce() {
		final ConcurrentSkipListSet<Integer> seen = new ConcurrentSkipListSet<>();
		Parallel.forEach(100, 3, seen::add);
		assertEquals(100, seen.size());
	}

	@Test
	void reportsAnExceptionThrownByAWorker() {
		// 以前は握り潰され、残りの添字が処理されないまま正常に戻っていた。
		final TinyPDGException e = assertThrows(TinyPDGException.class,
				() -> Parallel.forEach(10, 1, index -> {
					if (3 == index) {
						throw new IllegalStateException("boom");
					}
				}));
		assertInstanceOf(IllegalStateException.class, e.getCause());
	}

	@Test
	void reportsAnErrorThrownByAWorker() {
		final TinyPDGException e = assertThrows(TinyPDGException.class,
				() -> Parallel.forEach(10, 2, index -> {
					throw new StackOverflowError();
				}));
		assertInstanceOf(StackOverflowError.class, e.getCause());
	}
}
