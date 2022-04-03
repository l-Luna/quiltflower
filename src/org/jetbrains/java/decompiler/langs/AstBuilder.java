package org.jetbrains.java.decompiler.langs;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public interface AstBuilder {

  AstNode build(ClassNode node);

  class WithTransformations implements AstBuilder {

    private final AstBuilder astBuilder;
    private final List<Consumer<AstNode>> transformations;

    @SafeVarargs
    public WithTransformations(AstBuilder astBuilder, Consumer<AstNode>... transformations) {
      this.astBuilder = astBuilder;
      this.transformations = Arrays.asList(transformations);
    }

    public AstNode build(ClassNode node) {
      AstNode astNode = astBuilder.build(node);
      for (Consumer<AstNode> transformation : transformations) {
        astNode.accept(transformation);
      }
      return astNode;
    }
  }
}