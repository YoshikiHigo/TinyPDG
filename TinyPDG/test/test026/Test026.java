public class Test026 {
	public int getNextToken() throws InvalidInputException {
		this.wasAcr = false;
		if (diet) {
			jumpOverMethodBody();
			diet = false;
			return currentPosition > source.length ? TokenNameEOF
					: TokenNameRBRACE;
		}
		try {
			while (true) { // loop for jumping over comments
				withoutUnicodePtr = 0;
				// start with a new token (even comment written with unicode )
				// ---------Consume white space and handles
				// startPosition---------
				int whiteStart = currentPosition;
				boolean isWhiteSpace;
				do {
					startPosition = currentPosition;
					if (((currentCharacter = source[currentPosition++]) == '\\')
							&& (source[currentPosition] == 'u')) {
						isWhiteSpace = jumpOverUnicodeWhiteSpace();
					} else {
						if ((currentCharacter == '\r')
								|| (currentCharacter == '\n')) {
							checkNonExternalizeString();
							if (recordLineSeparator) {
								pushLineSeparator();
							} else {
								currentLine = null;
							}
						}
						isWhiteSpace = (currentCharacter == ' ')
								|| Character.isWhitespace(currentCharacter);
					}
				} while (isWhiteSpace);
				if (tokenizeWhiteSpace && (whiteStart != currentPosition - 1)) {
					// reposition scanner in case we are interested by spaces as
					// tokens
					currentPosition--;
					startPosition = whiteStart;
					return TokenNameWHITESPACE;
				}
				// little trick to get out in the middle of a source compuation
				if (currentPosition > eofPosition)
					return TokenNameEOF;
				// ---------Identify the next token-------------
				switch (currentCharacter) {
				case '(':
					return TokenNameLPAREN;
				case ')':
					return TokenNameRPAREN;
				case '{':
					return TokenNameLBRACE;
				case '}':
					return TokenNameRBRACE;
				case '[':
					return TokenNameLBRACKET;
				case ']':
					return TokenNameRBRACKET;
				case ';':
					return TokenNameSEMICOLON;
				case ',':
					return TokenNameCOMMA;
				case '.':
					if (getNextCharAsDigit())
						return scanNumber(true);
					return TokenNameDOT;
				case '+': {
					int test;
					if ((test = getNextChar('+', '=')) == 0)
						return TokenNamePLUS_PLUS;
					if (test > 0)
						return TokenNamePLUS_EQUAL;
					return TokenNamePLUS;
				}
				case '-': {
					int test;
					if ((test = getNextChar('-', '=')) == 0)
						return TokenNameMINUS_MINUS;
					if (test > 0)
						return TokenNameMINUS_EQUAL;
					return TokenNameMINUS;
				}
				case '~':
					return TokenNameTWIDDLE;
				case '!':
					if (getNextChar('='))
						return TokenNameNOT_EQUAL;
					return TokenNameNOT;
				case '*':
					if (getNextChar('='))
						return TokenNameMULTIPLY_EQUAL;
					return TokenNameMULTIPLY;
				case '%':
					if (getNextChar('='))
						return TokenNameREMAINDER_EQUAL;
					return TokenNameREMAINDER;
				case '<': {
					int test;
					if ((test = getNextChar('=', '<')) == 0)
						return TokenNameLESS_EQUAL;
					if (test > 0) {
						if (getNextChar('='))
							return TokenNameLEFT_SHIFT_EQUAL;
						return TokenNameLEFT_SHIFT;
					}
					return TokenNameLESS;
				}
				case '>': {
					int test;
					if ((test = getNextChar('=', '>')) == 0)
						return TokenNameGREATER_EQUAL;
					if (test > 0) {
						if ((test = getNextChar('=', '>')) == 0)
							return TokenNameRIGHT_SHIFT_EQUAL;
						if (test > 0) {
							if (getNextChar('='))
								return TokenNameUNSIGNED_RIGHT_SHIFT_EQUAL;
							return TokenNameUNSIGNED_RIGHT_SHIFT;
						}
						return TokenNameRIGHT_SHIFT;
					}
					return TokenNameGREATER;
				}
				case '=':
					if (getNextChar('='))
						return TokenNameEQUAL_EQUAL;
					return TokenNameEQUAL;
				case '&': {
					int test;
					if ((test = getNextChar('&', '=')) == 0)
						return TokenNameAND_AND;
					if (test > 0)
						return TokenNameAND_EQUAL;
					return TokenNameAND;
				}
				case '|': {
					int test;
					if ((test = getNextChar('|', '=')) == 0)
						return TokenNameOR_OR;
					if (test > 0)
						return TokenNameOR_EQUAL;
					return TokenNameOR;
				}
				case '^':
					if (getNextChar('='))
						return TokenNameXOR_EQUAL;
					return TokenNameXOR;
				case '?':
					return TokenNameQUESTION;
				case ':':
					return TokenNameCOLON;
				case '\'': {
					int test;
					if ((test = getNextChar('\n', '\r')) == 0) {
						throw new InvalidInputException(
								INVALID_CHARACTER_CONSTANT);
					}
					if (test > 0) {
						// relocate if finding another quote fairly close: thus
						// unicode '/u000D' will be fully consumed
						for (int lookAhead = 0; lookAhead < 3; lookAhead++) {
							if (currentPosition + lookAhead == source.length)
								break;
							if (source[currentPosition + lookAhead] == '\n')
								break;
							if (source[currentPosition + lookAhead] == '\'') {
								currentPosition += lookAhead + 1;
								break;
							}
						}
						throw new InvalidInputException(
								INVALID_CHARACTER_CONSTANT);
					}
				}
					if (getNextChar('\'')) {
						// relocate if finding another quote fairly close: thus
						// unicode '/u000D' will be fully consumed
						for (int lookAhead = 0; lookAhead < 3; lookAhead++) {
							if (currentPosition + lookAhead == source.length)
								break;
							if (source[currentPosition + lookAhead] == '\n')
								break;
							if (source[currentPosition + lookAhead] == '\'') {
								currentPosition += lookAhead + 1;
								break;
							}
						}
						throw new InvalidInputException(
								INVALID_CHARACTER_CONSTANT);
					}
					if (getNextChar('\\'))
						scanEscapeCharacter();
					else { // consume next character
						unicodeAsBackSlash = false;
						if (((currentCharacter = source[currentPosition++]) == '\\')
								&& (source[currentPosition] == 'u')) {
							getNextUnicodeChar();
						} else {
							if (withoutUnicodePtr != 0) {
								withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
							}
						}
					}
					if (getNextChar('\''))
						return TokenNameCharacterLiteral;
					// relocate if finding another quote fairly close: thus
					// unicode '/u000D' will be fully consumed
					for (int lookAhead = 0; lookAhead < 20; lookAhead++) {
						if (currentPosition + lookAhead == source.length)
							break;
						if (source[currentPosition + lookAhead] == '\n')
							break;
						if (source[currentPosition + lookAhead] == '\'') {
							currentPosition += lookAhead + 1;
							break;
						}
					}
					throw new InvalidInputException(INVALID_CHARACTER_CONSTANT);
				case '"':
					try {
						// consume next character
						unicodeAsBackSlash = false;
						if (((currentCharacter = source[currentPosition++]) == '\\')
								&& (source[currentPosition] == 'u')) {
							getNextUnicodeChar();
						} else {
							if (withoutUnicodePtr != 0) {
								withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
							}
						}
						while (currentCharacter != '"') {
							/**** \r and \n are not valid in string literals ****/
							if ((currentCharacter == '\n')
									|| (currentCharacter == '\r')) {
								// relocate if finding another quote fairly
								// close: thus unicode '/u000D' will be fully
								// consumed
								for (int lookAhead = 0; lookAhead < 50; lookAhead++) {
									if (currentPosition + lookAhead == source.length)
										break;
									if (source[currentPosition + lookAhead] == '\n')
										break;
									if (source[currentPosition + lookAhead] == '\"') {
										currentPosition += lookAhead + 1;
										break;
									}
								}
								throw new InvalidInputException(
										INVALID_CHAR_IN_STRING);
							}
							if (currentCharacter == '\\') {
								int escapeSize = currentPosition;
								boolean backSlashAsUnicodeInString = unicodeAsBackSlash;
								// scanEscapeCharacter make a side effect on
								// this value and we need the previous value few
								// lines down this one
								scanEscapeCharacter();
								escapeSize = currentPosition - escapeSize;
								if (withoutUnicodePtr == 0) {
									// buffer all the entries that have been
									// left aside....
									withoutUnicodePtr = currentPosition
											- escapeSize - 1 - startPosition;
									System.arraycopy(source, startPosition,
											withoutUnicodeBuffer, 1,
											withoutUnicodePtr);
									withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
								} else { // overwrite the / in the buffer
									withoutUnicodeBuffer[withoutUnicodePtr] = currentCharacter;
									if (backSlashAsUnicodeInString) { // there
																		// are
																		// TWO \
																		// in
																		// the
																		// stream
																		// where
																		// only
																		// one
																		// is
																		// correct
										withoutUnicodePtr--;
									}
								}
							}
							// consume next character
							unicodeAsBackSlash = false;
							if (((currentCharacter = source[currentPosition++]) == '\\')
									&& (source[currentPosition] == 'u')) {
								getNextUnicodeChar();
							} else {
								if (withoutUnicodePtr != 0) {
									withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
								}
							}
						}
					} catch (IndexOutOfBoundsException e) {
						throw new InvalidInputException(UNTERMINATED_STRING);
					} catch (InvalidInputException e) {
						if (e.getMessage().equals(INVALID_ESCAPE)) {
							// relocate if finding another quote fairly close:
							// thus unicode '/u000D' will be fully consumed
							for (int lookAhead = 0; lookAhead < 50; lookAhead++) {
								if (currentPosition + lookAhead == source.length)
									break;
								if (source[currentPosition + lookAhead] == '\n')
									break;
								if (source[currentPosition + lookAhead] == '\"') {
									currentPosition += lookAhead + 1;
									break;
								}
							}
						}
						throw e;
					}// rethrow
					if (checkNonExternalizedStringLiterals) { // check for presence of NLS tags //$NON-NLS-?$ where ? is an int.
						if (currentLine == null) {
							currentLine = new NLSLine();
							lines.add(currentLine);
						}
						currentLine.add(new StringLiteral(
								getCurrentTokenSourceString(), startPosition,
								currentPosition - 1));
					}
					return TokenNameStringLiteral;
				case '/': {
					int test;
					if ((test = getNextChar('/', '*')) == 0) { // line comment
						int endPositionForLineComment = 0;
						try { // get the next char
							if (((currentCharacter = source[currentPosition++]) == '\\')
									&& (source[currentPosition] == 'u')) {
								// -------------unicode traitement ------------
								int c1 = 0, c2 = 0, c3 = 0, c4 = 0;
								currentPosition++;
								while (source[currentPosition] == 'u') {
									currentPosition++;
								}
								if ((c1 = Character
										.getNumericValue(source[currentPosition++])) > 15
										|| c1 < 0
										|| (c2 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c2 < 0
										|| (c3 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c3 < 0
										|| (c4 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c4 < 0) {
									throw new InvalidInputException(
											INVALID_UNICODE_ESCAPE);
								} else {
									currentCharacter = (char) (((c1 * 16 + c2) * 16 + c3) * 16 + c4);
								}
							}
							// handle the \\u case manually into comment
							if (currentCharacter == '\\') {
								if (source[currentPosition] == '\\')
									currentPosition++;
							} // jump over the \\
							boolean isUnicode = false;
							while (currentCharacter != '\r'
									&& currentCharacter != '\n') {
								// get the next char
								isUnicode = false;
								if (((currentCharacter = source[currentPosition++]) == '\\')
										&& (source[currentPosition] == 'u')) {
									isUnicode = true;
									// -------------unicode traitement
									// ------------
									int c1 = 0, c2 = 0, c3 = 0, c4 = 0;
									currentPosition++;
									while (source[currentPosition] == 'u') {
										currentPosition++;
									}
									if ((c1 = Character
											.getNumericValue(source[currentPosition++])) > 15
											|| c1 < 0
											|| (c2 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c2 < 0
											|| (c3 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c3 < 0
											|| (c4 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c4 < 0) {
										throw new InvalidInputException(
												INVALID_UNICODE_ESCAPE);
									} else {
										currentCharacter = (char) (((c1 * 16 + c2) * 16 + c3) * 16 + c4);
									}
								}
								// handle the \\u case manually into comment
								if (currentCharacter == '\\') {
									if (source[currentPosition] == '\\')
										currentPosition++;
								}
							}// jump over the \\
							if (isUnicode) {
								endPositionForLineComment = currentPosition - 6;
							} else {
								endPositionForLineComment = currentPosition - 1;
							}
							recordComment(false);
							if ((currentCharacter == '\r')
									|| (currentCharacter == '\n')) {
								checkNonExternalizeString();
								if (recordLineSeparator) {
									if (isUnicode) {
										pushUnicodeLineSeparator();
									} else {
										pushLineSeparator();
									}
								} else {
									currentLine = null;
								}
							}
							if (tokenizeComments) {
								if (!isUnicode) {
									currentPosition = endPositionForLineComment;
								}// reset one character behind
								return TokenNameCOMMENT_LINE;
							}
						} catch (IndexOutOfBoundsException e) { // an eof will
																// them be
																// generated
							if (tokenizeComments) {
								currentPosition--; // reset one character behind
								return TokenNameCOMMENT_LINE;
							}
						}
						break;
					}
					if (test > 0) { // traditional and annotation comment
						boolean isJavadoc = false, star = false;
						// consume next character
						unicodeAsBackSlash = false;
						if (((currentCharacter = source[currentPosition++]) == '\\')
								&& (source[currentPosition] == 'u')) {
							getNextUnicodeChar();
						} else {
							if (withoutUnicodePtr != 0) {
								withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
							}
						}
						if (currentCharacter == '*') {
							isJavadoc = true;
							star = true;
						}
						if ((currentCharacter == '\r')
								|| (currentCharacter == '\n')) {
							checkNonExternalizeString();
							if (recordLineSeparator) {
								pushLineSeparator();
							} else {
								currentLine = null;
							}
						}
						try { // get the next char
							if (((currentCharacter = source[currentPosition++]) == '\\')
									&& (source[currentPosition] == 'u')) {
								// -------------unicode traitement ------------
								getNextUnicodeChar();
							}
							// handle the \\u case manually into comment
							if (currentCharacter == '\\') {
								if (source[currentPosition] == '\\')
									currentPosition++;
							}// jump over the \\
							// empty comment is not a javadoc /**/
							if (currentCharacter == '/') {
								isJavadoc = false;
							}
							// loop until end of comment */
							while ((currentCharacter != '/') || (!star)) {
								if ((currentCharacter == '\r')
										|| (currentCharacter == '\n')) {
									checkNonExternalizeString();
									if (recordLineSeparator) {
										pushLineSeparator();
									} else {
										currentLine = null;
									}
								}
								star = currentCharacter == '*';
								// get next char
								if (((currentCharacter = source[currentPosition++]) == '\\')
										&& (source[currentPosition] == 'u')) {
									// -------------unicode traitement
									// ------------
									getNextUnicodeChar();
								}
								// handle the \\u case manually into comment
								if (currentCharacter == '\\') {
									if (source[currentPosition] == '\\')
										currentPosition++;
								}
							}// jump over the \\
							recordComment(isJavadoc);
							if (tokenizeComments) {
								if (isJavadoc)
									return TokenNameCOMMENT_JAVADOC;
								return TokenNameCOMMENT_BLOCK;
							}
						} catch (IndexOutOfBoundsException e) {
							throw new InvalidInputException(
									UNTERMINATED_COMMENT);
						}
						break;
					}
					if (getNextChar('='))
						return TokenNameDIVIDE_EQUAL;
					return TokenNameDIVIDE;
				}
				case '\u001a':
					if (atEnd())
						return TokenNameEOF;
					// the atEnd may not be <currentPosition == source.length>
					// if source is only some part of a real (external) stream
					throw new InvalidInputException("Ctrl-Z"); //$NON-NLS-1$
				default:
					if (Character.isJavaIdentifierStart(currentCharacter))
						return scanIdentifierOrKeyword();
					if (Character.isDigit(currentCharacter))
						return scanNumber(false);
					return TokenNameERROR;
				}
			}
		} // -----------------end switch while try--------------------
		catch (IndexOutOfBoundsException e) {
		}
		return TokenNameEOF;
	}

	public int getNextToken() throws InvalidInputException {
		this.wasAcr = false;
		if (diet) {
			jumpOverMethodBody();
			diet = false;
			return currentPosition > source.length ? TokenNameEOF
					: TokenNameRBRACE;
		}
		try {
			while (true) { // loop for jumping over comments
				withoutUnicodePtr = 0;
				// start with a new token (even comment written with unicode )
				// ---------Consume white space and handles
				// startPosition---------
				int whiteStart = currentPosition;
				boolean isWhiteSpace;
				do {
					startPosition = currentPosition;
					if (((currentCharacter = source[currentPosition++]) == '\\')
							&& (source[currentPosition] == 'u')) {
						isWhiteSpace = jumpOverUnicodeWhiteSpace();
					} else {
						if (recordLineSeparator
								&& ((currentCharacter == '\r') || (currentCharacter == '\n')))
							pushLineSeparator();
						isWhiteSpace = (currentCharacter == ' ')
								|| Character.isWhitespace(currentCharacter);
					}
					/* completion requesting strictly inside blanks */
					if ((whiteStart != currentPosition)
							// && (previousToken == TokenNameDOT)
							&& (completionIdentifier == null)
							&& (whiteStart <= cursorLocation + 1)
							&& (cursorLocation < startPosition)
							&& !Character
									.isJavaIdentifierStart(currentCharacter)) {
						currentPosition = startPosition; // for next token read
						return TokenNameIdentifier;
					}
				} while (isWhiteSpace);
				if (tokenizeWhiteSpace && (whiteStart != currentPosition - 1)) {
					// reposition scanner in case we are interested by spaces as
					// tokens
					currentPosition--;
					startPosition = whiteStart;
					return TokenNameWHITESPACE;
				}
				// little trick to get out in the middle of a source comptuation
				if (currentPosition > eofPosition) {
					/* might be completing at eof (e.g. behind a dot) */
					if (completionIdentifier == null
							&& startPosition == cursorLocation + 1) {
						currentPosition = startPosition; // for being detected
															// as empty free
															// identifier
						return TokenNameIdentifier;
					}
					return TokenNameEOF;
				}
				// ---------Identify the next token-------------
				switch (currentCharacter) {
				case '(':
					return TokenNameLPAREN;
				case ')':
					return TokenNameRPAREN;
				case '{':
					return TokenNameLBRACE;
				case '}':
					return TokenNameRBRACE;
				case '[':
					return TokenNameLBRACKET;
				case ']':
					return TokenNameRBRACKET;
				case ';':
					return TokenNameSEMICOLON;
				case ',':
					return TokenNameCOMMA;
				case '.':
					if (getNextCharAsDigit())
						return scanNumber(true);
					return TokenNameDOT;
				case '+': {
					int test;
					if ((test = getNextChar('+', '=')) == 0)
						return TokenNamePLUS_PLUS;
					if (test > 0)
						return TokenNamePLUS_EQUAL;
					return TokenNamePLUS;
				}
				case '-': {
					int test;
					if ((test = getNextChar('-', '=')) == 0)
						return TokenNameMINUS_MINUS;
					if (test > 0)
						return TokenNameMINUS_EQUAL;
					return TokenNameMINUS;
				}
				case '~':
					return TokenNameTWIDDLE;
				case '!':
					if (getNextChar('='))
						return TokenNameNOT_EQUAL;
					return TokenNameNOT;
				case '*':
					if (getNextChar('='))
						return TokenNameMULTIPLY_EQUAL;
					return TokenNameMULTIPLY;
				case '%':
					if (getNextChar('='))
						return TokenNameREMAINDER_EQUAL;
					return TokenNameREMAINDER;
				case '<': {
					int test;
					if ((test = getNextChar('=', '<')) == 0)
						return TokenNameLESS_EQUAL;
					if (test > 0) {
						if (getNextChar('='))
							return TokenNameLEFT_SHIFT_EQUAL;
						return TokenNameLEFT_SHIFT;
					}
					return TokenNameLESS;
				}
				case '>': {
					int test;
					if ((test = getNextChar('=', '>')) == 0)
						return TokenNameGREATER_EQUAL;
					if (test > 0) {
						if ((test = getNextChar('=', '>')) == 0)
							return TokenNameRIGHT_SHIFT_EQUAL;
						if (test > 0) {
							if (getNextChar('='))
								return TokenNameUNSIGNED_RIGHT_SHIFT_EQUAL;
							return TokenNameUNSIGNED_RIGHT_SHIFT;
						}
						return TokenNameRIGHT_SHIFT;
					}
					return TokenNameGREATER;
				}
				case '=':
					if (getNextChar('='))
						return TokenNameEQUAL_EQUAL;
					return TokenNameEQUAL;
				case '&': {
					int test;
					if ((test = getNextChar('&', '=')) == 0)
						return TokenNameAND_AND;
					if (test > 0)
						return TokenNameAND_EQUAL;
					return TokenNameAND;
				}
				case '|': {
					int test;
					if ((test = getNextChar('|', '=')) == 0)
						return TokenNameOR_OR;
					if (test > 0)
						return TokenNameOR_EQUAL;
					return TokenNameOR;
				}
				case '^':
					if (getNextChar('='))
						return TokenNameXOR_EQUAL;
					return TokenNameXOR;
				case '?':
					return TokenNameQUESTION;
				case ':':
					return TokenNameCOLON;
				case '\'': {
					int test;
					if ((test = getNextChar('\n', '\r')) == 0) {
						throw new InvalidInputException(
								INVALID_CHARACTER_CONSTANT);
					}
					if (test > 0) {
						// relocate if finding another quote fairly close: thus
						// unicode '/u000D' will be fully consumed
						for (int lookAhead = 0; lookAhead < 3; lookAhead++) {
							if (currentPosition + lookAhead == source.length)
								break;
							if (source[currentPosition + lookAhead] == '\n')
								break;
							if (source[currentPosition + lookAhead] == '\'') {
								currentPosition += lookAhead + 1;
								break;
							}
						}
						throw new InvalidInputException(
								INVALID_CHARACTER_CONSTANT);
					}
				}
					if (getNextChar('\'')) {
						// relocate if finding another quote fairly close: thus
						// unicode '/u000D' will be fully consumed
						for (int lookAhead = 0; lookAhead < 3; lookAhead++) {
							if (currentPosition + lookAhead == source.length)
								break;
							if (source[currentPosition + lookAhead] == '\n')
								break;
							if (source[currentPosition + lookAhead] == '\'') {
								currentPosition += lookAhead + 1;
								break;
							}
						}
						throw new InvalidInputException(
								INVALID_CHARACTER_CONSTANT);
					}
					if (getNextChar('\\'))
						scanEscapeCharacter();
					else { // consume next character
						unicodeAsBackSlash = false;
						if (((currentCharacter = source[currentPosition++]) == '\\')
								&& (source[currentPosition] == 'u')) {
							getNextUnicodeChar();
						} else {
							if (withoutUnicodePtr != 0) {
								withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
							}
						}
					}
					if (getNextChar('\''))
						return TokenNameCharacterLiteral;
					// relocate if finding another quote fairly close: thus
					// unicode '/u000D' will be fully consumed
					for (int lookAhead = 0; lookAhead < 20; lookAhead++) {
						if (currentPosition + lookAhead == source.length)
							break;
						if (source[currentPosition + lookAhead] == '\n')
							break;
						if (source[currentPosition + lookAhead] == '\'') {
							currentPosition += lookAhead + 1;
							break;
						}
					}
					throw new InvalidInputException(INVALID_CHARACTER_CONSTANT);
				case '"':
					try {
						// consume next character
						unicodeAsBackSlash = false;
						if (((currentCharacter = source[currentPosition++]) == '\\')
								&& (source[currentPosition] == 'u')) {
							getNextUnicodeChar();
						} else {
							if (withoutUnicodePtr != 0) {
								withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
							}
						}
						while (currentCharacter != '"') {
							/**** \r and \n are not valid in string literals ****/
							if ((currentCharacter == '\n')
									|| (currentCharacter == '\r')) {
								// relocate if finding another quote fairly
								// close: thus unicode '/u000D' will be fully
								// consumed
								for (int lookAhead = 0; lookAhead < 50; lookAhead++) {
									if (currentPosition + lookAhead == source.length)
										break;
									if (source[currentPosition + lookAhead] == '\n')
										break;
									if (source[currentPosition + lookAhead] == '\"') {
										currentPosition += lookAhead + 1;
										break;
									}
								}
								throw new InvalidInputException(
										INVALID_CHAR_IN_STRING);
							}
							if (currentCharacter == '\\') {
								int escapeSize = currentPosition;
								boolean backSlashAsUnicodeInString = unicodeAsBackSlash;
								// scanEscapeCharacter make a side effect on
								// this value and we need the previous value few
								// lines down this one
								scanEscapeCharacter();
								escapeSize = currentPosition - escapeSize;
								if (withoutUnicodePtr == 0) {
									// buffer all the entries that have been
									// left aside....
									withoutUnicodePtr = currentPosition
											- escapeSize - 1 - startPosition;
									System.arraycopy(source, startPosition,
											withoutUnicodeBuffer, 1,
											withoutUnicodePtr);
									withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
								} else { // overwrite the / in the buffer
									withoutUnicodeBuffer[withoutUnicodePtr] = currentCharacter;
									if (backSlashAsUnicodeInString) { // there
																		// are
																		// TWO \
																		// in
																		// the
																		// stream
																		// where
																		// only
																		// one
																		// is
																		// correct
										withoutUnicodePtr--;
									}
								}
							}
							// consume next character
							unicodeAsBackSlash = false;
							if (((currentCharacter = source[currentPosition++]) == '\\')
									&& (source[currentPosition] == 'u')) {
								getNextUnicodeChar();
							} else {
								if (withoutUnicodePtr != 0) {
									withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
								}
							}
						}
					} catch (IndexOutOfBoundsException e) {
						throw new InvalidInputException(UNTERMINATED_STRING);
					} catch (InvalidInputException e) {
						if (e.getMessage().equals(INVALID_ESCAPE)) {
							// relocate if finding another quote fairly close:
							// thus unicode '/u000D' will be fully consumed
							for (int lookAhead = 0; lookAhead < 50; lookAhead++) {
								if (currentPosition + lookAhead == source.length)
									break;
								if (source[currentPosition + lookAhead] == '\n')
									break;
								if (source[currentPosition + lookAhead] == '\"') {
									currentPosition += lookAhead + 1;
									break;
								}
							}
						}
						throw e;
					}// rethrow
					if (startPosition <= cursorLocation
							&& cursorLocation <= currentPosition - 1) {
						throw new InvalidCursorLocation(
								InvalidCursorLocation.NO_COMPLETION_INSIDE_STRING);
					}
					return TokenNameStringLiteral;
				case '/': {
					int test;
					if ((test = getNextChar('/', '*')) == 0) { // line comment
						try { // get the next char
							if (((currentCharacter = source[currentPosition++]) == '\\')
									&& (source[currentPosition] == 'u')) {
								// -------------unicode traitement ------------
								int c1 = 0, c2 = 0, c3 = 0, c4 = 0;
								currentPosition++;
								while (source[currentPosition] == 'u') {
									currentPosition++;
								}
								if ((c1 = Character
										.getNumericValue(source[currentPosition++])) > 15
										|| c1 < 0
										|| (c2 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c2 < 0
										|| (c3 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c3 < 0
										|| (c4 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c4 < 0) {
									throw new InvalidInputException(
											INVALID_UNICODE_ESCAPE);
								} else {
									currentCharacter = (char) (((c1 * 16 + c2) * 16 + c3) * 16 + c4);
								}
							}
							// handle the \\u case manually into comment
							if (currentCharacter == '\\') {
								if (source[currentPosition] == '\\')
									currentPosition++;
							} // jump over the \\
							while (currentCharacter != '\r'
									&& currentCharacter != '\n') {
								// get the next char
								if (((currentCharacter = source[currentPosition++]) == '\\')
										&& (source[currentPosition] == 'u')) {
									// -------------unicode traitement
									// ------------
									int c1 = 0, c2 = 0, c3 = 0, c4 = 0;
									currentPosition++;
									while (source[currentPosition] == 'u') {
										currentPosition++;
									}
									if ((c1 = Character
											.getNumericValue(source[currentPosition++])) > 15
											|| c1 < 0
											|| (c2 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c2 < 0
											|| (c3 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c3 < 0
											|| (c4 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c4 < 0) {
										throw new InvalidInputException(
												INVALID_UNICODE_ESCAPE);
									} else {
										currentCharacter = (char) (((c1 * 16 + c2) * 16 + c3) * 16 + c4);
									}
								}
								// handle the \\u case manually into comment
								if (currentCharacter == '\\') {
									if (source[currentPosition] == '\\')
										currentPosition++;
								}
							}// jump over the \\
							recordComment(false);
							if (startPosition <= cursorLocation
									&& cursorLocation < currentPosition - 1) {
								throw new InvalidCursorLocation(
										InvalidCursorLocation.NO_COMPLETION_INSIDE_COMMENT);
							}
							if (recordLineSeparator
									&& ((currentCharacter == '\r') || (currentCharacter == '\n')))
								pushLineSeparator();
							if (tokenizeComments) {
								currentPosition--; // reset one character behind
								return TokenNameCOMMENT_LINE;
							}
						} catch (IndexOutOfBoundsException e) { // an eof will
																// them be
																// generated
							if (tokenizeComments) {
								currentPosition--; // reset one character behind
								return TokenNameCOMMENT_LINE;
							}
						}
						break;
					}
					if (test > 0) { // traditional and annotation comment
						boolean isJavadoc = false, star = false;
						// consume next character
						unicodeAsBackSlash = false;
						if (((currentCharacter = source[currentPosition++]) == '\\')
								&& (source[currentPosition] == 'u')) {
							getNextUnicodeChar();
						} else {
							if (withoutUnicodePtr != 0) {
								withoutUnicodeBuffer[++withoutUnicodePtr] = currentCharacter;
							}
						}
						if (currentCharacter == '*') {
							isJavadoc = true;
							star = true;
						}
						if (recordLineSeparator
								&& ((currentCharacter == '\r') || (currentCharacter == '\n')))
							pushLineSeparator();
						try { // get the next char
							if (((currentCharacter = source[currentPosition++]) == '\\')
									&& (source[currentPosition] == 'u')) {
								// -------------unicode traitement ------------
								int c1 = 0, c2 = 0, c3 = 0, c4 = 0;
								currentPosition++;
								while (source[currentPosition] == 'u') {
									currentPosition++;
								}
								if ((c1 = Character
										.getNumericValue(source[currentPosition++])) > 15
										|| c1 < 0
										|| (c2 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c2 < 0
										|| (c3 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c3 < 0
										|| (c4 = Character
												.getNumericValue(source[currentPosition++])) > 15
										|| c4 < 0) {
									throw new InvalidInputException(
											INVALID_UNICODE_ESCAPE);
								} else {
									currentCharacter = (char) (((c1 * 16 + c2) * 16 + c3) * 16 + c4);
								}
							}
							// handle the \\u case manually into comment
							if (currentCharacter == '\\') {
								if (source[currentPosition] == '\\')
									currentPosition++;
							} // jump over the \\
								// empty comment is not a javadoc /**/
							if (currentCharacter == '/') {
								isJavadoc = false;
							}
							// loop until end of comment */
							while ((currentCharacter != '/') || (!star)) {
								if (recordLineSeparator
										&& ((currentCharacter == '\r') || (currentCharacter == '\n')))
									pushLineSeparator();
								star = currentCharacter == '*';
								// get next char
								if (((currentCharacter = source[currentPosition++]) == '\\')
										&& (source[currentPosition] == 'u')) {
									// -------------unicode traitement
									// ------------
									int c1 = 0, c2 = 0, c3 = 0, c4 = 0;
									currentPosition++;
									while (source[currentPosition] == 'u') {
										currentPosition++;
									}
									if ((c1 = Character
											.getNumericValue(source[currentPosition++])) > 15
											|| c1 < 0
											|| (c2 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c2 < 0
											|| (c3 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c3 < 0
											|| (c4 = Character
													.getNumericValue(source[currentPosition++])) > 15
											|| c4 < 0) {
										throw new InvalidInputException(
												INVALID_UNICODE_ESCAPE);
									} else {
										currentCharacter = (char) (((c1 * 16 + c2) * 16 + c3) * 16 + c4);
									}
								}
								// handle the \\u case manually into comment
								if (currentCharacter == '\\') {
									if (source[currentPosition] == '\\')
										currentPosition++;
								}
							}// jump over the \\
							recordComment(isJavadoc);
							if (startPosition <= cursorLocation
									&& cursorLocation < currentPosition - 1) {
								throw new InvalidCursorLocation(
										InvalidCursorLocation.NO_COMPLETION_INSIDE_COMMENT);
							}
							if (tokenizeComments) {
								if (isJavadoc)
									return TokenNameCOMMENT_JAVADOC;
								return TokenNameCOMMENT_BLOCK;
							}
						} catch (IndexOutOfBoundsException e) {
							throw new InvalidInputException(
									UNTERMINATED_COMMENT);
						}
						break;
					}
					if (getNextChar('='))
						return TokenNameDIVIDE_EQUAL;
					return TokenNameDIVIDE;
				}
				case '\u001a':
					if (atEnd())
						return TokenNameEOF;
					// the atEnd may not be <currentPosition == source.length>
					// if source is only some part of a real (external) stream
					throw new InvalidInputException("Ctrl-Z"); //$NON-NLS-1$
				default:
					if (Character.isJavaIdentifierStart(currentCharacter))
						return scanIdentifierOrKeyword();
					if (Character.isDigit(currentCharacter))
						return scanNumber(false);
					return TokenNameERROR;
				}
			}
		} // -----------------end switch while try--------------------
		catch (IndexOutOfBoundsException e) {
		}
		/* might be completing at very end of file (e.g. behind a dot) */
		if (completionIdentifier == null && startPosition == cursorLocation + 1) {
			currentPosition = startPosition; // for being detected as empty free
												// identifier
			return TokenNameIdentifier;
		}
		return TokenNameEOF;
	}
}