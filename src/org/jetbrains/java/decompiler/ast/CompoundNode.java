package org.jetbrains.java.decompiler.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompoundNode implements AstNode {

  private final List<AstNode> nodes = new ArrayList<>();
  private AstNode parent;
  private String role;

  public CompoundNode(AstNode parent) {
    this.parent = parent;
    if (parent != null) {
      parent.addChild(this);
    }
  }

  public AstNode parent() {
    return parent;
  }

  public List<AstNode> children() {
    return Collections.unmodifiableList(nodes);
  }

  public void setParent(AstNode newParent) {
    parent = newParent;
  }

  public String text() {
    StringBuilder acc = new StringBuilder();
    for (AstNode node : nodes) {
      if (node.requiresSpace() && acc.length() > 0) {
        acc.append(" ");
      }
      acc.append(node.text());
    }
    return acc.toString();
  }

  public String role() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public boolean requiresSpace() {
    return true;
  }

  public void addChild(AstNode child) {
    nodes.add(child);
    child.setParent(this);
  }

  public void removeChild(AstNode child) {
    nodes.remove(child);
  }

  public void replaceChild(AstNode oldChild, AstNode newChild) {
    nodes.set(nodes.indexOf(oldChild), newChild);
  }
}