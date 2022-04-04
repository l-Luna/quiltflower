package org.jetbrains.java.decompiler.ast.java;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.ast.CompoundNode;
import org.jetbrains.java.decompiler.struct.StructClass;

public class ClassStructNode extends CompoundNode {

  public final StructClass data;

  public ClassStructNode(AstNode parent, StructClass data) {
    super(parent);
    this.data = data;
  }
}