package org.jetbrains.java.decompiler.langs.cyclic;

import org.jetbrains.java.decompiler.langs.Languages;
import org.jetbrains.java.decompiler.langs.java.JavaAstBuilder;
import org.jetbrains.java.decompiler.langs.AstBuilder;
import org.jetbrains.java.decompiler.langs.Language;
import org.jetbrains.java.decompiler.main.ClassWriter;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;

import java.util.Collections;
import java.util.List;

public class CyclicLanguage implements Language {

  public static final String ANNOTATION_NAME = "cyclic/lang/CyclicFile";

  public boolean appliesTo(ClassNode clazz) {
    return ClassWriter.hasAnnotation(clazz.classStruct, ANNOTATION_NAME);
  }

  public AstBuilder getBuilder() {
    return new AstBuilder.WithTransformations(new JavaAstBuilder(), this, new CyclicTransformer());
  }

  @Override
  public String cmdName() {
    return "Cyclic";
  }

  @Override
  public String fileExtension() {
    return "cyc";
  }

  @Override
  public List<Language> alsoUseContributorsFrom() {
    return Collections.singletonList(Languages.JAVA_LANGUAGE);
  }
}