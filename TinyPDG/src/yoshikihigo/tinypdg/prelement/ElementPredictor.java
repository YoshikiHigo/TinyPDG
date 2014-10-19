package yoshikihigo.tinypdg.prelement;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import yoshikihigo.tinypdg.prelement.data.CombinationalFrequency;
import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;
import yoshikihigo.tinypdg.prelement.data.Frequency;
import yoshikihigo.tinypdg.prelement.db.DAO;

public class ElementPredictor {

	public static void main(final String[] args) {

		try {

			final Options options = new Options();

			{
				final Option b = new Option("b", "database", true, "database");
				b.setArgName("database");
				b.setArgs(1);
				b.setRequired(true);
				options.addOption(b);
			}

			final CommandLineParser parser = new PosixParser();
			final CommandLine cmd = parser.parse(options, args);

			final String database = cmd.getOptionValue("b");
			final DAO dao = new DAO(database, false);

			final BufferedReader in = new BufferedReader(new InputStreamReader(
					System.in));
			while (true) {
				System.out.println("input an element for prediction");
				System.out.print("> ");
				final String line = in.readLine();

				if (line.equals("")) {
					in.close();
					System.out.println("done.");
					System.exit(0);
				}

				final List<CombinationalFrequency> frequencies = getPredictedElements(
						dao, line);
				printCombinationalFrequencies(frequencies);
			}

		} catch (final Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public static List<CombinationalFrequency> getPredictedElements(
			final DAO dao, final String baseText) {

		final List<CombinationalFrequency> frequencies = new ArrayList<CombinationalFrequency>();

		final List<Frequency> frequenciesForControl = dao.getFrequencies(
				DEPENDENCE_TYPE.CONTROL, baseText.hashCode());
		final List<Frequency> frequenciesForData = dao.getFrequencies(
				DEPENDENCE_TYPE.DATA, baseText.hashCode());
		final List<Frequency> frequenciesForExecution = dao.getFrequencies(
				DEPENDENCE_TYPE.EXECUTION, baseText.hashCode());

		final Iterator<Frequency> iteratorForControl = frequenciesForControl
				.iterator();
		while (iteratorForControl.hasNext()) {

			final Frequency frequencyForControl = iteratorForControl.next();
			iteratorForControl.remove();

			Frequency correspondingOnData = null;
			final Iterator<Frequency> iteratorForData = frequenciesForData
					.iterator();
			while (iteratorForData.hasNext()) {

				final Frequency frequencyForData = iteratorForData.next();
				if (frequencyForControl.hash == frequencyForData.hash) {
					iteratorForData.remove();
					correspondingOnData = frequencyForData;
					break;
				}
			}

			Frequency correspondingOnExecution = null;
			final Iterator<Frequency> iteratorForExecution = frequenciesForExecution
					.iterator();
			while (iteratorForExecution.hasNext()) {

				final Frequency frequencyForExecution = iteratorForExecution
						.next();
				if (frequencyForControl.hash == frequencyForExecution.hash) {
					iteratorForExecution.remove();
					correspondingOnExecution = frequencyForExecution;
					break;
				}
			}

			final CombinationalFrequency frequency = new CombinationalFrequency(
					frequencyForControl.hash, frequencyForControl.text,
					frequencyForControl, correspondingOnData,
					correspondingOnExecution);
			frequencies.add(frequency);
		}

		final Iterator<Frequency> iteratorForData = frequenciesForData
				.iterator();
		while (iteratorForData.hasNext()) {

			final Frequency frequencyForData = iteratorForData.next();
			iteratorForData.remove();

			Frequency correspondingOnExecution = null;
			final Iterator<Frequency> iteratorForExecution = frequenciesForExecution
					.iterator();
			while (iteratorForExecution.hasNext()) {

				final Frequency frequencyForExecution = iteratorForExecution
						.next();
				if (frequencyForData.hash == frequencyForExecution.hash) {
					iteratorForExecution.remove();
					correspondingOnExecution = frequencyForExecution;
					break;
				}
			}

			final CombinationalFrequency frequency = new CombinationalFrequency(
					frequencyForData.hash, frequencyForData.text, null,
					frequencyForData, correspondingOnExecution);
			frequencies.add(frequency);
		}

		final Iterator<Frequency> iteratorForExecution = frequenciesForExecution
				.iterator();
		while (iteratorForExecution.hasNext()) {

			final Frequency frequencyForExecution = iteratorForExecution.next();
			iteratorForExecution.remove();

			final CombinationalFrequency frequency = new CombinationalFrequency(
					frequencyForExecution.hash, frequencyForExecution.text,
					null, null, frequencyForExecution);
			Collections.sort(frequencies,
					new Comparator<CombinationalFrequency>() {

						@Override
						public int compare(final CombinationalFrequency f1,
								final CombinationalFrequency f2) {

							if (f1.getTotalSupport() > f2.getTotalSupport()) {
								return -1;
							} else if (f1.getTotalSupport() < f2
									.getTotalSupport()) {
								return 1;
							} else {
								return 0;
							}
						}
					});
			frequencies.add(frequency);
		}

		return frequencies;
	}

	public static void printCombinationalFrequencies(
			final List<CombinationalFrequency> frequencies) {

		for (final CombinationalFrequency frequency : frequencies) {
			System.out.print("support: ");
			System.out.print(frequency.getTotalSupport());
			System.out.print(" (control: ");
			System.out.print(frequency.control.support);
			System.out.print(", data: ");
			System.out.print(frequency.data.support);
			System.out.print(", execution: ");
			System.out.print(frequency.execution.support);
			System.out.print("), probability: ");
			System.out.print(frequency.getTotalProbability());
			System.out.print(" (control: ");
			System.out.print(frequency.control.probablity);
			System.out.print(", data: ");
			System.out.print(frequency.data.probablity);
			System.out.print(", execution: ");
			System.out.print(frequency.execution.probablity);
			System.out.print("), predicted element: ");
			System.out.println(frequency.text);
		}
	}
}
