package org.jetbrains.java.decompiler.langs.cyclic;

import org.jetbrains.java.decompiler.langs.java.JavaAstBuilder;
import org.jetbrains.java.decompiler.langs.AstBuilder;
import org.jetbrains.java.decompiler.langs.Language;
import org.jetbrains.java.decompiler.main.ClassWriter;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;

public class CyclicLanguage implements Language {

  public static final String ANNOTATION_NAME = "cyclic/lang/CyclicFile";

  public boolean appliesTo(ClassNode clazz) {
    return ClassWriter.hasAnnotation(clazz.classStruct, ANNOTATION_NAME);
  }

  public AstBuilder getBuilder() {
    return new AstBuilder.WithTransformations(new JavaAstBuilder(), new CyclicTransformer());
  }
}