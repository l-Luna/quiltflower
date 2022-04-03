package org.jetbrains.java.decompiler.langs;

import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

public interface MemberHider {

  default boolean isMethodHidden(StructMethod method, ClassNode in) {
    return false;
  }

  default boolean isFieldHidden(StructField field, ClassNode in) {
    return false;
  }
}