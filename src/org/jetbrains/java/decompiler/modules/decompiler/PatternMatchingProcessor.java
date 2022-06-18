package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.Pair;

import java.util.*;
import java.util.function.BiConsumer;

public final class PatternMatchingProcessor {

  public static boolean reduce(RootStatement root, boolean records) {
    boolean res = reduceRec(root, root, records);

    if (res) {
      SequenceHelper.condenseSequences(root);
    }

    return res;
  }

  private static boolean reduceRec(Statement statement, RootStatement root, boolean records) {
    boolean res = false;
    for (Statement stat : statement.getStats()) {
      if (reduceRec(stat, root, records)) {
        res = true;
      }
    }

    if (statement instanceof IfStatement) {
      res |= reduceIf((IfStatement) statement, root, records);
    }

    return res;
  }

  private static boolean reduceIf(IfStatement statement, RootStatement root, boolean records) {
    Optional<PatternExprent> pattern = records ? matchRecordPattern(statement, root) : matchTypePattern(statement);

    if (pattern.isPresent()) {
      FunctionExprent oldCond = (FunctionExprent) statement.getHeadexprent().getCondition(); // (a instanceof b) != false
      if (!(oldCond.getLstOperands().get(0) instanceof VarExprent)) {
        oldCond = (FunctionExprent) oldCond.getLstOperands().get(0);
      }
      statement.getHeadexprent().setCondition(new FunctionExprent(FunctionExprent.FunctionType.INSTANCEOF, Arrays.asList(oldCond.getLstOperands().get(0), pattern.get()), null));
      statement.setPatternMatched(true);
      return true;
    }
    return false;
  }

  public static Optional<PatternExprent> matchTypePattern(IfStatement st) {
    /*
     Follows the pattern:
     if (it instanceof T != false) {
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

  public static Optional<PatternExprent> matchRecordPattern(IfStatement st, RootStatement root) {
    /*
     Follows the pattern:
     if (it instanceof T != false) { // or (it != null)
       T t = (T)it; if named?
       { // for every component
         Top top = $proxy$cN((T)? it);
         CN varN = safeCast(top);
       }
       rest of code...
     }
     Where:
      - `Top` is the top type of that kind (Object for all object components, int for all int components)
      - `$proxy$cN` is a generated wrapper method that wraps accessor calls in a try/catch for MatchExceptions
      - `safeCast` is a safe conversion for that kind (type pattern for objects, Integer.valueOf for int-likes...)
      The same `top` variable is reused as much as possible.
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

        if (isRecordClass(target.getExprType())) {
          Statement head = st.getIfstat().getBasichead();
          // TODO!: records with multiple top types! how does that get arranged?
          if (head.getExprents() != null && head.getExprents().size() > 0) {
            Exprent first = head.getExprents().get(0);
            if (first instanceof AssignmentExprent) {
              VarExprent var = (VarExprent) ((AssignmentExprent) first).getLeft();

              List<Pair<Statement, Exprent>> references = new ArrayList<>();
              findExprents(root, var, (st1, expr) -> references.add(Pair.of(st1, expr)));
              // don't check trailing uses of the top variable
              for (int i = references.size() - 1; i >= 0; i--) {
                Pair<Statement, Exprent> ref = references.get(i);
                if (ref.b instanceof AssignmentExprent || ref.a instanceof IfStatement && ref.a.getImplicitlyDefinedVars().size() > 0) {
                  break;
                } else {
                  references.remove(i);
                }
              }
              boolean isTop = references.stream().allMatch(ref ->
                isSafeCast(var, ref.b, ref.a) || isProxyAssignment(var, ref.b));
              if (isTop) {
                // all refs now are either:
                // - if(top instanceof Pattern)
                // - int v = safeCast(top)
                // - top = $proxy$c(...);
                // all can become inlined patterns or removed
                List<PatternExprent> components = new ArrayList<>();
                for (int i = 0; i < references.size(); i++) {
                  Pair<Statement, Exprent> ref = references.get(i);
                  if (ref.a instanceof IfStatement) {
                    Statement ifStat = ref.a;
                    PatternExprent pattern = (PatternExprent) ((FunctionExprent) ((IfStatement) ifStat).getHeadexprent().getCondition()).getLstOperands().get(1);
                    components.add(pattern);
                    Statement trueBranch = ((IfStatement) ifStat).getIfstat();
                    ifStat.replaceWith(trueBranch);
                    //trueBranch.getBasichead().getExprents().addAll(0, ifStat.getBasichead().getExprents());
                  } else {
                    if (ref.b instanceof AssignmentExprent) {
                      Exprent left = ((AssignmentExprent) ref.b).getLeft();
                      if (!left.equals(var) || i == references.size() - 1) {
                        components.add(new PatternExprent.TypePatternExprent((VarExprent) left, var.getVarType()));
                      }
                      ref.a.getExprents().remove(ref.b);
                    }
                  }
                }
                return Optional.of(new PatternExprent.RecordPatternExprent(target.getExprType(), components, null));
              }
            }
          }
        }
      }
    }
    return Optional.empty();
  }

  private static boolean isRecordClass(VarType type) {
    StructClass target = DecompilerContext.getStructContext().getClass(type.value);
    return target != null && target.getRecordComponents() != null;
  }

  private static boolean isSafeCast(VarExprent var, Exprent usage, Statement container){
    // object components used in further patterns
    if (container instanceof IfStatement) {
      Exprent cond = ((IfStatement) container).getHeadexprent().getCondition();
      if (cond instanceof FunctionExprent) {
        FunctionExprent func = (FunctionExprent) cond;
        return func.getFuncType() == FunctionExprent.FunctionType.INSTANCEOF
          && func.getLstOperands().get(0).equals(var)
          && func.getLstOperands().get(1) instanceof PatternExprent;
      }
    }
    // TODO: int components passed through valueOf
    return false;
  }

  private static boolean isProxyAssignment(VarExprent var, Exprent usage){
    if (usage instanceof AssignmentExprent && ((AssignmentExprent) usage).getLeft().equals(var)) {
      Exprent right = ((AssignmentExprent) usage).getRight();
      return right instanceof InvocationExprent
        && ((InvocationExprent) right).getLstParameters().size() == 1
        && ((InvocationExprent) right).getName().startsWith("$proxy$"); // TODO: check inside of method instead of trusting name
    }
    return false;
  }

  private static void findExprents(Statement start, VarExprent var, BiConsumer<Statement, Exprent> consumer) {
    Queue<Statement> statQueue = new ArrayDeque<>();
    statQueue.add(start);

    while (!statQueue.isEmpty()) {
      Statement stat = statQueue.remove();
      statQueue.addAll(stat.getStats());

      List<Exprent> exprents = stat.getExprents();
      if (exprents == null) {
        exprents = new ArrayList<>();
      }

      if (stat instanceof IfStatement) {
        IfStatement ifStat = (IfStatement) stat;
        exprents.add(ifStat.getHeadexprent());
      }

      for (Exprent expr : exprents) {
        for (Exprent a : expr.getAllExprents(true, true)) {
          if (a instanceof VarExprent && ((VarExprent) a).getIndex() == var.getIndex()) {
            consumer.accept(stat, expr);
            break;
          }
        }
      }
    }
  }
}