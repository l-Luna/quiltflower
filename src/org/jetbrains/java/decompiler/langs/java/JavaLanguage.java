package org.jetbrains.java.decompiler.langs.java;

import org.jetbrains.java.decompiler.langs.AstBuilder;
import org.jetbrains.java.decompiler.langs.Language;
import org.jetbrains.java.decompiler.main.ClassesProcessor;

public class JavaLanguage implements Language {

  public boolean appliesTo(ClassesProcessor.ClassNode clazz) {
    return true;
  }

  public AstBuilder getBuilder() {
    return new JavaAstBuilder();
  }
}