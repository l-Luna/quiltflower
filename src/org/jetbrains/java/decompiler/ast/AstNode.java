package org.jetbrains.java.decompiler.ast;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * An AST node in decompiled code that may be transformed further or written as output.
 */
public interface AstNode {

  AstNode parent();

  /*immutable*/ List<AstNode> children();

  void setParent(AstNode newParent);

  String text();

  String role();

  void setRole(String role);

  /**
   * Whether this node requires space according to language syntax, e.g. for keywords.
   * How much space is *actually* present depends on the formatter, but this guarantees at least one.
   */
  boolean requiresSpace();

  // optional operations

  void addChild(AstNode child);

  void removeChild(AstNode child);

  void replaceChild(AstNode oldChild, AstNode newChild);

  // default implementations

  default void replace(AstNode with) {
    AstNode parent = parent();
    if (parent != null) {
      parent.replaceChild(this, with);
    }
  }

  default void remove() {
    AstNode parent = parent();
    if (parent != null) {
      parent.removeChild(this);
    }
  }

  default void accept(Consumer<AstNode> visitor) {
    visitor.accept(this);
    for (AstNode child : children()) {
      child.accept(visitor);
    }
  }

  default void transform(UnaryOperator<AstNode> visitor) {
    AstNode newNode = visitor.apply(this);
    if (newNode != this) {
      replace(newNode);
    } else for (AstNode child : children()) {
      child.transform(visitor);
    }
  }

  default AstNode firstChild() {
    return children().isEmpty() ? null : children().get(0);
  }

  default AstNode lastChild() {
    return children().isEmpty() ? null : children().get(children().size() - 1);
  }

  default boolean hasRole(String role) {
    return role() != null && role().equals(role);
  }
}