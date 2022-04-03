package org.jetbrains.java.decompiler.langs.java;

import org.jetbrains.java.decompiler.langs.AstBuilder;
import org.jetbrains.java.decompiler.langs.Language;
import org.jetbrains.java.decompiler.langs.MemberHider;
import org.jetbrains.java.decompiler.main.ClassesProcessor;

import java.util.Collections;
import java.util.List;

public class JavaLanguage implements Language {

  public boolean appliesTo(ClassesProcessor.ClassNode clazz) {
    return false; // Languages#languageFor(ClassNode) special-cases this
  }

  public AstBuilder getBuilder() {
    return new JavaAstBuilder();
  }

  @Override
  public List<MemberHider> hiders() {
    return Collections.singletonList(new JavaMemberHider());
  }
}