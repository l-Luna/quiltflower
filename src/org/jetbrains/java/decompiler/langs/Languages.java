package org.jetbrains.java.decompiler.langs;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.langs.cyclic.CyclicLanguage;
import org.jetbrains.java.decompiler.langs.java.JavaLanguage;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;

import java.util.Arrays;
import java.util.List;

public final class Languages {

  public static final JavaLanguage JAVA_LANGUAGE = new JavaLanguage();

  // TODO: find languages from external jars (e.g. by service loader)
  // Java goes last to ensure other languages take precedence
  public static final List<Language> LANGUAGES = Arrays.asList(
    new CyclicLanguage(), JAVA_LANGUAGE
  );

  public static Language appropriateFor(ClassNode clazz) {
    for (Language language : LANGUAGES) {
      if (language.appliesTo(clazz)) {
        return language;
      }
    }
    return JAVA_LANGUAGE;
  }

  public static AstNode buildUsingInferredLanguage(ClassNode clazz) {
    return appropriateFor(clazz).getBuilder().build(clazz);
  }
}