package org.jetbrains.java.decompiler.langs;

import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;

public interface Language {

  boolean appliesTo(ClassNode clazz);

  AstBuilder getBuilder();

  // TODO: resugarings, member hiding, classfile attribute loading...
}