package org.jetbrains.java.decompiler.langs.cyclic;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.ast.LeafNode;
import org.jetbrains.java.decompiler.ast.java.ClassStructNode;
import org.jetbrains.java.decompiler.ast.java.JavaNodeRoles;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;

import java.util.function.Consumer;

/**
 * Example transformer that converts decompiled Java to Cyclic.
 */
public class CyclicTransformer implements Consumer<AstNode> {

  // TODO:
  //  - convert annotation methods with bodies?
  //  - convert enums and singles
  public void accept(AstNode astNode) {
    if (astNode.hasRole(JavaNodeRoles.THROWS_LIST)) {
      astNode.remove();
    } else if (astNode.hasRole(JavaNodeRoles.MODIFIER) && astNode.text().equals("default")) {
      astNode.remove();
    } else if (astNode.hasRole(JavaNodeRoles.CLASS_KIND)) {
      if (astNode.parent() instanceof ClassStructNode) {
        ClassStructNode struct = (ClassStructNode) astNode.parent();
        // TODO: make this not suck
        if (struct.data.hasAttribute(new StructGeneralAttribute.Key<SingleTypeAttrParser.IsSingleTypeStruct>("Cyclic:IsSingle"))) {
          ((LeafNode) astNode).setText("single");
        }
      }
      if (astNode.text().equals("@interface")) {
        ((LeafNode) astNode).setText("annotation");
      }
    } else if (astNode.hasRole(JavaNodeRoles.ANNOTATION_DEFAULT_VALUE)) {
      // replace the "default" with "->"
      ((LeafNode) astNode.firstChild()).setText("->");
    } else if (astNode.hasRole(JavaNodeRoles.ANNOTATION_DEFAULT_VALUE)) {
      // replace the "default" with "->"
      ((LeafNode) astNode.firstChild()).setText("->");
    }
  }
}