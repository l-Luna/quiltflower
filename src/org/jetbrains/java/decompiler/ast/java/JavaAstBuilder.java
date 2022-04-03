package org.jetbrains.java.decompiler.ast.java;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.ast.CompoundNode;
import org.jetbrains.java.decompiler.ast.LeafNode;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassWriter;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.RecordHelper;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.NewExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarTypeProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.StructRecordComponent;
import org.jetbrains.java.decompiler.struct.attr.*;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericClassDescriptor;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericFieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericMethodDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JavaAstBuilder {

  // Mirrors ClassWriter#classToJava
  public static AstNode fromClass(ClassNode node) {
    ClassWrapper wrapper = node.getWrapper();
    StructClass cl = wrapper.getClassStruct();
    CompoundNode result = new CompoundNode(null);

    int flags = node.type == ClassNode.CLASS_ROOT ? cl.getAccessFlags() : node.access;
    boolean isDeprecated = cl.hasAttribute(StructGeneralAttribute.ATTRIBUTE_DEPRECATED);
    boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || cl.hasAttribute(StructGeneralAttribute.ATTRIBUTE_SYNTHETIC);
    boolean isEnum = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM) && (flags & CodeConstants.ACC_ENUM) != 0;
    boolean isInterface = (flags & CodeConstants.ACC_INTERFACE) != 0;
    boolean isAnnotation = (flags & CodeConstants.ACC_ANNOTATION) != 0;
    boolean isModuleInfo = (flags & CodeConstants.ACC_MODULE) != 0 && cl.hasAttribute(StructGeneralAttribute.ATTRIBUTE_MODULE);
    StructPermittedSubclassesAttribute permittedSubClassesAttr = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_PERMITTED_SUBCLASSES);
    List<String> permittedSubClasses = permittedSubClassesAttr != null ? permittedSubClassesAttr.getClasses() : Collections.emptyList();
    boolean isSealed = permittedSubClassesAttr != null && !permittedSubClasses.isEmpty();
    boolean isNonSealed = !isSealed && cl.getVersion().hasSealedClasses() && ClassWriter.isSuperClassSealed(cl);

    if (isDeprecated && !ClassWriter.containsDeprecatedAnnotation(cl)) {
      new CommentNode(result, "@deprecated", true);
    }

    if (isSynthetic) {
      new CommentNode(result, "synthetic class");
    }

    if (isEnum) {
      // remove abstract and final flags (JLS 8.9 Enums)
      flags &= ~CodeConstants.ACC_ABSTRACT;
      flags &= ~CodeConstants.ACC_FINAL;

      // remove implicit static flag for local enums (JLS 14.3 Local class and interface declarations)
      if (node.type == ClassNode.CLASS_LOCAL) {
        flags &= ~CodeConstants.ACC_STATIC;
      }
    }

    List<StructRecordComponent> components = cl.getRecordComponents();
    if (components != null) {
      // records are implicitly final
      flags &= ~CodeConstants.ACC_FINAL;
    }

    appendModifiers(result, flags, ClassWriter.CLASS_ALLOWED, isInterface, ClassWriter.CLASS_EXCLUDED);
    AstNode modifiers = result.lastChild();
    if (!isEnum && isSealed) {
      appendLeaf(modifiers, "sealed");
    } else if (isNonSealed) {
      appendLeaf(modifiers, "non-sealed");
    }

    if (isEnum) {
      appendLeaf(result, "enum", JavaNodeRoles.CLASS_KIND);
    } else if (isInterface) {
      if (isAnnotation) {
        appendLeaf(result, "@interface", JavaNodeRoles.CLASS_KIND);
      } else {
        appendLeaf(result, "interface", JavaNodeRoles.CLASS_KIND);
      }
    } else if (isModuleInfo) {
      StructModuleAttribute moduleAttribute = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_MODULE);

      if ((moduleAttribute.moduleFlags & CodeConstants.ACC_OPEN) != 0) {
        appendLeaf(modifiers, "open");
      }

      appendLeaf(result, "module", JavaNodeRoles.CLASS_KIND);
      appendLeaf(result, moduleAttribute.moduleName);
    } else if (components != null) {
      appendLeaf(result, "record", JavaNodeRoles.CLASS_KIND);
    } else {
      appendLeaf(result, "class", JavaNodeRoles.CLASS_KIND);
    }
    appendLeaf(result, node.simpleName);

    GenericClassDescriptor descriptor = cl.getSignature();
    if (descriptor != null && !descriptor.fparameters.isEmpty()) {
      appendTypeParameters(result, descriptor.fparameters, descriptor.fbounds);
    }

    if (components != null) {
      appendRecordComponents(result, cl, components);
    }

    // TODO: better tree structure (preserve information better for transformations)

    if (!isEnum && !isInterface && components == null && cl.superClass != null) {
      VarType supertype = new VarType(cl.superClass.getString(), true);
      if (!VarType.VARTYPE_OBJECT.equals(supertype)) {
        appendLeaf(result, "extends");
        appendLeaf(result, ExprProcessor.getCastTypeName(descriptor == null ? supertype : descriptor.superclass));
      }
    }

    if (!isAnnotation) {
      int[] interfaces = cl.getInterfaces();
      if (interfaces.length > 0) {
        appendLeaf(result, isInterface ? "extends" : "implements");
        for (int i = 0; i < interfaces.length; i++) {
          if (i > 0) {
            appendLeaf(result, ",");
          }
          appendLeaf(result, ExprProcessor.getCastTypeName(descriptor == null ? new VarType(cl.getInterface(i), true) : descriptor.superinterfaces.get(i)));
        }
      }
    }

    if (!isEnum && isSealed) {
      appendLeaf(result, "permits");
      for (int i = 0; i < permittedSubClasses.size(); i++) {
        if (i > 0) {
          appendLeaf(result, ",");
        }
        appendLeaf(result, ExprProcessor.getCastTypeName(new VarType(permittedSubClasses.get(i), true)));
      }
    }

    appendLeaf(result, "{");

    appendFields(result, cl, wrapper, cl.getFields());
    appendMethods(result, node, cl, wrapper, cl.getMethods());
    appendInnerClasses(result, node, wrapper);

    appendLeaf(result, "}");

    return result;
  }

  private static AstNode appendLeaf(AstNode tree, String text) {
    return new LeafNode(tree, text);
  }

  private static void appendLeaf(AstNode tree, String text, String role) {
    AstNode node = appendLeaf(tree, text);
    node.setRole(role);
  }

  // TODO: make these return an AstNode instead of appending
  // TODO: better tree structure (preserve information better for transformations)
  private static void appendModifiers(AstNode tree, int flags, int allowed, boolean isInterface, int excluded) {
    AstNode list = new CompoundNode(tree);
    list.setRole(JavaNodeRoles.MODIFIERS);
    flags &= allowed;
    if (!isInterface) excluded = 0;
    for (int modifier : ClassWriter.MODIFIERS.keySet()) {
      if ((flags & modifier) == modifier && (modifier & excluded) == 0) {
        appendLeaf(list, ClassWriter.MODIFIERS.get(modifier), JavaNodeRoles.MODIFIER);
      }
    }
  }

  private static void appendTypeParameters(AstNode tree, List<String> parameters, List<List<VarType>> bounds) {
    AstNode list = new CompoundNode(tree);
    appendLeaf(list, "<");
    for (int i = 0; i < parameters.size(); i++) {
      AstNode param = new CompoundNode(list);
      if (i > 0) {
        appendLeaf(list, ",");
      }

      appendLeaf(param, parameters.get(i));

      List<VarType> parameterBounds = bounds.get(i);
      if (parameterBounds.size() > 1 || !"java/lang/Object".equals(parameterBounds.get(0).value)) {
        appendLeaf(param, "extends");
        appendLeaf(param, ExprProcessor.getCastTypeName(parameterBounds.get(0)));
        for (int j = 1; j < parameterBounds.size(); j++) {
          appendLeaf(param, "&");
          appendLeaf(param, ExprProcessor.getCastTypeName(parameterBounds.get(j)));
        }
      }
    }
    appendLeaf(list, ">");
  }

  private static void appendRecordComponents(AstNode tree, StructClass cl, List<StructRecordComponent> components) {
    AstNode list = new CompoundNode(tree);
    list.setRole(JavaNodeRoles.RECORD_COMPONENTS);
    appendLeaf(list, "(");
    for (int i = 0; i < components.size(); i++) {
      StructRecordComponent cd = components.get(i);
      if (i > 0) {
        appendLeaf(list, ",");
      }
      boolean varArgComponent = i == components.size() - 1 && RecordHelper.isVarArgRecord(cl);
      appendRecordComponent(list, cd, i, varArgComponent);
    }
    appendLeaf(list, ")");
  }

  private static void appendRecordComponent(AstNode tree, StructRecordComponent cd, int i, boolean varArgComponent) {
    AstNode component = new CompoundNode(tree);
    VarType fieldType = new VarType(cd.getDescriptor(), false);
    GenericFieldDescriptor descriptor = cd.getSignature();

    if (descriptor != null) fieldType = descriptor.type;

    appendLeaf(component, ExprProcessor.getCastTypeName(varArgComponent ? fieldType.decreaseArrayDim() : fieldType));
    if (varArgComponent) {
      appendLeaf(component, "...");
    }

    appendLeaf(component, cd.getName());
  }

  private static void appendFields(AstNode tree, StructClass cl, ClassWrapper wrapper, List<StructField> fields) {
    boolean enumFields = false, addedEnumSemicolon = false;
    List<StructRecordComponent> components = cl.getRecordComponents();
    AstNode fieldsList = new CompoundNode(tree);

    for (StructField fd : fields) {
      boolean hide = fd.isSynthetic() && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC) ||
        wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
      if (hide) continue;

      if (components != null && fd.getAccessFlags() == (CodeConstants.ACC_FINAL | CodeConstants.ACC_PRIVATE) &&
        components.stream().anyMatch(c -> c.getName().equals(fd.getName()) && c.getDescriptor().equals(fd.getDescriptor()))) {
        // Record component field: skip it
        continue;
      }
      AstNode field = new CompoundNode(fieldsList);
      field.setRole(JavaNodeRoles.FIELD);

      boolean isEnum = fd.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
      if (isEnum) {
        if (enumFields) {
          appendLeaf(fieldsList, ",");
        }
        enumFields = true;
      } else if (enumFields) {
        appendLeaf(fieldsList, ";");
        addedEnumSemicolon = true;
        enumFields = false;
      }

      appendField(field, cl, wrapper, fd);
    }
    if (enumFields && !addedEnumSemicolon) {
      appendLeaf(fieldsList, ";");
    }
  }

  private static void appendField(AstNode field, StructClass cl, ClassWrapper wrapper, StructField fd) {
    boolean isInterface = cl.hasModifier(CodeConstants.ACC_INTERFACE);
    boolean isDeprecated = fd.hasAttribute(StructGeneralAttribute.ATTRIBUTE_DEPRECATED);
    boolean isEnum = fd.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);

    if (isDeprecated && !ClassWriter.containsDeprecatedAnnotation(cl)) {
      new CommentNode(field, "@deprecated", true);
    }

    if (fd.isSynthetic()) {
      new CommentNode(field, "synthetic field");
    }

    if (!isEnum) {
      appendModifiers(field, fd.getAccessFlags(), ClassWriter.FIELD_ALLOWED, isInterface, ClassWriter.FIELD_EXCLUDED);
    }

    Map.Entry<VarType, GenericFieldDescriptor> fieldTypeData = ClassWriter.getFieldTypeData(fd);
    VarType fieldType = fieldTypeData.getKey();
    GenericFieldDescriptor descriptor = fieldTypeData.getValue();

    if (!isEnum) {
      new TypeNode(field, descriptor == null ? fieldType : descriptor.type);
    }

    appendLeaf(field, fd.getName());

    Exprent initializer;
    if (fd.hasModifier(CodeConstants.ACC_STATIC)) {
      initializer = wrapper.getStaticFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
    } else {
      initializer = wrapper.getDynamicFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
    }
    if (initializer != null) {
      if (isEnum && initializer.type == Exprent.EXPRENT_NEW) {
        NewExprent expr = (NewExprent) initializer;
        expr.setEnumConst(true);
        new ExprentNode(field, expr);
      } else {
        appendLeaf(field, "=");

        if (initializer.type == Exprent.EXPRENT_CONST) {
          ((ConstExprent) initializer).adjustConstType(fieldType);
        }

        // FIXME: special case field initializer. Can map to more than one method (constructor) and bytecode instruction.
        // TODO: use ExprentNode
        TextBuffer buffer = new TextBuffer();
        ExprProcessor.getCastedExprent(initializer, descriptor == null ? fieldType : descriptor.type, buffer, 0, false);
        appendLeaf(field, buffer.toString());
      }
    } else if (fd.hasModifier(CodeConstants.ACC_FINAL) && fd.hasModifier(CodeConstants.ACC_STATIC)) {
      StructConstantValueAttribute attr = fd.getAttribute(StructGeneralAttribute.ATTRIBUTE_CONSTANT_VALUE);
      if (attr != null) {
        PrimitiveConstant constant = cl.getPool().getPrimitiveConstant(attr.getIndex());
        appendLeaf(field, "=");
        new ExprentNode(field, new ConstExprent(fieldType, constant.value, null));
      }
    }

    if (!isEnum) {
      appendLeaf(field, ";");
    }
  }

  private static void appendMethods(AstNode tree, ClassNode node, StructClass cl, ClassWrapper wrapper, List<StructMethod> methods) {
    AstNode methodList = new CompoundNode(tree);
    for (int i = 0; i < methods.size(); i++) {
      StructMethod mt = methods.get(i);

      boolean hide = mt.isSynthetic() && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC) ||
        mt.hasModifier(CodeConstants.ACC_BRIDGE) && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_BRIDGE) ||
        wrapper.getHiddenMembers().contains(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
      if (hide) continue;

      AstNode method = fromMethod(node, mt, cl, wrapper, i);
      if (method != null) {
        method.setRole(JavaNodeRoles.METHOD);
        methodList.addChild(method);
      }
    }
  }

  private static AstNode fromMethod(ClassNode node, StructMethod mt, StructClass cl, ClassWrapper wrapper, int methodIndex) {
    // Get method by index, this keeps duplicate methods (with the same key) separate
    MethodWrapper methodWrapper = wrapper.getMethodWrapper(methodIndex);
    // Don't set the parent yet, that's done in appendMethods
    AstNode result = new CompoundNode(null);

    boolean hideMethod = false;

    MethodWrapper outerWrapper = (MethodWrapper) DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);

    try {
      boolean isInterface = cl.hasModifier(CodeConstants.ACC_INTERFACE);
      boolean isAnnotation = cl.hasModifier(CodeConstants.ACC_ANNOTATION);
      boolean isEnum = cl.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
      boolean isDeprecated = mt.hasAttribute(StructGeneralAttribute.ATTRIBUTE_DEPRECATED);
      boolean clInit = false, init = false, dInit = false;

      MethodDescriptor md = MethodDescriptor.parseDescriptor(mt, node);

      int flags = mt.getAccessFlags();
      if ((flags & CodeConstants.ACC_NATIVE) != 0) {
        flags &= ~CodeConstants.ACC_STRICT; // compiler bug: a strictfp class sets all methods to strictfp
      }
      if (CodeConstants.CLINIT_NAME.equals(mt.getName())) {
        flags &= CodeConstants.ACC_STATIC; // ignore all modifiers except 'static' in a static initializer
      }

      boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || mt.hasAttribute(StructGeneralAttribute.ATTRIBUTE_SYNTHETIC);
      boolean isBridge = (flags & CodeConstants.ACC_BRIDGE) != 0;

      if (isDeprecated && !ClassWriter.containsDeprecatedAnnotation(cl)) {
        new CommentNode(result, "@deprecated", true);
      }

      if (isSynthetic) {
        new CommentNode(result, "synthetic method");
      }

      if (isBridge) {
        new CommentNode(result, "bridge method");
      }

      if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILER_COMMENTS) && methodWrapper.addErrorComment || methodWrapper.commentLines != null) {
        if (methodWrapper.addErrorComment) {
          for (String s : ClassWriter.getErrorComment()) {
            methodWrapper.addComment(s);
          }
        }

        for (String s : methodWrapper.commentLines) {
          appendLeaf(result, "// " + s + "\n"); // yeah
        }
      }

      // TODO: overrides

      String name = mt.getName();
      if (CodeConstants.INIT_NAME.equals(name)) {
        if (node.type == ClassNode.CLASS_ANONYMOUS) {
          name = "";
          dInit = true;
        } else {
          name = node.simpleName;
          init = true;
        }
      } else if (CodeConstants.CLINIT_NAME.equals(name)) {
        name = "";
        clInit = true;
      }

      if (!dInit) {
        appendModifiers(result, flags, ClassWriter.METHOD_ALLOWED, isInterface, ClassWriter.METHOD_EXCLUDED);
      }

      if (isInterface && !mt.hasModifier(CodeConstants.ACC_STATIC) && mt.containsCode() && (flags & CodeConstants.ACC_PRIVATE) == 0) {
        // 'default' modifier (Java 8)
        // assuming modifiers may be present on defaults in all cases
        appendLeaf(result.lastChild(), "default");
      }

      GenericMethodDescriptor descriptor = mt.getSignature();
      boolean throwsExceptions = false;
      int paramCount = 0;

      if (!clInit && !dInit) {
        boolean thisVar = !mt.hasModifier(CodeConstants.ACC_STATIC);

        if (descriptor != null && !descriptor.typeParameters.isEmpty()) {
          appendTypeParameters(result, descriptor.typeParameters, descriptor.typeParameterBounds);
        }

        if (!init) {
          new TypeNode(result, descriptor == null ? md.ret : descriptor.returnType);
        }

        appendLeaf(result, ClassWriter.toValidJavaIdentifier(name));
        appendLeaf(result, "(");

        List<VarVersionPair> mask = methodWrapper.synthParameters;

        int lastVisibleParameterIndex = -1;
        for (int i = 0; i < md.params.length; i++) {
          if (mask == null || mask.get(i) == null) {
            lastVisibleParameterIndex = i;
          }
        }

        List<StructMethodParametersAttribute.Entry> methodParameters = null;
        if (DecompilerContext.getOption(IFernflowerPreferences.USE_METHOD_PARAMETERS)) {
          StructMethodParametersAttribute attr = mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_METHOD_PARAMETERS);
          if (attr != null) {
            methodParameters = attr.getEntries();
          }
        }

        int index = isEnum && init ? 3 : thisVar ? 1 : 0;
        int start = isEnum && init ? 2 : 0;
        boolean hasDescriptor = descriptor != null;
        //mask should now have the Outer.this in it... so this *shouldn't* be nessasary.
        //if (init && !isEnum && ((node.access & CodeConstants.ACC_STATIC) == 0) && node.type == ClassNode.CLASS_MEMBER)
        //    index++;

        AstNode parameterList = new CompoundNode(result);
        parameterList.setRole(JavaNodeRoles.PARAMETER_LIST);
        for (int i = start; i < md.params.length; i++) {
          AstNode parameter = new CompoundNode(parameterList);
          parameter.setRole(JavaNodeRoles.PARAMETER);
          VarType parameterType = hasDescriptor && paramCount < descriptor.parameterTypes.size() ? descriptor.parameterTypes.get(paramCount) : md.params[i];
          if (mask == null || mask.get(i) == null) {
            if (paramCount > 0) {
              appendLeaf(parameterList, ",");
            }

            if (methodParameters != null && i < methodParameters.size()) {
              appendModifiers(parameter, methodParameters.get(i).myAccessFlags, CodeConstants.ACC_FINAL, isInterface, 0);
            } else if (methodWrapper.varproc.getVarFinal(new VarVersionPair(index, 0)) == VarTypeProcessor.VAR_EXPLICIT_FINAL) {
              appendLeaf(parameter, "final");
            }

            String typeName;
            VarType typeForName;
            boolean isVarArg = i == lastVisibleParameterIndex && mt.hasModifier(CodeConstants.ACC_VARARGS) && parameterType.arrayDim > 0;
            if (isVarArg) {
              parameterType = parameterType.decreaseArrayDim();
            }
            typeForName = parameterType;
            typeName = ExprProcessor.getCastTypeName(typeForName);

            if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeName) &&
              DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
              typeForName = VarType.VARTYPE_OBJECT;
            }
            new TypeNode(parameter, typeForName);
            if (isVarArg) {
              appendLeaf(parameter, "...");
            }

            String parameterName;
            if (methodParameters != null && i < methodParameters.size()) {
              parameterName = methodParameters.get(i).myName;
            } else {
              parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
            }

            if ((flags & (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_NATIVE)) != 0) {
              String newParameterName = methodWrapper.methodStruct.getVariableNamer().renameAbstractParameter(parameterName, index);
              parameterName = !newParameterName.equals(parameterName) ? newParameterName : DecompilerContext.getStructContext().renameAbstractParameter(methodWrapper.methodStruct.getClassQualifiedName(), mt.getName(), mt.getDescriptor(), index - (((flags & CodeConstants.ACC_STATIC) == 0) ? 1 : 0), parameterName);

            }

            appendLeaf(parameter, parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

            paramCount++;
          }

          index += parameterType.stackSize;
        }

        appendLeaf(result, ")");

        StructExceptionsAttribute attr = mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_EXCEPTIONS);
        if ((descriptor != null && !descriptor.exceptionTypes.isEmpty()) || attr != null) {
          throwsExceptions = true;
          AstNode throwsList = new CompoundNode(result);
          throwsList.setRole(JavaNodeRoles.THROWS_LIST);
          appendLeaf(throwsList, "throws");

          boolean useDescriptor = hasDescriptor && !descriptor.exceptionTypes.isEmpty();
          for (int i = 0; i < attr.getThrowsExceptions().size(); i++) {
            if (i > 0) {
              appendLeaf(throwsList, ", ");
            }
            VarType type = useDescriptor ? descriptor.exceptionTypes.get(i) : new VarType(attr.getExcClassname(i, cl.getPool()), true);
            new TypeNode(throwsList, type);
          }
        }
      }

      if ((flags & (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_NATIVE)) != 0) { // native or abstract method (explicit or interface)
        if (isAnnotation) {
          StructAnnDefaultAttribute attr = mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_ANNOTATION_DEFAULT);
          if (attr != null) {
            AstNode defaultValue = new CompoundNode(result);
            defaultValue.setRole(JavaNodeRoles.ANNOTATION_DEFAULT_VALUE);
            appendLeaf(defaultValue, "default");
            new ExprentNode(defaultValue, attr.getDefaultValue());
          }
        }

        appendLeaf(result, ";");
      } else {

        // We do not have line information for method start, lets have it here for now
        appendLeaf(result, "{");

        RootStatement root = methodWrapper.root;

        // TODO: use AstNodes for statements
        if (root != null && methodWrapper.decompileError == null) { // check for existence
          try {
            // Avoid generating imports for ObjectMethods during root.toJava(...)
            if (RecordHelper.isHiddenRecordMethod(cl, mt, root)) {
              hideMethod = true;
            } else {
              TextBuffer code = root.toJava(0);
              //code.addBytecodeMapping(root.getDummyExit().bytecode);
              hideMethod = code.length() == 0 && (clInit || dInit || ClassWriter.hideConstructor(node, init, throwsExceptions, paramCount, flags));
              appendLeaf(result, code.toString());
            }
          } catch (Throwable t) {
            String message = "Method " + mt.getName() + " " + mt.getDescriptor() + " in class " + node.classStruct.qualifiedName + " couldn't be written.";
            DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN, t);
            methodWrapper.decompileError = t;
          }
        }

        if (methodWrapper.decompileError != null) {
          TextBuffer buffer = new TextBuffer();
          ClassWriter.dumpError(buffer, methodWrapper, 0);
          appendLeaf(result, "\n" + buffer + "\n");
        }
        appendLeaf(result, "}");
      }
    } finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
    }

    return hideMethod ? null : result;
  }

  private static void appendInnerClasses(AstNode tree, ClassNode node, ClassWrapper wrapper) {
    AstNode innerClassList = new CompoundNode(tree);
    for (ClassNode inner : node.nested) {
      if (inner.type == ClassNode.CLASS_MEMBER) {
        StructClass innerCl = inner.classStruct;
        boolean isSynthetic = (inner.access & CodeConstants.ACC_SYNTHETIC) != 0 || innerCl.isSynthetic();
        boolean hide = isSynthetic && DecompilerContext.getOption(IFernflowerPreferences.REMOVE_SYNTHETIC) ||
          wrapper.getHiddenMembers().contains(innerCl.qualifiedName);
        if (hide) continue;

        AstNode classNode = fromClass(inner);
        innerClassList.addChild(classNode);
      }
    }
  }
}