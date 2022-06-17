package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.BitSet;
import java.util.List;

public abstract class PatternExprent extends Exprent {

  protected PatternExprent() {
    super(Type.PATTERN);
  }

  @Override
  protected List<Exprent> getAllExprents(List<Exprent> list) {
    return null;
  }

  @Override
  public Exprent copy() {
    return null;
  }

  @Override
  public TextBuffer toJava(int indent) {
    return null;
  }

  @Override
  public void getBytecodeRange(BitSet values) {

  }
}