package org.jetbrains.java.decompiler.ast.java;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.ast.LeafNode;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;

public class ExprentNode extends LeafNode {

  private final Exprent expression;

  public ExprentNode(AstNode parent, Exprent expression) {
    super(parent, "<ignored>");
    this.expression = expression;
  }

  @Override
  public String text() {
    return expression.toJava().toString();
  }

  @Override
  public LeafNode setText(String text) {
    throw new UnsupportedOperationException();
  }
}