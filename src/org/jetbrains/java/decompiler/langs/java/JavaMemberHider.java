package org.jetbrains.java.decompiler.langs.java;

import org.jetbrains.java.decompiler.langs.MemberHider;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

public class JavaMemberHider implements MemberHider {

  // TODO: inline hiding logic here

  public boolean isMethodHidden(StructMethod method, ClassNode in) {
    return in.getWrapper().getHiddenMembers().contains(
      InterpreterUtil.makeUniqueKey(method.getName(), method.getDescriptor())
    );
  }

  @Override
  public boolean isFieldHidden(StructField field, ClassNode in) {
    return in.getWrapper().getHiddenMembers().contains(
      InterpreterUtil.makeUniqueKey(field.getName(), field.getDescriptor())
    );
  }
}