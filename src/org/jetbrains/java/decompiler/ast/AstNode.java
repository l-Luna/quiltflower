package org.jetbrains.java.decompiler.ast;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * An AST node in decompiled code that may be transformed further or written as output.
 */
public abstract class AstNode {

  public abstract AstNode parent();

  public abstract /*immutable*/ List<AstNode> children();

  public abstract void setParent(AstNode newParent);

  public abstract String text();

  /**
   * Whether this node requires space according to language syntax, e.g. for keywords.
   * How much space is *actually* present depends on the formatter, but this guarantees at least one.
   */
  public abstract boolean requiresSpace();

  // optional operations

  public abstract void addChild(AstNode child);

  public abstract void removeChild(AstNode child);

  public abstract void replaceChild(AstNode oldChild, AstNode newChild);

  // default implementations

  public void replace(AstNode with) {
    AstNode parent = parent();
    if (parent != null) {
      parent.replaceChild(this, with);
    }
  }

  public void accept(Consumer<AstNode> visitor) {
    visitor.accept(this);
    for (AstNode child : children()) {
      child.accept(visitor);
    }
  }

  public void transform(UnaryOperator<AstNode> visitor) {
    AstNode newNode = visitor.apply(this);
    if (newNode != this) {
      replace(newNode);
    } else for (AstNode child : children()) {
      child.transform(visitor);
    }
  }

  public AstNode firstChild() {
    return children().isEmpty() ? null : children().get(0);
  }

  public AstNode lastChild() {
    return children().isEmpty() ? null : children().get(children().size() - 1);
  }
}