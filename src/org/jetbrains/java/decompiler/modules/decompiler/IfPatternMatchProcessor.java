package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent.FunctionType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles pattern matching for instanceof in statements.
 *
 * @author SuperCoder79
 */
public final class IfPatternMatchProcessor {

  // Finds all assignments and their associated variables in a statement's predecessors.
  // FIXME: This isn't working as it should! it should be traversing the predecessor tree!
  // TODO: move
  public static void findVarsInPredecessors(List<VarVersionPair> vvs, Statement root) {
    for (StatEdge pred : root.getAllPredecessorEdges()) {
      Statement stat = pred.getSource();

      if (stat.getExprents() != null) {
        for (Exprent exprent : stat.getExprents()) {

          // Check for assignment exprents
          if (exprent instanceof AssignmentExprent) {
            AssignmentExprent assignment = (AssignmentExprent) exprent;

            // If the left type of the assignment is a variable, store it's var info
            if (assignment.getLeft() instanceof VarExprent) {
              vvs.add(((VarExprent) assignment.getLeft()).getVarVersionPair());
            }
          }
        }
      }
    }
  }
}
