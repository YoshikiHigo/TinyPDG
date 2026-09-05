package yoshikihigo.tinypdg;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

/**
 * 添字の並びを、決まった本数のスレッドで分担して処理する。
 *
 * <p>PDG の生成、ハッシュの計算、クローンの検出が同じ骨組みを 3 回書いて
 * いた。共有のカウンタから次の添字を取り出すスレッドを threads 本起こし、
 * 全てが終わるまで待つ、というものである。
 *
 * <p>カウンタは 1 回の呼び出しごとに作る。以前は各クラスの static フィールド
 * で、JVM 内の全ての実行で共有され巻き戻らなかったため、同じ JVM で 2 回目を
 * 走らせると 1 回目が数え終わった位置から始まり、何も処理せずに正常終了して
 * いるように見えていた。
 */
public final class Parallel {

	private Parallel() {
	}

	/**
	 * 0 以上 count 未満の添字を threads 本のスレッドで分担して処理し、全てが
	 * 終わるまで待つ。どの添字がどのスレッドに渡るかは実行ごとに変わる。
	 */
	public static void forEach(final int count, final int threads,
			final IntConsumer work) {
		Parallel.<Object>forEach(count, threads, () -> null,
				(state, index) -> work.accept(index), state -> {
				});
	}

	/**
	 * スレッドごとの状態を持つ版。
	 *
	 * <p>各スレッドは、始めに newState で自分の状態を作り、渡された添字ごとに
	 * その状態と添字で work を呼び、添字が尽きたら finish に状態を渡す。
	 * クローンの検出が、スレッドごとに見つけたペアを集めて終わりにまとめて
	 * 重複を落とすのに使う。
	 *
	 * @param <S> スレッドごとの状態
	 */
	public static <S> void forEach(final int count, final int threads,
			final Supplier<S> newState, final ObjIntConsumer<S> work,
			final Consumer<S> finish) {

		if (threads < 1) {
			throw new IllegalArgumentException(
					"threads は 1 以上でなければならない: " + threads);
		}

		final AtomicInteger next = new AtomicInteger(0);

		// close() が全タスクの終了を待つ。
		try (final ExecutorService pool = Executors
				.newFixedThreadPool(threads)) {
			for (int i = 0; i < threads; i++) {
				pool.execute(() -> {
					final S state = newState.get();
					for (int index = next.getAndIncrement(); index < count; index = next
							.getAndIncrement()) {
						work.accept(state, index);
					}
					finish.accept(state);
				});
			}
		}
	}
}
