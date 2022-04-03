package org.jetbrains.java.decompiler.ast.example;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.ast.LeafNode;
import org.jetbrains.java.decompiler.ast.java.JavaNodeRoles;

import java.util.function.Consumer;

/**
 * Example transformer that converts decompiled Java to Cyclic.
 * <p/> Removes `throws` lists, `default` modifiers, and converts annotation definitions.
 */
public class CyclicTransformer implements Consumer<AstNode> {

  @Override
  public void accept(AstNode astNode) {
    if (astNode.hasRole(JavaNodeRoles.THROWS_LIST)) {
      astNode.remove();
    } else if (astNode.hasRole(JavaNodeRoles.MODIFIER) && astNode.text().equals("default")) {
      astNode.remove();
    } else if (astNode.hasRole(JavaNodeRoles.CLASS_KIND) && astNode.text().equals("@interface")) {
      ((LeafNode) astNode).setText("annotation");
    } else if (astNode.hasRole(JavaNodeRoles.ANNOTATION_DEFAULT_VALUE)) {
      // replace the "default" with "->"
      ((LeafNode) astNode.firstChild()).setText("->");
    }
  }
}