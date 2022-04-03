package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.*;
import org.jetbrains.java.decompiler.struct.attr.StructAnnotationAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructAnnotationParameterAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructTypeAnnotationAttribute;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericFieldDescriptor;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;
import java.util.stream.Collectors;

public final class RecordHelper {
  public static boolean isHiddenRecordMethod(StructClass cl, StructMethod mt, RootStatement root) {
    if (cl.getRecordComponents() == null) return false;
    return isSyntheticRecordMethod(mt, root) || isDefaultRecordMethod(mt, root) ||
      (mt.getName().equals(CodeConstants.INIT_NAME) && !hasAnnotations(mt) && isDefaultRecordConstructor(cl, root));
  }

  public static void appendRecordComponents(TextBuffer buffer, StructClass cl, List<StructRecordComponent> components, int indent) {
    buffer.pushNewlineGroup(indent, 1);
    buffer.appendPossibleNewline();
    buffer.pushNewlineGroup(indent, 0);
    for (int i = 0; i < components.size(); i++) {
      StructRecordComponent cd = components.get(i);
      if (i > 0) {
        buffer.append(",").appendPossibleNewline(" ");
      }
      boolean varArgComponent = i == components.size() - 1 && isVarArgRecord(cl);
      recordComponentToJava(buffer, cl, cd, i, varArgComponent);
    }
    buffer.popNewlineGroup();
    buffer.appendPossibleNewline("", true);
    buffer.popNewlineGroup();
  }

  private static Exprent getSimpleReturnValue(RootStatement root) {
    Statement block = root.getFirst();
    if (!(block instanceof BasicBlockStatement)) return null;
    List<Exprent> exprents = block.getExprents();
    if (exprents.isEmpty()) return null;
    Exprent exit = exprents.get(0);
    if (!(exit instanceof ExitExprent)) return null;
    return ((ExitExprent) exit).getValue();
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static boolean isSyntheticRecordMethod(StructMethod mt, RootStatement root) {
    String name = mt.getName(), descriptor = mt.getDescriptor();
    if ((!name.equals("equals") || !descriptor.equals("(Ljava/lang/Object;)Z")) &&
      (!name.equals("hashCode") || !descriptor.equals("()I")) &&
      (!name.equals("toString") || !descriptor.equals("()Ljava/lang/String;"))) {
      return false;
    }
    Exprent value = getSimpleReturnValue(root);
    if (!(value instanceof InvocationExprent)) return false;
    LinkConstant bootstrapMethod = ((InvocationExprent) value).getBootstrapMethod();
    if (bootstrapMethod == null) return false;
    return "java/lang/runtime/ObjectMethods".equals(bootstrapMethod.classname) && "bootstrap".equals(bootstrapMethod.elementname);
  }

  // Simple heuristic to check if a method is a default getter generated by a record.
  private static boolean isDefaultRecordMethod(StructMethod mt, RootStatement root) {
    Exprent value = getSimpleReturnValue(root);
    if (!(value instanceof FieldExprent)) return false;
    FieldExprent fieldExprent = ((FieldExprent) value);
    Exprent instance = fieldExprent.getInstance();
    if (!(instance instanceof VarExprent)) return false;
    return ((VarExprent) instance).getIndex() == 0 && fieldExprent.getName().equals(mt.getName());
  }

  private static boolean isDefaultRecordConstructor(StructClass cl, RootStatement root) {
    List<StructRecordComponent> components = cl.getRecordComponents();
    if (components == null) return false;
    Statement block = root.getFirst();
    if (!(block instanceof BasicBlockStatement)) return false;
    List<Exprent> exprents = block.getExprents();
    if (exprents.size() != components.size()) return false;
    int lastIndex = 0;
    for (int i = 0; i < components.size(); i++) {
      StructRecordComponent component = components.get(i);
      Exprent assignment = exprents.get(i);
      if (!(assignment instanceof AssignmentExprent)) return false;
      Exprent left = ((AssignmentExprent) assignment).getLeft();
      if (!(left instanceof FieldExprent)) return false;
      if (!component.getName().equals(((FieldExprent) left).getName())) return false;
      Exprent fieldInstance = ((FieldExprent) left).getInstance();
      if (!(fieldInstance instanceof VarExprent) || ((VarExprent) fieldInstance).getIndex() != 0) return false;
      Exprent right = ((AssignmentExprent) assignment).getRight();
      if (!(right instanceof VarExprent)) return false;
      int index = ((VarExprent) right).getIndex();
      if (index <= lastIndex) return false;
      lastIndex = index;
    }
    return true;
  }

  private static StructMethod getCanonicalConstructor(StructClass cl) {
    String canonicalConstructorDescriptor =
      cl.getRecordComponents().stream().map(StructField::getDescriptor).collect(Collectors.joining("", "(", ")V"));
    return cl.getMethod(CodeConstants.INIT_NAME, canonicalConstructorDescriptor);
  }

  private static StructMethod getGetter(StructClass cl, StructRecordComponent rc) {
    return cl.getMethod(rc.getName(), "()" + rc.getDescriptor());
  }

  public static boolean isVarArgRecord(StructClass cl) {
    StructMethod init = getCanonicalConstructor(cl);
    return init != null && init.hasModifier(CodeConstants.ACC_VARARGS);
  }

  private static Set<String> getRecordComponentAnnotations(StructClass cl, StructRecordComponent cd, int param) {
    Set<String> annotations = new LinkedHashSet<>();
    List<StructMember> members = new ArrayList<>();
    members.add(cd);
    StructMethod getter = getGetter(cl, cd);
    if (getter != null) members.add(getter);

    for (StructMember member : members) {
      for (StructGeneralAttribute.Key<?> key : ClassWriter.ANNOTATION_ATTRIBUTES) {
        StructAnnotationAttribute attribute = (StructAnnotationAttribute) member.getAttribute(key);
        if (attribute == null) continue;
        for (AnnotationExprent annotation : attribute.getAnnotations()) {
          String text = annotation.toJava(-1).convertToStringAndAllowDataDiscard();
          annotations.add(text);
        }
      }

      for (StructGeneralAttribute.Key<?> key : ClassWriter.TYPE_ANNOTATION_ATTRIBUTES) {
        StructTypeAnnotationAttribute attribute = (StructTypeAnnotationAttribute) member.getAttribute(key);
        if (attribute == null) continue;
        for (TypeAnnotation annotation : attribute.getAnnotations()) {
          if (!annotation.isTopLevel()) continue;
          int type = annotation.getTargetType();
          if (type == TypeAnnotation.FIELD || type == TypeAnnotation.METHOD_PARAMETER) {
            String text = annotation.getAnnotation().toJava(-1).convertToStringAndAllowDataDiscard();
            annotations.add(text);
          }
        }
      }
    }

    StructMember constr = getCanonicalConstructor(cl);
    if (constr == null) return annotations;

    for (StructGeneralAttribute.Key<?> key : ClassWriter.PARAMETER_ANNOTATION_ATTRIBUTES) {
      StructAnnotationParameterAttribute attribute = (StructAnnotationParameterAttribute) constr.getAttribute(key);
      if (attribute == null) continue;
      List<List<AnnotationExprent>> paramAnnotations = attribute.getParamAnnotations();
      if (param >= paramAnnotations.size()) continue;
      for (AnnotationExprent annotation : paramAnnotations.get(param)) {
        String text = annotation.toJava(-1).convertToStringAndAllowDataDiscard();
        annotations.add(text);
      }
    }

    return annotations;
  }

  private static void recordComponentToJava(TextBuffer buffer, StructClass cl, StructRecordComponent cd, int param, boolean varArgComponent) {
    Set<String> annotations = getRecordComponentAnnotations(cl, cd, param);
    for (String annotation : annotations) {
      buffer.append(annotation).append(' ');
    }

    VarType fieldType = new VarType(cd.getDescriptor(), false);
    GenericFieldDescriptor descriptor = cd.getSignature();

    if (descriptor != null) fieldType = descriptor.type;

    buffer.append(ExprProcessor.getCastTypeName(varArgComponent ? fieldType.decreaseArrayDim() : fieldType));
    if (varArgComponent) {
      buffer.append("...");
    }
    buffer.append(' ');

    buffer.append(cd.getName());
  }
  private static boolean hasAnnotations(StructMethod mt) {
    return mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS) != null ||
      mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS) != null;
  }
}
