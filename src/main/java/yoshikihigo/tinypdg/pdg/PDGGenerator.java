package yoshikihigo.tinypdg.pdg;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.jdt.core.dom.CompilationUnit;
import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.scorpio.NormalizedText;

public class PDGGenerator {

  /**
   * 第一引数はJavaファイルを含むディレクトリ 第二引数は生成したPDF情報を吐き出すためのディレクトリ
   * 
   * @param args
   */
  public static void main(final String[] args) {

    final Path srcPath = Paths.get(args[0]);
    final Path outputPath = Paths.get(args[1]);

    try {
      Files.createDirectories(outputPath);
    } catch (final IOException e) {
      System.err.println("failed to create \"" + outputPath.toString() + "\"");
      System.err.println(e.getMessage());
      System.exit(0);
    }

    final Set<Path> srcPaths = collectJavaFiles(srcPath);
    final int totalNumberOfFiles = srcPaths.size();
    final AtomicInteger index = new AtomicInteger(1);
    srcPaths.parallelStream()
        .forEach(path -> {
          final Path relativePath = srcPath.relativize(path);
          System.err.println(Integer.toString(index.getAndIncrement()) + "/"
              + Integer.toString(totalNumberOfFiles) + " : " + relativePath.toString());
          final List<MethodInfo> methods = getMethods(path);
          final List<PDG> pdgs = getPDGs(methods);
          System.out.print("writting ... ");
          for (final PDG pdg : pdgs) {
            final String fileName = createPDGFileName(relativePath, pdg);
            final List<String> lines = generatePDGData(pdg);
            final Path outputFilePath = outputPath.resolve(fileName);
            try {
              Files.write(outputFilePath, lines);
            } catch (final IOException e) {
              System.err.println("failed to write \"" + outputFilePath.toString());
              continue;
            }
          }
          System.out.println("done.");
        });
  }

  private static Set<Path> collectJavaFiles(final Path srcPath) {

    try {
      return Files.walk(srcPath)
          .parallel()
          .filter(p -> isJavaFile(p))
          .collect(Collectors.toSet());
    } catch (final IOException e) {
      System.err.println("failed to access \"" + srcPath.toString() + "\"");
      System.err.println(e.getMessage());
      System.exit(0);
    }

    return Collections.emptySet();
  }

  private static boolean isJavaFile(final Path path) {
    return Files.isRegularFile(path) && path.toString()
        .endsWith(".java");
  }

  private static List<MethodInfo> getMethods(final Path srcPath) {
    final File file = new File(srcPath.toString());
    final CompilationUnit unit = TinyPDGASTVisitor.createAST(file);
    final List<MethodInfo> methods = new ArrayList<MethodInfo>();
    final TinyPDGASTVisitor visitor = new TinyPDGASTVisitor(file.getAbsolutePath(), unit, methods);
    unit.accept(visitor);
    return methods;
  }


  private static List<PDG> getPDGs(final List<MethodInfo> methods) {
    final StopWatch stopwatch = new StopWatch();
    stopwatch.start();
    final List<PDG> pdgs = new ArrayList<>();
    final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
    final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
    for (final MethodInfo method : methods) {
      System.out.print(method.name + " : " + method.startLine + "--" + method.endLine + " ... ");
      stopwatch.split();
      final PDG pdg = new PDG(method, pdgNodeFactory, cfgNodeFactory, true, true, false);
      pdg.build();
      if (pdg.getAllNodes()
          .size() < 5) {
        System.out.println(" omitted.");
        continue;
      }
      pdgs.add(pdg);
      final long milliSecondTime = stopwatch.getSplitTime();
      System.out.println("done " + Long.toString(milliSecondTime));
    }
    return pdgs;
  }

  private static String createPDGFileName(final Path relativePath, final PDG pdg) {
    return relativePath.toString()
        .replace('\\', '_') + "_" + pdg.unit.name + "_" + pdg.unit.startLine + "_"
        + pdg.unit.endLine;
  }

  private static List<String> generatePDGData(final PDG pdg) {
    final List<String> lines = new ArrayList<>();
    for (final PDGEdge edge : pdg.getAllEdges()) {
      final String fromNodeText = new NormalizedText(edge.fromNode.core).getText();
      final String toNodeText = new NormalizedText(edge.toNode.core).getText();
      final String fromNodeMD5 = getMD5(fromNodeText);
      final String toNodeMD5 = getMD5(toNodeText);
      lines.add(fromNodeMD5 + ", " + toNodeMD5 + ", " + edge.type.toString());
    }
    return lines;
  }

  private static String getMD5(final String text) {
    try {
      final MessageDigest md5 = MessageDigest.getInstance("MD5");
      final byte[] bytes = md5.digest(text.getBytes());
      final BigInteger value = new BigInteger(1, bytes);
      final String md5Text = new String(String.format("%032x", value));
      return md5Text;
    } catch (final Exception e) {
      return "0000000000";
    }
  }
}
