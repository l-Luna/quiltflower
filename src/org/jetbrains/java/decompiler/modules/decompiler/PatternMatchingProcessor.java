package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class PatternMatchingProcessor {

  public static boolean reduce(RootStatement root) {
    boolean res = reduce(root, root);

    if (res) {
      SequenceHelper.condenseSequences(root);
    }

    return res;
  }

  private static boolean reduce(Statement statement, RootStatement root) {
    boolean res = false;
    for (Statement stat : statement.getStats()) {
      if (reduce(stat, root)) {
        res = true;
      }
    }

    if (statement instanceof IfStatement) {
      res |= reduceIf((IfStatement) statement);
    }

    return res;
  }

  private static boolean reduceIf(IfStatement statement) {
    Optional<PatternExprent> pattern = matchTypePattern(statement);
    if (!pattern.isPresent())
      pattern = matchRecordPattern(statement);
    // ...

    if (pattern.isPresent()) {
      FunctionExprent oldWrapper = (FunctionExprent) statement.getHeadexprent().getCondition(); // (a instanceof b) != false
      FunctionExprent oldCond = (FunctionExprent) oldWrapper.getLstOperands().get(0);
      statement.getHeadexprent().setCondition(new FunctionExprent(FunctionExprent.FunctionType.INSTANCEOF, Arrays.asList(oldCond.getLstOperands().get(0), pattern.get()), null));
      statement.setPatternMatched(true);
      return true;
    }
    return false;
  }

  public static Optional<PatternExprent> matchTypePattern(IfStatement st) {
    /*
     Follows the pattern:
     if (it instanceof T) {
       T t = (T)it;
       rest of code...
     }
    */
    Exprent condition = st.getHeadexprent().getCondition();
    if (condition instanceof FunctionExprent) {
      FunctionExprent func = (FunctionExprent) condition;
      if (func.getFuncType() == FunctionExprent.FunctionType.NE
          && func.getLstOperands().get(0) instanceof FunctionExprent
          && ((FunctionExprent) func.getLstOperands().get(0)).getFuncType() == FunctionExprent.FunctionType.INSTANCEOF) {
        func = (FunctionExprent) func.getLstOperands().get(0);
      }
      if (func.getLstOperands().size() == 2 && func.getFuncType() == FunctionExprent.FunctionType.INSTANCEOF) {
        Exprent source = func.getLstOperands().get(0);
        Exprent target = func.getLstOperands().get(1);

        Statement head = st.getIfstat() == null ? null : st.getIfstat().getBasichead();
        boolean isHead = head != null && head != st.getIfstat();
        // me when IfPatternMatchProcessor
        if (head != null && head.getExprents() != null && (head.getExprents().size() > (isHead ? 0 : 1))) {
          Exprent first = head.getExprents().get(0);

          if (first instanceof AssignmentExprent) {
            Exprent left = first.getAllExprents().get(0);
            Exprent right = first.getAllExprents().get(1);

            if (right instanceof FunctionExprent) {
              if (((FunctionExprent) right).getFuncType() == FunctionExprent.FunctionType.CAST) {
                Exprent casted = right.getAllExprents().get(0);

                if (source.equals(casted)) {
                  if (left instanceof VarExprent && target.getExprType().equals(left.getExprType())) {
                    List<VarVersionPair> vvs = new ArrayList<>();
                    IfPatternMatchProcessor.findVarsInPredecessors(vvs, st.getIfstat());
                    VarVersionPair var = ((VarExprent) left).getVarVersionPair();
                    // already seen -> fail
                    for (VarVersionPair vv : vvs) {
                      if (var.var == vv.var) {
                        return Optional.empty();
                      }
                    }

                    head.getExprents().remove(0);
                    return Optional.of(new PatternExprent.TypePatternExprent((VarExprent) left, target.getExprType()));
                  }
                }
              }
            }
          }
        }
      }
    }
    return Optional.empty();
  }

  public static Optional<PatternExprent> matchRecordPattern(IfStatement st) {
    /*
     Follows the pattern:
     if (it instanceof T) {
       T t = (T)it; if named?
       C1 var_ = $proxy$c1((T)? it); for every component
       rest of code...
     }
    */
    return Optional.empty();
  }
}