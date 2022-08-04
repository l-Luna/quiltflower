/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.junit.jupiter.api.*;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.jetbrains.java.decompiler.DecompilerTestFixture.assertFilesEqual;
import static org.jetbrains.java.decompiler.DecompilerTestFixture.getContent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/*
 * Set individual test duration time limit to 60 seconds.
 * This will help us to test bugs hanging decompiler.
 */
@Timeout(60)
public abstract class SingleClassesTestBase {
  private TestSet currentTestSet;
  private final List<TestSet> testSets = new ArrayList<>();
  private final Set<String> classNames = new HashSet<>();
  
  protected String[] getDecompilerOptions() {
    return new String[] {};
  }

  protected abstract void registerAll();

  protected final void registerSet(String name, Runnable initializer, Object ...options) {
    currentTestSet = new TestSet(name, options);
    initializer.run();
    testSets.add(currentTestSet);
  }

  protected final void register(TestDefinition.Version version, String testClass, String... others) {
    register(version, testClass, false, others);
  }

  private void register(TestDefinition.Version version, String testClass, boolean failable, String... others) {
    if (classNames.contains(testClass)) {
      throw new AssertionFailedError("Registered same class twice! " + testClass);
    }
    classNames.add(testClass);

    List<String> othersList = new ArrayList<>(others.length);
    for (String other : others) othersList.add(getFullClassName(other));
    currentTestSet.testDefinitions.add(new TestDefinition(version, getFullClassName(testClass), othersList, failable));
  }

  @Deprecated
  // Temporary fix for inconsistent javac code generation
  protected final void registerFailable(TestDefinition.Version version, String testClass, String... others) {
    register(version, testClass, true, others);
  }

  protected final void registerRaw(TestDefinition.Version version, String testClass, String ...others) {
    currentTestSet.testDefinitions.add(new TestDefinition(version, testClass, Arrays.asList(others), false));
  }

  private static String getFullClassName(String className) {
    if (!className.contains("/")) return "pkg/" + className;
    return className;
  }

  @TestFactory
  @DisplayName("Test single classes")
  public Stream<DynamicContainer> testRegistered() {
    testSets.clear();
    registerAll();
    return testSets.stream()
      .filter(s -> !s.testDefinitions.isEmpty())
      .map(s -> DynamicContainer.dynamicContainer(s.name, s.getTests()));
  }

  protected static Path getClassFile(DecompilerTestFixture fixture, TestDefinition.Version version, String name) {
    return fixture.getTestDataDir().resolve("classes/" + version.directory + "/" + name + ".class");
  }

  protected static Path getReferenceFile(DecompilerTestFixture fixture, String testClass) {
    return fixture.getTestDataDir().resolve("results/" + testClass + ".dec");
  }

  private static List<Path> collectClasses(Path classFile) {
    List<Path> files = new ArrayList<>();
    if (Files.exists(classFile))
      files.add(classFile);

    Path parent = classFile.getParent();
    if (parent != null) {
      final Pattern pattern = Pattern.compile(classFile.getFileName().toString().replace(".class", "") + "(\\$.*|__init)\\.class");
      try {
        Files.list(parent).filter(p -> pattern.matcher(p.getFileName().toString()).matches()).forEach(files::add);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return files;
  }

  static class TestSet {
    public final String name;
    public final Object[] options;
    public final List<TestDefinition> testDefinitions = new ArrayList<>();

    public TestSet(String name, Object[] options) {
      this.name = name;
      this.options = options;
    }

    public Stream<DynamicNode> getTests() {
      Map<TestDefinition.Version, List<DynamicTest>> tests = new EnumMap<>(TestDefinition.Version.class);
      for (TestDefinition def : testDefinitions) {
        String name = def.testClass;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        Path classFile = def.getClassFile();
        Path ref = def.getReferenceFile();
        DynamicTest test = DynamicTest.dynamicTest(name, Files.exists(ref) ? ref.toUri() : classFile.toUri(), () -> {
          def.run(options);
        });
        tests.computeIfAbsent(def.version, k -> new ArrayList<>()).add(test);
      }
      Path baseDir = new DecompilerTestFixture().getTestDataDir().resolve("classes/");
      return tests.entrySet().stream().map(e -> {
        TestDefinition.Version version = e.getKey();
        return DynamicContainer.dynamicContainer(
          version.toString(),
          baseDir.resolve(version.directory).toUri(),
          e.getValue().stream()
        );
      });
    }
  }

  static class TestDefinition {
    public final Version version;
    public final String testClass;
    public final List<String> others;
    public final boolean failable;
    private final DecompilerTestFixture fixture = new DecompilerTestFixture();

    public TestDefinition(Version version, String testClass, List<String> others, boolean failable) {
      this.version = version;
      this.testClass = testClass;
      this.others = others;
      this.failable = failable;
    }

    public Path getClassFile() {
      return SingleClassesTestBase.getClassFile(fixture, version, testClass);
    }

    public Path getReferenceFile() {
      return SingleClassesTestBase.getReferenceFile(fixture, testClass);
    }

    public void run(Object[] options) throws IOException {
      fixture.setUp(options);
      ConsoleDecompiler decompiler = fixture.getDecompiler();
      Path classFile = getClassFile();
      boolean foundAnySource = Files.isRegularFile(classFile);
      for (Path file : collectClasses(classFile)) {
        decompiler.addSource(file.toFile());
        foundAnySource = true;
      }
      assertTrue(foundAnySource, classFile + " or other sources should exist");

      for (String companionFile : others) {
        Path companionClassFile = SingleClassesTestBase.getClassFile(fixture, version, companionFile);
        assertTrue(Files.isRegularFile(companionClassFile), companionFile + " should exist");
        for (Path file : collectClasses(companionClassFile)) {
          decompiler.addSource(file.toFile());
        }
      }

      decompiler.decompileContext();

      TextBuffer.checkLeaks();

      String testFileName = classFile.getFileName().toString();
      String testName = testFileName.substring(0, testFileName.length() - 6);
      Path decompiledFile = fixture.getTargetDir().resolve(testName + ".java");

      String decompiledContent;

      if (version == Version.SCALA) {
        // scala likes to generate "unrelated" classfiles for the majority of its functionality
        // tack those onto the end of the decompiled files
        decompiledContent = getContent(decompiledFile);
        for (String companionFile : others) {
          Path decompiledCompanion = fixture.getTargetDir().resolve(companionFile + ".java");
          // cut off any packages
          decompiledCompanion = fixture.getTargetDir().resolve(decompiledCompanion.getFileName());
          assertTrue(Files.isRegularFile(decompiledCompanion));

          decompiledContent += "\n\n" + "// Decompiled companion from " + companionFile + "\n" + getContent(decompiledCompanion);
        }
      } else if(version == Version.CLOJURE) {
        // I'm sorry
        decompiledContent = "";

        for (Path file : collectClasses(classFile)) {
          String suffix = file.getFileName().toString().replace(".class", "");
          Path decompiledSource = fixture.getTargetDir().resolve(suffix + ".java");
          assertTrue(Files.isRegularFile(decompiledSource));

          decompiledContent += "\n\n" + "// Decompiled from " + suffix + "\n" + getContent(decompiledSource);
        }
      } else {
        assertTrue(Files.isRegularFile(decompiledFile), decompiledFile + " should exist");
        decompiledContent = getContent(decompiledFile);
      }

      Path referenceFile = getReferenceFile();
      if (!Files.exists(referenceFile)) {
        try {
          Files.createDirectories(referenceFile.getParent());
          Files.write(referenceFile, StandardCharsets.UTF_8.encode(decompiledContent).array());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        //noinspection ConstantConditions
        assumeTrue(false, referenceFile.getFileName() + " was not present yet");
      } else {
        try {
          assertTrue(Files.isRegularFile(referenceFile));
          assertEquals(getContent(referenceFile), decompiledContent);
        } catch (AssertionFailedError e) {
          if (failable) {
            assumeTrue(false, referenceFile.getFileName() + " failed but was ignored");
          } else {
            throw e;
          }
        }
      }
      fixture.tearDown();
    }

    enum Version {
      CUSTOM("custom", "Custom"),
      JAVA_8(8),
      JAVA_8_NODEBUG(8, "nodebug", "No Debug Info"),
      JAVA_9(9),
      JAVA_11(11),
      JAVA_16(16),
      JAVA_16_PREVIEW(16, "preview", "Preview"),
      JAVA_16_NODEBUG(16, "nodebug", "No Debug Info"),
      JAVA_17(17),
      JAVA_17_PREVIEW(17, "preview", "Preview"),
      GROOVY("groovy", "Groovy"),
      KOTLIN("kt", "Kotlin"),
      SCALA("scala", "Scala"),
      CLOJURE("clojure", "Clojure"),
      JASM("jasm", "Custom (jasm)"),
      ;

      public final String directory;
      public final String display;

      Version(String directory, String display) {
        this.directory = directory;
        this.display = display;
      }

      Version(int javaVersion) {
        this(javaVersion, "", "");
      }

      Version(int javaVersion, String suffix, String display) {
        this("java" + javaVersion + suffix, "Java " + javaVersion + (!display.isEmpty() ? " " + display : ""));
      }

      @Override
      public String toString() {
        return display;
      }
    }
  }
}