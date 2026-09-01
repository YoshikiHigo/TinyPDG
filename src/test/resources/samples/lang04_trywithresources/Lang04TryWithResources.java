package lang04_trywithresources;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Lang04TryWithResources {

	int count(final String path) {
		int lines = 0;
		try (final BufferedReader reader = new BufferedReader(new FileReader(path))) {
			String line;
			while (null != (line = reader.readLine())) {
				lines = lines + line.length();
			}
		} catch (final IOException | RuntimeException e) {
			lines = -1;
		}
		return lines;
	}
}
