package yoshikihigo.tinypdg.prelement;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import yoshikihigo.tinypdg.CommandLineTools;
import yoshikihigo.tinypdg.prelement.data.CombinationalFrequency;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.prelement.data.Frequency;
import yoshikihigo.tinypdg.prelement.db.DAO;

public class ElementPredictor {

	public static void main(final String[] args) {

		try {

			final Options options = new Options();

			options.addOption(CommandLineTools.databaseOption());

			final CommandLineParser parser = new DefaultParser();
			final CommandLine cmd = parser.parse(options, args);

			final String database = cmd.getOptionValue("b");

			try (final DAO dao = new DAO(database, false);
					final BufferedReader in = new BufferedReader(
							new InputStreamReader(System.in))) {
				while (true) {
					System.out.println("input an element for prediction");
					System.out.print("> ");
					final String line = in.readLine();

					// 空行か、入力の終わりで終える。以前は入力の終わり (null) を
					// 見ておらず、NullPointerException で落ちていた。
					if (null == line || line.isEmpty()) {
						System.out.println("done.");
						// これは正常終了。main から戻れば終了コードは 0 になる。
						return;
					}

					final List<CombinationalFrequency> frequencies = getPredictedElements(
							dao, line);
					printCombinationalFrequencies(frequencies);
				}
			}

		} catch (final Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static List<CombinationalFrequency> getPredictedElements(
			final DAO dao, final String baseText) {

		final List<CombinationalFrequency> frequencies = new ArrayList<>();

		final List<Frequency> frequenciesForControl = dao.getFrequencies(
				PDGEdge.TYPE.CONTROL, baseText.hashCode());
		final List<Frequency> frequenciesForData = dao.getFrequencies(
				PDGEdge.TYPE.DATA, baseText.hashCode());
		final List<Frequency> frequenciesForExecution = dao.getFrequencies(
				PDGEdge.TYPE.EXECUTION, baseText.hashCode());

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
			frequencies.add(frequency);
		}

		// 支持度の合計が大きいものから。全部そろってから 1 回だけ並べる。
		frequencies.sort(Comparator
				.comparingInt(CombinationalFrequency::getTotalSupport)
				.reversed());

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
			System.out.print(frequency.control.probability);
			System.out.print(", data: ");
			System.out.print(frequency.data.probability);
			System.out.print(", execution: ");
			System.out.print(frequency.execution.probability);
			System.out.print("), predicted element: ");
			System.out.println(frequency.text);
		}
	}
}
