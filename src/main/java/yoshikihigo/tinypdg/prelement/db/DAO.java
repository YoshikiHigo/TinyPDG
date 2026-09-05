package yoshikihigo.tinypdg.prelement.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import yoshikihigo.tinypdg.TinyPDGException;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.prelement.data.Frequency;

/**
 * 依存の頻度を入れる SQLite のデータベース。
 *
 * <p>AutoCloseable なので try-with-resources で使える。書き込みは
 * まとめて実行され、残りは close で流れる。読み戻す前には必ず閉じること。
 */
public class DAO implements AutoCloseable {

	static public final String TEXTS_SCHEMA = "id integer primary key autoincrement, hash integer, text string";
	static public final String FREQUENCIES_SCHEMA = "id integer primary key autoincrement, type string, fromhash integer, tohash integer, support integer, probability real";

	private final Connection connection;
	private final BatchedInsert insertToTexts;
	private final BatchedInsert insertToFrequencies;
	private final PreparedStatement selectFromFrequencies;

	/**
	 * まとめて実行する insert。一定の件数がたまるごとに、そして close で流す。
	 *
	 * <p>texts と frequencies の 2 つの insert が、件数を数えて流す同じ
	 * 手順をそれぞれ持っていた。
	 */
	private static final class BatchedInsert implements AutoCloseable {

		private static final int BATCH_SIZE = 2000;

		private final PreparedStatement statement;
		private int waiting;

		BatchedInsert(final Connection connection, final String sql)
				throws SQLException {
			this.statement = connection.prepareStatement(sql);
			this.waiting = 0;
		}

		/** 引数を詰めるための文。詰めたら {@link #addBatch()} を呼ぶ。 */
		PreparedStatement statement() {
			return this.statement;
		}

		void addBatch() throws SQLException {
			this.statement.addBatch();
			if (BATCH_SIZE < ++this.waiting) {
				this.statement.executeBatch();
				this.waiting = 0;
			}
		}

		@Override
		public void close() throws SQLException {
			try (this.statement) {
				if (0 < this.waiting) {
					this.statement.executeBatch();
					this.waiting = 0;
				}
			}
		}
	}

	/**
	 * @param creation テーブルがなければ作る。読むだけなら false
	 * @throws TinyPDGException データベースを開けない場合
	 */
	public DAO(final String database, final boolean creation) {

		// JDBC 4 以降、ドライバは ServiceLoader が見つける。Class.forName は
		// 要らない。

		try {
			this.connection = DriverManager
					.getConnection("jdbc:sqlite:" + database);
		} catch (final SQLException e) {
			throw new TinyPDGException(
					"データベースを開けませんでした: " + database, e);
		}

		try {
			if (creation) {
				try (final Statement statement = this.connection
						.createStatement()) {
					statement.executeUpdate(
							"create table if not exists texts (" + TEXTS_SCHEMA
									+ ")");
					statement.executeUpdate(
							"create table if not exists frequencies ("
									+ FREQUENCIES_SCHEMA + ")");
				}
			}

			this.insertToTexts = new BatchedInsert(this.connection,
					"insert into texts (hash, text) values (?, ?)");
			this.insertToFrequencies = new BatchedInsert(this.connection,
					"insert into frequencies (type, fromhash, tohash, support, probability) values (?, ?, ?, ?, ?)");
			this.selectFromFrequencies = this.connection.prepareStatement(
					"select tohash, (select text from texts T where T.hash = F.tohash), support, probability from frequencies F where (fromhash = ?) and (type = ?)");

		} catch (final SQLException e) {
			// 開いた接続を残さない。
			try {
				this.connection.close();
			} catch (final SQLException suppressed) {
				e.addSuppressed(suppressed);
			}
			throw new TinyPDGException(
					"データベースを開けませんでした: " + database, e);
		}
	}

	public void addToTexts(final int hash, final String text) {

		try {
			final PreparedStatement insert = this.insertToTexts.statement();
			insert.setInt(1, hash);
			insert.setString(2, text);
			this.insertToTexts.addBatch();

		} catch (final SQLException e) {
			throw new TinyPDGException("texts への書き込みに失敗しました。", e);
		}
	}

	public void addToFrequencies(final PDGEdge.TYPE type,
			final int fromhash, final Frequency frequency) {

		try {
			final PreparedStatement insert = this.insertToFrequencies
					.statement();
			insert.setString(1, type.toString());
			insert.setInt(2, fromhash);
			insert.setInt(3, frequency.hash);
			insert.setInt(4, frequency.support);
			insert.setFloat(5, frequency.probability);
			this.insertToFrequencies.addBatch();

		} catch (final SQLException e) {
			throw new TinyPDGException(
					"frequencies への書き込みに失敗しました。", e);
		}
	}

	public List<Frequency> getFrequencies(final PDGEdge.TYPE type,
			final int fromhash) {

		final List<Frequency> frequencies = new ArrayList<>();

		try {
			this.selectFromFrequencies.clearParameters();
			this.selectFromFrequencies.setInt(1, fromhash);
			this.selectFromFrequencies.setString(2, type.toString());

			try (final ResultSet result = this.selectFromFrequencies
					.executeQuery()) {
				while (result.next()) {
					final int tohash = result.getInt(1);
					final String toText = result.getString(2);
					final int support = result.getInt(3);
					final float probability = result.getFloat(4);
					frequencies.add(new Frequency(probability, support, tohash,
							toText));
				}
			}

		} catch (final SQLException e) {
			throw new TinyPDGException(
					"frequencies の読み出しに失敗しました。", e);
		}

		return frequencies;
	}

	/**
	 * 残っている書き込みを流し、文と接続を閉じる。
	 *
	 * <p>try-with-resources は挙げた逆順に閉じるので、insert が先に閉じて
	 * バッチを流し、最後に接続が閉じる。以前は select の文を閉じ忘れていた。
	 */
	@Override
	public void close() {
		try (this.connection;
				this.selectFromFrequencies;
				this.insertToFrequencies;
				this.insertToTexts) {
			// 閉じるだけ。
		} catch (final SQLException e) {
			throw new TinyPDGException("データベースを閉じられませんでした。", e);
		}
	}
}
