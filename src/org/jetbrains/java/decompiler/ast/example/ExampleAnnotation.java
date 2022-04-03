package org.jetbrains.java.decompiler.ast.example;

@Deprecated
public @interface ExampleAnnotation {

  String value() default "13";

  @Deprecated()
  int o() default 0;
  int p();
}