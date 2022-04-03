package org.jetbrains.java.decompiler.langs;

import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;

import java.util.Collections;
import java.util.List;

public interface Language extends LanguageContributor {

  boolean appliesTo(ClassNode clazz);

  AstBuilder getBuilder();

  default boolean contributeTo(Language language) {
    return language == this;
  }

  default List<Language> alsoUseContributorsFrom() {
    return Collections.emptyList();
  }

  // TODO: resugarings, member hiding, classfile attribute loading...
}