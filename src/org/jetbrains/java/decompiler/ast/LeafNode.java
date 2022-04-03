package org.jetbrains.java.decompiler.ast;

import java.util.Collections;
import java.util.List;

public class LeafNode implements AstNode {

  private AstNode parent;
  private String text;

  public LeafNode(AstNode parent, String text) {
    this.parent = parent;
    this.text = text;
    if(parent != null) {
      parent.addChild(this);
    }
  }

  public AstNode parent() {
    return parent;
  }

  public List<AstNode> children() {
    return Collections.emptyList();
  }

  public void setParent(AstNode newParent) {
    parent = newParent;
  }

  public String text() {
    return text;
  }

  public boolean requiresSpace() {
    // default implementation just checks if there's any alphanumeric characters
    for (char c : text().toCharArray()) {
      if (Character.isAlphabetic(c) || Character.isDigit(c)) {
        return true;
      }
    }
    return false;
  }

  public void addChild(AstNode child) {
    throw new UnsupportedOperationException();
  }

  public void removeChild(AstNode child) {
    throw new UnsupportedOperationException();
  }

  public void replaceChild(AstNode oldChild, AstNode newChild) {
    throw new UnsupportedOperationException();
  }

  public LeafNode setText(String text) {
    this.text = text;
    return this;
  }
}