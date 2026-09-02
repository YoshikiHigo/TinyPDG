TinyPDG
=======

[![build](https://github.com/YoshikiHigo/TinyPDG/actions/workflows/build.yml/badge.svg)](https://github.com/YoshikiHigo/TinyPDG/actions/workflows/build.yml)

A library for building intraprocedural PDGs for Java programs.

TinyPDG parses Java source files with Eclipse JDT and builds, for each
method, a control flow graph (CFG) and a program dependence graph (PDG)
carrying control, data and execution dependences. It also ships a few
command line tools built on top of that.

Requirements
------------

* JDK 25 or later. The build declares a Java 25 toolchain, so Gradle will
  download one if it is not already installed.
* No Gradle installation needed - use the wrapper.

Building
--------

    ./gradlew build

This compiles, runs the tests, and produces `build/libs/TinyPDG-0.1.0.jar`.
Compilation runs with `-Xlint:all` and the build is expected to stay
warning free.

Command line tools
------------------

Every tool takes `-d` for the target, which may be a single `.java` file
or a directory that is searched recursively. Source files are read as
UTF-8.

`-j <version>` sets the Java version assumed when parsing (`8`, `11`,
`17`, `21`, `25`, ...). It defaults to 25. Use it when analysing sources
that a newer compiler would reject.

### Writing graphs for Graphviz

    java -cp <classpath> yoshikihigo.tinypdg.graphviz.Writer \
        -d src/main/java -p pdg.dot -c cfg.dot

| Option | Meaning |
| --- | --- |
| `-d`, `--directory` | target file or directory (required) |
| `-p`, `--ProgramDependencyGraph` | write PDGs to this file |
| `-c`, `--ControlFlowGraph` | write CFGs to this file |
| `-j`, `--java-version` | Java version assumed for the sources |

At least one of `-p` and `-c` is needed for the run to produce anything.
Render the result with `dot -Tpdf pdg.dot -o pdg.pdf`.

### Detecting clones (Scorpio)

    java -cp <classpath> yoshikihigo.tinypdg.scorpio.Scorpio \
        -d src/main/java -o clonepairs.csv -s 10 -t 4

| Option | Meaning |
| --- | --- |
| `-d`, `--directory` | target file or directory (required) |
| `-o`, `--output` | output file for the detected clone pairs (required) |
| `-s`, `--size` | smallest graph to consider, in nodes (required) |
| `-t`, `--thread` | number of threads (default 1) |
| `-C`, `--control` | use control dependences, `on` or `off` (default on) |
| `-D`, `--data` | use data dependences, `on` or `off` (default on) |
| `-E`, `--execution` | use execution dependences, `on` or `off` (default on) |
| `-M`, `--merging` | merge equivalent adjacent nodes, `on` or `off` (default on) |
| `-j`, `--java-version` | Java version assumed for the sources |

Merging needs execution dependences, and is turned off automatically if
`-E off` is given.

### Collecting dependence frequencies

    java -cp <classpath> yoshikihigo.tinypdg.prelement.DependenceDistiller \
        -d src/main/java -b frequencies.db -s 5 -t 4

Walks the PDGs and records, in an SQLite database, how often each kind of
dependence connects each pair of normalized statements.

| Option | Meaning |
| --- | --- |
| `-d`, `--directory` | target file or directory (required) |
| `-b`, `--database` | SQLite database to write (required) |
| `-s`, `--size` | smallest graph to consider, in nodes (default 5) |
| `-t`, `--thread` | number of threads (default 1) |
| `-j`, `--java-version` | Java version assumed for the sources |

### Querying those frequencies

    java -cp <classpath> yoshikihigo.tinypdg.prelement.ElementPredictor \
        -b frequencies.db

Reads a statement from standard input and prints the statements most
often found to depend on it. An empty line ends the session.

Using it as a library
---------------------

```java
final List<MethodInfo> methods = JavaAstFactory.collectMethods(
        new File("src/main/java"), JavaAstFactory.DEFAULT_JAVA_VERSION);

for (final MethodInfo method : methods) {
    final PDG pdg = new PDG(method);
    pdg.build();

    for (final PDGEdge edge : pdg.getAllEdges()) {
        System.out.println(edge.fromNode.getText() + " -> "
                + edge.toNode.getText() + " [" + edge.type + "]");
    }
}
```

`new PDG(method)` builds all three kinds of dependence. To choose, pass a
`PDG.Dependences`:

```java
new PDG(method, new PDGNodeFactory(), new CFGNodeFactory(),
        new PDG.Dependences(false, true, false));   // data only
```

`PDGGeneration.buildInParallel` does the same across a thread pool. A CFG
alone is `new CFG(method, new CFGNodeFactory())`.

Failures are reported as `TinyPDGException`, an unchecked exception
carrying the original cause.

Tests
-----

    ./gradlew test

Most of the suite compares the CFG and PDG built for each sample under
`src/test/resources/samples` against a recorded text form under
`src/test/resources/golden`. After an intended change to the graphs,
regenerate them and read the diff before committing:

    ./gradlew test -Dtinypdg.golden=update
    git diff src/test/resources/golden

Layout
------

| Package | Contents |
| --- | --- |
| `ast` | parsing source into JDT ASTs and visiting them |
| `pe` | the program elements a graph is built over |
| `cfg` | control flow graphs |
| `pdg` | program dependence graphs |
| `graphviz` | writing graphs in dot format |
| `scorpio` | clone detection |
| `prelement` | dependence frequencies and the SQLite database behind them |
