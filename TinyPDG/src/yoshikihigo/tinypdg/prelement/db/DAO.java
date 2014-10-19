package yoshikihigo.tinypdg.prelement.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;
import yoshikihigo.tinypdg.prelement.data.Frequency;

public class DAO {

	static public final String TEXTS_SCHEMA = "id integer primary key autoincrement, hash integer, text string";
	static public final String FREQUENCIES_SCHEMA = "id integer primary key autoincrement, type string, fromhash integer, tohash integer, support integer, probability real";

	protected Connection connector;
	private PreparedStatement insertToTexts;
	private PreparedStatement insertToFrequencies;
	private PreparedStatement selectFromFrequencies;

	private int numberInWaitingBatchForTexts;
	private int numberInWaitingBatchForFrequencies;

	public DAO(final String database, final boolean creation) {

		try {
			Class.forName("org.sqlite.JDBC");
		} catch (final ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		}

		try {
			final StringBuilder url = new StringBuilder();
			url.append("jdbc:sqlite:");
			url.append(database);
			this.connector = DriverManager.getConnection(url.toString());

			if (creation) {
				final Statement statement = this.connector.createStatement();
				statement.executeUpdate("create table if not exists texts ("
						+ TEXTS_SCHEMA + ")");
				statement
						.executeUpdate("create table if not exists frequencies ("
								+ FREQUENCIES_SCHEMA + ")");
			}

			this.insertToTexts = this.connector
					.prepareStatement("insert into texts (hash, text) values (?, ?)");
			this.insertToFrequencies = this.connector
					.prepareStatement("insert into frequencies (type, fromhash, tohash, support, probability) values (?, ?, ?, ?, ?)");
			this.selectFromFrequencies = this.connector
					.prepareStatement("select tohash, (select text from texts T where T.hash = F.tohash), support, probability from frequencies F where (fromhash = ?) and (type = ?)");

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		this.numberInWaitingBatchForTexts = 0;
		this.numberInWaitingBatchForFrequencies = 0;
	}

	public void addToTexts(final int hash, final String text) {

		try {
			this.insertToTexts.setInt(1, hash);
			this.insertToTexts.setString(2, text);
			this.insertToTexts.addBatch();

			if (2000 < ++this.numberInWaitingBatchForTexts) {
				this.insertToTexts.executeBatch();
				this.numberInWaitingBatchForTexts = 0;
			}

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void addToFrequencies(final DEPENDENCE_TYPE type,
			final int fromhash, final Frequency frequency) {

		try {
			this.insertToFrequencies.setString(1, type.text);
			this.insertToFrequencies.setInt(2, fromhash);
			this.insertToFrequencies.setInt(3, frequency.hash);
			this.insertToFrequencies.setInt(4, frequency.support);
			this.insertToFrequencies.setFloat(5, frequency.probablity);
			this.insertToFrequencies.addBatch();

			if (2000 < ++this.numberInWaitingBatchForFrequencies) {
				this.insertToFrequencies.executeBatch();
				this.numberInWaitingBatchForFrequencies = 0;
			}

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public List<Frequency> getFrequencies(final DEPENDENCE_TYPE type,
			final int fromhash) {

		final List<Frequency> frequencies = new ArrayList<Frequency>();

		try {
			this.selectFromFrequencies.clearParameters();
			this.selectFromFrequencies.setInt(1, fromhash);
			this.selectFromFrequencies.setString(2, type.text);
			final ResultSet result = this.selectFromFrequencies.executeQuery();

			while (result.next()) {
				final int tohash = result.getInt(1);
				final String toText = result.getString(2);
				final int support = result.getInt(3);
				final float probability = result.getFloat(4);
				final Frequency frequency = new Frequency(probability, support,
						tohash, toText);
				frequencies.add(frequency);
			}

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		return frequencies;
	}

	public void close() {

		try {

			if (0 < this.numberInWaitingBatchForTexts) {
				this.insertToTexts.executeBatch();
				this.numberInWaitingBatchForTexts = 0;
			}

			if (0 < this.numberInWaitingBatchForFrequencies) {
				this.insertToFrequencies.executeBatch();
				this.numberInWaitingBatchForFrequencies = 0;
			}

			this.insertToTexts.close();
			this.insertToFrequencies.close();
			this.connector.close();

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
