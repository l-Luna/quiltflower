package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public abstract class PatternExprent extends Exprent {

  protected PatternExprent() {
    super(Type.PATTERN);
  }

  public abstract List<VarExprent> getBindings();

  public static class TypePatternExprent extends PatternExprent {

    private final VarExprent var;

    public TypePatternExprent(VarExprent var) {
      this.var = var;
    }

    @Override
    protected List<Exprent> getAllExprents(List<Exprent> list) {
      return Collections.singletonList(var);
    }

    @Override
    public Exprent copy() {
      return new TypePatternExprent(var);
    }

    @Override
    public TextBuffer toJava(int indent) {
      TextBuffer buffer = new TextBuffer();
      //buffer.addBytecodeMapping(bytecode);
      buffer.append(ExprProcessor.getCastTypeName(var.getVarType()));
      buffer.append(" ");
      buffer.append(var.getName());
      return buffer;
    }

    @Override
    public void getBytecodeRange(BitSet values) {
      measureBytecode(values, var);
      measureBytecode(values);
    }

    @Override
    public List<VarExprent> getBindings() {
      return Collections.singletonList(var);
    }
  }

  public static class RecordPatternExprent extends PatternExprent {
    VarType recordType;
    List<PatternExprent> nestedPatterns;
    VarExprent recordVar; // for named record patterns, X(...) x

    public RecordPatternExprent(VarType recordType, List<PatternExprent> nestedPatterns, VarExprent recordVar) {
      this.recordType = recordType;
      this.nestedPatterns = nestedPatterns;
      this.recordVar = recordVar;
    }

    @Override
    protected List<Exprent> getAllExprents(List<Exprent> list) {
      List<Exprent> buffer = new ArrayList<>(nestedPatterns);
      if (recordVar != null) {
        buffer.add(recordVar);
      }
      return buffer;
    }

    @Override
    public Exprent copy() {
      return new RecordPatternExprent(recordType, nestedPatterns, recordVar);
    }

    @Override
    public TextBuffer toJava(int indent) {
      TextBuffer buffer = new TextBuffer();
      buffer.append(ExprProcessor.getCastTypeName(recordType));
      buffer.append("(");
      for (int i = 0; i < nestedPatterns.size(); i++) {
        if (i != 0) {
          buffer.append(", ");
        }
        buffer.append(nestedPatterns.get(i).toJava());
      }
      buffer.append(")");
      if (recordVar != null) {
        buffer.append(" ");
        buffer.append(recordVar.getName());
      }
      return buffer;
    }

    @Override
    public void getBytecodeRange(BitSet values) {
      for (PatternExprent p : nestedPatterns) {
        measureBytecode(values, p);
      }
      if (recordVar != null) {
        measureBytecode(values, recordVar);
      }
      measureBytecode(values);
    }

    @Override
    public List<VarExprent> getBindings() {
      List<VarExprent> bindings = new ArrayList<>(nestedPatterns.size() + 1);
      for (PatternExprent p : nestedPatterns) {
        bindings.addAll(p.getBindings());
      }
      if (recordVar != null) {
        bindings.add(recordVar);
      }
      return bindings;
    }
  }
}