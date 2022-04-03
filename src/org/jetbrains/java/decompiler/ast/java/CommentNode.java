package org.jetbrains.java.decompiler.ast.java;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.ast.LeafNode;

public class CommentNode extends LeafNode {

  private final boolean docComment;

  public CommentNode(AstNode parent, String text, boolean docComment) {
    super(parent, text);
    this.docComment = docComment;
  }

  public CommentNode(AstNode parent, String text) {
    this(parent, text, false);
  }

  @Override
  public boolean requiresSpace() {
    return false;
  }

  @Override
  public String text() {
    return (docComment ? "/** " : "/* ") + super.text() + " */";
  }
}