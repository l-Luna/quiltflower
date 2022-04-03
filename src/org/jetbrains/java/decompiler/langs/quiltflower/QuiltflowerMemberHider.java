package org.jetbrains.java.decompiler.langs.quiltflower;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.langs.MemberHider;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import static org.jetbrains.java.decompiler.main.DecompilerContext.getOption;

public class QuiltflowerMemberHider implements MemberHider {

  public boolean isMethodHidden(StructMethod method, ClassNode in) {
    boolean ignoredSynthetic = method.isSynthetic() && getOption(IFernflowerPreferences.REMOVE_SYNTHETIC);
    boolean ignoredBridge = method.hasModifier(CodeConstants.ACC_BRIDGE) && getOption(IFernflowerPreferences.REMOVE_BRIDGE);
    return ignoredSynthetic || ignoredBridge;
  }

  public boolean isFieldHidden(StructField field, ClassNode in) {
    return field.isSynthetic() && getOption(IFernflowerPreferences.REMOVE_SYNTHETIC);
  }
}