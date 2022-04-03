package org.jetbrains.java.decompiler.ast.java;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.ast.LeafNode;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.struct.gen.VarType;

public class TypeNode extends LeafNode {

  private final VarType type;

  public TypeNode(AstNode parent, VarType type) {
    super(parent, "<ignored>");
    this.type = type;
  }

  @Override
  public String text() {
    return ExprProcessor.getCastTypeName(type);
  }

  @Override
  public LeafNode setText(String text) {
    throw new UnsupportedOperationException();
  }
}