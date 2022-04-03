// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.langs.Languages;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.SwitchHelper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.*;
import org.jetbrains.java.decompiler.struct.attr.*;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.*;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ClassWriter {
  private static final Set<String> ERROR_DUMP_STOP_POINTS = new HashSet<>(Arrays.asList(
    "Fernflower.decompileContext",
    "MethodProcessorRunnable.codeToJava",
    "ClassWriter.methodToJava",
    "ClassWriter.methodLambdaToJava",
    "ClassWriter.classLambdaToJava"
  ));

  private static boolean invokeProcessors(TextBuffer buffer, ClassNode node) {
    ClassWrapper wrapper = node.getWrapper();
    if (wrapper == null) {
      buffer.append("/* $FF: Couldn't be decompiled. Class " + node.classStruct.qualifiedName + " wasn't processed yet! */");
      List<String> lines = new ArrayList<>();
      lines.addAll(ClassWriter.getErrorComment());
      for (String line : lines) {
        buffer.append("//");
        if (!line.isEmpty()) buffer.append(' ').append(line);
        buffer.appendLineSeparator();
      }
      return false; // Doesn't make sense! how is this null? referencing an anonymous class in another object?
    }
    StructClass cl = wrapper.getClassStruct();

    // Very late switch processing, needs entire class to be decompiled for eclipse switchmap style switch-on-enum
    for (MethodWrapper method : wrapper.getMethods()) {
      if (method.root != null) {
        try {
          SwitchHelper.simplifySwitches(method.root, method.methodStruct, method.root);
        } catch (Throwable e) {
          DecompilerContext.getLogger().writeMessage("Method " + method.methodStruct.getName() + " " + method.methodStruct.getDescriptor() + " in class " + node.classStruct.qualifiedName + " couldn't be written.",
            IFernflowerLogger.Severity.WARN,
            e);
          method.decompileError = e;
        }
      }
    }

    try {
      InitializerProcessor.extractInitializers(wrapper);
      InitializerProcessor.hideInitalizers(wrapper);

      if (node.type == ClassNode.CLASS_ROOT &&
        cl.getVersion().has14ClassReferences() &&
        DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_CLASS_1_4)) {
        ClassReference14Processor.processClassReferences(node);
      }

      if (cl.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM)) {
        EnumProcessor.clearEnum(wrapper);
      }

      if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ASSERTIONS)) {
        AssertProcessor.buildAssertions(node);
      }
    } catch (Throwable t) {
      DecompilerContext.getLogger().writeMessage("Class " + node.simpleName + " couldn't be written.",
        IFernflowerLogger.Severity.WARN,
        t);
      buffer.append("// $FF: Couldn't be decompiled");
      buffer.appendLineSeparator();
      if (DecompilerContext.getOption(IFernflowerPreferences.DUMP_EXCEPTION_ON_ERROR)) {
        List<String> lines = new ArrayList<>();
        lines.addAll(ClassWriter.getErrorComment());
        collectErrorLines(t, lines);
        for (String line : lines) {
          buffer.append("//");
          if (!line.isEmpty()) buffer.append(' ').append(line);
          buffer.appendLineSeparator();
        }
      }

      return false;
    }

    return true;
  }

  public void classLambdaToJava(ClassNode node, TextBuffer buffer, Exprent method_object, int indent) {
    ClassWrapper wrapper = node.getWrapper();
    if (wrapper == null) {
      return;
    }

    boolean lambdaToAnonymous = DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS);

    ClassNode outerNode = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, node);

    try {
      StructClass cl = wrapper.getClassStruct();

      DecompilerContext.getLogger().startWriteClass(node.simpleName);

      if (node.lambdaInformation.is_method_reference) {
        if (!node.lambdaInformation.is_content_method_static && method_object != null) {
          // reference to a virtual method
          method_object.getInferredExprType(new VarType(CodeConstants.TYPE_OBJECT, 0, node.lambdaInformation.content_class_name));
          TextBuffer instance = method_object.toJava(indent);
          // If the instance is casted, then we need to wrap it
          if (method_object.type == Exprent.EXPRENT_FUNCTION && ((FunctionExprent)method_object).getFuncType() == FunctionExprent.FUNCTION_CAST && ((FunctionExprent)method_object).doesCast()) {
            buffer.append('(').append(instance).append(')');
          }
          else {
            buffer.append(instance);
          }
        }
        else {
          // reference to a static method
          buffer.append(ExprProcessor.getCastTypeName(new VarType(node.lambdaInformation.content_class_name, true)));
        }

        buffer.append("::")
          .append(CodeConstants.INIT_NAME.equals(node.lambdaInformation.content_method_name) ? "new" : node.lambdaInformation.content_method_name);
      }
      else {
        // lambda method
        StructMethod mt = cl.getMethod(node.lambdaInformation.content_method_key);
        MethodWrapper methodWrapper = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());
        MethodDescriptor md_content = MethodDescriptor.parseDescriptor(node.lambdaInformation.content_method_descriptor);
        MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(node.lambdaInformation.method_descriptor);

        boolean simpleLambda = false;

        if (!lambdaToAnonymous) {
          boolean lambdaParametersNeedParentheses = md_lambda.params.length != 1;

          if (lambdaParametersNeedParentheses) {
            buffer.append('(');
          }

          boolean firstParameter = true;
          int index = node.lambdaInformation.is_content_method_static ? 0 : 1;
          int start_index = md_content.params.length - md_lambda.params.length;

          for (int i = 0; i < md_content.params.length; i++) {
            if (i >= start_index) {
              if (!firstParameter) {
                buffer.append(", ");
              }

              String parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
              buffer.append(parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

              firstParameter = false;
            }

            index += md_content.params[i].stackSize;
          }

          if (lambdaParametersNeedParentheses) {
            buffer.append(")");
          }
          buffer.append(" ->");

          RootStatement root = wrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;
          if (DecompilerContext.getOption(IFernflowerPreferences.INLINE_SIMPLE_LAMBDAS) && methodWrapper.decompileError == null && root != null) {
            Statement firstStat = root.getFirst();
            if (firstStat.type == Statement.TYPE_BASICBLOCK && firstStat.getExprents() != null && firstStat.getExprents().size() == 1) {
              Exprent firstExpr = firstStat.getExprents().get(0);
              boolean isVarDefinition = firstExpr.type == Exprent.EXPRENT_ASSIGNMENT &&
                ((AssignmentExprent)firstExpr).getLeft().type == Exprent.EXPRENT_VAR &&
                ((VarExprent)((AssignmentExprent)firstExpr).getLeft()).isDefinition();

              boolean isThrow = firstExpr.type == Exprent.EXPRENT_EXIT &&
                ((ExitExprent)firstExpr).getExitType() == ExitExprent.EXIT_THROW;

              if (!isVarDefinition && !isThrow) {
                simpleLambda = true;
                MethodWrapper outerWrapper = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
                DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);
                try {
                  TextBuffer codeBuffer = firstExpr.toJava(indent + 1);

                  if (firstExpr.type == Exprent.EXPRENT_EXIT)
                    codeBuffer.setStart(6); // skip return
                  else
                    codeBuffer.prepend(" ");

                  codeBuffer.addBytecodeMapping(root.getDummyExit().bytecode);
                  buffer.append(codeBuffer, node.classStruct.qualifiedName, InterpreterUtil.makeUniqueKey(methodWrapper.methodStruct.getName(), methodWrapper.methodStruct.getDescriptor()));
                }
                catch (Throwable ex) {
                  DecompilerContext.getLogger().writeMessage("Method " + mt.getName() + " " + mt.getDescriptor() + " in class " + node.classStruct.qualifiedName + " couldn't be written.",
                    IFernflowerLogger.Severity.WARN,
                    ex);
                  methodWrapper.decompileError = ex;
                  buffer.append(" // $FF: Couldn't be decompiled");
                }
                finally {
                  DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
                }
              }
            }
          }
        }

        if (!simpleLambda) {
          buffer.append(" {").appendLineSeparator();

          methodLambdaToJava(node, wrapper, mt, buffer, indent + 1, !lambdaToAnonymous);

          buffer.appendIndent(indent).append("}");
        }
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, outerNode);
    }

    DecompilerContext.getLogger().endWriteClass();
  }

  public void classToJava(ClassNode node, TextBuffer buffer) {
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, node);

    // last minute processing
    boolean ok = invokeProcessors(buffer, node);

    if (!ok) {
      return;
    }

    ClassWrapper wrapper = node.getWrapper();
    StructClass cl = wrapper.getClassStruct();

    DecompilerContext.getLogger().startWriteClass(cl.qualifiedName);

    AstNode astNode = Languages.buildUsingInferredLanguage(node);
    buffer.append(astNode.text());
  }

  public static void packageInfoToJava(StructClass cl, TextBuffer buffer) {
    appendAnnotations(buffer, 0, cl, -1);

    int index = cl.qualifiedName.lastIndexOf('/');
    String packageName = cl.qualifiedName.substring(0, index).replace('/', '.');
    buffer.append("package ").append(packageName).append(';').appendLineSeparator().appendLineSeparator();
  }

  public static void moduleInfoToJava(StructClass cl, TextBuffer buffer) {
    appendAnnotations(buffer, 0, cl, -1);

    StructModuleAttribute moduleAttribute = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_MODULE);

    if ((moduleAttribute.moduleFlags & CodeConstants.ACC_OPEN) != 0) {
      buffer.append("open ");
    }

    buffer.append("module ").append(moduleAttribute.moduleName).append(" {").appendLineSeparator();

    writeModuleInfoBody(buffer, moduleAttribute);

    buffer.append('}').appendLineSeparator();
  }

  private static void writeModuleInfoBody(TextBuffer buffer, StructModuleAttribute moduleAttribute) {
    boolean newLineNeeded = false;

    List<StructModuleAttribute.RequiresEntry> requiresEntries = moduleAttribute.requires;
    if (!requiresEntries.isEmpty()) {
      for (StructModuleAttribute.RequiresEntry requires : requiresEntries) {
        if (!isGenerated(requires.flags)) {
          buffer.appendIndent(1).append("requires ").append(requires.moduleName.replace('/', '.')).append(';').appendLineSeparator();
          newLineNeeded = true;
        }
      }
    }

    List<StructModuleAttribute.ExportsEntry> exportsEntries = moduleAttribute.exports;
    if (!exportsEntries.isEmpty()) {
      if (newLineNeeded) buffer.appendLineSeparator();
      for (StructModuleAttribute.ExportsEntry exports : exportsEntries) {
        if (!isGenerated(exports.flags)) {
          buffer.appendIndent(1).append("exports ").append(exports.packageName.replace('/', '.'));
          List<String> exportToModules = exports.exportToModules;
          if (exportToModules.size() > 0) {
            buffer.append(" to").appendLineSeparator();
            appendFQClassNames(buffer, exportToModules);
          }
          buffer.append(';').appendLineSeparator();
          newLineNeeded = true;
        }
      }
    }

    List<StructModuleAttribute.OpensEntry> opensEntries = moduleAttribute.opens;
    if (!opensEntries.isEmpty()) {
      if (newLineNeeded) buffer.appendLineSeparator();
      for (StructModuleAttribute.OpensEntry opens : opensEntries) {
        if (!isGenerated(opens.flags)) {
          buffer.appendIndent(1).append("opens ").append(opens.packageName.replace('/', '.'));
          List<String> opensToModules = opens.opensToModules;
          if (opensToModules.size() > 0) {
            buffer.append(" to").appendLineSeparator();
            appendFQClassNames(buffer, opensToModules);
          }
          buffer.append(';').appendLineSeparator();
          newLineNeeded = true;
        }
      }
    }

    List<String> usesEntries = moduleAttribute.uses;
    if (!usesEntries.isEmpty()) {
      if (newLineNeeded) buffer.appendLineSeparator();
      for (String uses : usesEntries) {
        buffer.appendIndent(1).append("uses ").append(ExprProcessor.buildJavaClassName(uses)).append(';').appendLineSeparator();
      }
      newLineNeeded = true;
    }

    List<StructModuleAttribute.ProvidesEntry> providesEntries = moduleAttribute.provides;
    if (!providesEntries.isEmpty()) {
      if (newLineNeeded) buffer.appendLineSeparator();
      for (StructModuleAttribute.ProvidesEntry provides : providesEntries) {
        buffer.appendIndent(1).append("provides ").append(ExprProcessor.buildJavaClassName(provides.interfaceName)).append(" with").appendLineSeparator();
        appendFQClassNames(buffer, provides.implementationNames.stream().map(ExprProcessor::buildJavaClassName).collect(Collectors.toList()));
        buffer.append(';').appendLineSeparator();
      }
    }
  }

  private static boolean isGenerated(int flags) {
    return (flags & (CodeConstants.ACC_SYNTHETIC | CodeConstants.ACC_MANDATED)) != 0;
  }

  public static boolean isSuperClassSealed(StructClass cl) {
    if (cl.superClass != null) {
      StructClass superClass = DecompilerContext.getStructContext().getClass((String) cl.superClass.value);
      if (superClass != null && superClass.hasAttribute(StructGeneralAttribute.ATTRIBUTE_PERMITTED_SUBCLASSES)) {
        return true;
      }
    }
    for (String iface : cl.getInterfaceNames()) {
      StructClass ifaceClass = DecompilerContext.getStructContext().getClass(iface);
      if (ifaceClass != null && ifaceClass.hasAttribute(StructGeneralAttribute.ATTRIBUTE_PERMITTED_SUBCLASSES)) {
        return true;
      }
    }
    return false;
  }

  private static void methodLambdaToJava(ClassNode lambdaNode,
                                         ClassWrapper classWrapper,
                                         StructMethod mt,
                                         TextBuffer buffer,
                                         int indent,
                                         boolean codeOnly) {
    MethodWrapper methodWrapper = classWrapper.getMethodWrapper(mt.getName(), mt.getDescriptor());

    MethodWrapper outerWrapper = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, methodWrapper);

    try {
      String method_name = lambdaNode.lambdaInformation.method_name;
      MethodDescriptor md_content = MethodDescriptor.parseDescriptor(lambdaNode.lambdaInformation.content_method_descriptor);
      MethodDescriptor md_lambda = MethodDescriptor.parseDescriptor(lambdaNode.lambdaInformation.method_descriptor);

      if (!codeOnly) {
        buffer.appendIndent(indent);
        buffer.append("public ");
        buffer.append(method_name);
        buffer.append("(");

        boolean firstParameter = true;
        int index = lambdaNode.lambdaInformation.is_content_method_static ? 0 : 1;
        int start_index = md_content.params.length - md_lambda.params.length;

        for (int i = 0; i < md_content.params.length; i++) {
          if (i >= start_index) {
            if (!firstParameter) {
              buffer.append(", ");
            }

            String typeName = ExprProcessor.getCastTypeName(md_content.params[i].copy());
            if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeName) &&
                DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
              typeName = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT);
            }

            buffer.append(typeName);
            buffer.append(" ");

            String parameterName = methodWrapper.varproc.getVarName(new VarVersionPair(index, 0));
            buffer.append(parameterName == null ? "param" + index : parameterName); // null iff decompiled with errors

            firstParameter = false;
          }

          index += md_content.params[i].stackSize;
        }

        buffer.append(") {").appendLineSeparator();

        indent += 1;
      }

      RootStatement root = classWrapper.getMethodWrapper(mt.getName(), mt.getDescriptor()).root;
      if (methodWrapper.decompileError == null) {
        if (root != null) { // check for existence
          try {
            TextBuffer childBuf = root.toJava(indent);
            childBuf.addBytecodeMapping(root.getDummyExit().bytecode);
            buffer.append(childBuf, classWrapper.getClassStruct().qualifiedName, InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
          }
          catch (Throwable t) {
            String message = "Method " + mt.getName() + " " + mt.getDescriptor() + " in class " + lambdaNode.classStruct.qualifiedName + " couldn't be written.";
            DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN, t);
            methodWrapper.decompileError = t;
          }
        }
      }

      if (methodWrapper.decompileError != null) {
        dumpError(buffer, methodWrapper, indent);
      }

      if (!codeOnly) {
        indent -= 1;
        buffer.appendIndent(indent).append('}').appendLineSeparator();
      }
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_WRAPPER, outerWrapper);
    }
  }

  public static String toValidJavaIdentifier(String name) {
    if (name == null || name.isEmpty()) return name;

    boolean changed = false;
    StringBuilder res = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if ((i == 0 && !Character.isJavaIdentifierStart(c))
          || (i > 0 && !Character.isJavaIdentifierPart(c))) {
        changed = true;
        res.append("_");
      }
      else {
        res.append(c);
      }
    }
    if (!changed) {
      return name;
    }
    return res.append("/* $FF was: ").append(name).append("*/").toString();
  }

  public static void dumpError(TextBuffer buffer, MethodWrapper wrapper, int indent) {
    List<String> lines = new ArrayList<>();
    lines.add("$FF: Couldn't be decompiled");
    boolean exceptions = DecompilerContext.getOption(IFernflowerPreferences.DUMP_EXCEPTION_ON_ERROR);
    boolean bytecode = DecompilerContext.getOption(IFernflowerPreferences.DUMP_BYTECODE_ON_ERROR);
    if (exceptions) {
      lines.addAll(ClassWriter.getErrorComment());
      collectErrorLines(wrapper.decompileError, lines);
      if (bytecode) {
        lines.add("");
      }
    }
    if (bytecode) {
      try {
        lines.add("Bytecode:");
        collectBytecode(wrapper, lines);
      } catch (Exception e) {
        lines.add("Error collecting bytecode:");
        collectErrorLines(e, lines);
      } finally {
        wrapper.methodStruct.releaseResources();
      }
    }
    for (String line : lines) {
      buffer.appendIndent(indent);
      buffer.append("//");
      if (!line.isEmpty()) buffer.append(' ').append(line);
      buffer.appendLineSeparator();
    }
  }

  public static void collectErrorLines(Throwable error, List<String> lines) {
    StackTraceElement[] stack = error.getStackTrace();
    List<StackTraceElement> filteredStack = new ArrayList<>();
    boolean hasSeenOwnClass = false;
    for (StackTraceElement e : stack) {
      String className = e.getClassName();
      boolean isOwnClass = className.startsWith("org.jetbrains.java.decompiler");
      if (isOwnClass) {
        hasSeenOwnClass = true;
      } else if (hasSeenOwnClass) {
        break;
      }
      filteredStack.add(e);
      if (isOwnClass) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        if (ERROR_DUMP_STOP_POINTS.contains(simpleName + "." + e.getMethodName())) {
          break;
        }
      }
    }
    if (filteredStack.isEmpty()) return;
    lines.add(error.toString());
    for (StackTraceElement e : filteredStack) {
      lines.add("  at " + e);
    }
    Throwable cause = error.getCause();
    if (cause != null) {
      List<String> causeLines = new ArrayList<>();
      collectErrorLines(cause, causeLines);
      if (!causeLines.isEmpty()) {
        lines.add("Caused by: " + causeLines.get(0));
        lines.addAll(causeLines.subList(1, causeLines.size()));
      }
    }
  }

  private static void collectBytecode(MethodWrapper wrapper, List<String> lines) throws IOException {
    ClassNode classNode = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    StructMethod method = wrapper.methodStruct;
    InstructionSequence instructions = method.getInstructionSequence();
    if (instructions == null) {
      method.expandData(classNode.classStruct);
      instructions = method.getInstructionSequence();
    }
    int lastOffset = instructions.getOffset(instructions.length() - 1);
    int digits = 8 - Integer.numberOfLeadingZeros(lastOffset) / 4;
    ConstantPool pool = classNode.classStruct.getPool();
    StructBootstrapMethodsAttribute bootstrap = classNode.classStruct.getAttribute(StructGeneralAttribute.ATTRIBUTE_BOOTSTRAP_METHODS);

    for (int idx = 0; idx < instructions.length(); idx++) {
      int offset = instructions.getOffset(idx);
      Instruction instr = instructions.getInstr(idx);
      StringBuilder sb = new StringBuilder();
      String offHex = Integer.toHexString(offset);
      for (int i = offHex.length(); i < digits; i++) sb.append('0');
      sb.append(offHex).append(": ");
      if (instr.wide) {
        sb.append("wide ");
      }
      sb.append(TextUtil.getInstructionName(instr.opcode));
      switch (instr.group) {
        case CodeConstants.GROUP_INVOCATION: {
          sb.append(' ');
          if (instr.opcode == CodeConstants.opc_invokedynamic && bootstrap != null) {
            appendBootstrapCall(sb, pool.getLinkConstant(instr.operand(0)), bootstrap);
          } else {
            appendConstant(sb, pool.getConstant(instr.operand(0)));
          }
          for (int i = 1; i < instr.operandsCount(); i++) {
            sb.append(' ').append(instr.operand(i));
          }
          break;
        }
        case CodeConstants.GROUP_FIELDACCESS: {
          sb.append(' ');
          appendConstant(sb, pool.getConstant(instr.operand(0)));
          break;
        }
        case CodeConstants.GROUP_JUMP: {
          sb.append(' ');
          int dest = offset + instr.operand(0);
          String destHex = Integer.toHexString(dest);
          for (int i = destHex.length(); i < digits; i++) sb.append('0');
          sb.append(destHex);
          break;
        }
        default: {
          switch (instr.opcode) {
            case CodeConstants.opc_new:
            case CodeConstants.opc_checkcast:
            case CodeConstants.opc_instanceof:
            case CodeConstants.opc_ldc:
            case CodeConstants.opc_ldc_w:
            case CodeConstants.opc_ldc2_w: {
              sb.append(' ');
              PooledConstant constant = pool.getConstant(instr.operand(0));
              if (constant.type == CodeConstants.CONSTANT_Dynamic && bootstrap != null) {
                appendBootstrapCall(sb, (LinkConstant) constant, bootstrap);
              } else {
                appendConstant(sb, constant);
              }
              break;
            }
            default: {
              for (int i = 0; i < instr.operandsCount(); i++) {
                sb.append(' ').append(instr.operand(i));
              }
            }
          }
        }
      }
      lines.add(sb.toString());
    }
  }

  private static void appendBootstrapCall(StringBuilder sb, LinkConstant target, StructBootstrapMethodsAttribute bootstrap) {
    sb.append(target.elementname).append(' ').append(target.descriptor);

    LinkConstant bsm = bootstrap.getMethodReference(target.index1);
    List<PooledConstant> bsmArgs = bootstrap.getMethodArguments(target.index1);

    sb.append(" bsm=");
    appendConstant(sb, bsm);
    sb.append(" args=[ ");
    boolean first = true;
    for (PooledConstant arg : bsmArgs) {
      if (!first) sb.append(", ");
      first = false;
      appendConstant(sb, arg);
    }
    sb.append(" ]");
  }

  private static void appendConstant(StringBuilder sb, PooledConstant constant) {
    if (constant == null) {
      sb.append("<null constant>");
      return;
    }
    if (constant instanceof PrimitiveConstant) {
      PrimitiveConstant prim = ((PrimitiveConstant) constant);
      Object value = prim.value;
      String stringValue = String.valueOf(value);
      if (prim.type == CodeConstants.CONSTANT_Class) {
        sb.append(stringValue);
      } else if (prim.type == CodeConstants.CONSTANT_String) {
        sb.append('"').append(ConstExprent.convertStringToJava(stringValue, false)).append('"');
      } else {
        sb.append(stringValue);
      }
    } else if (constant instanceof LinkConstant) {
      LinkConstant linkConstant = (LinkConstant) constant;
      sb.append(linkConstant.classname).append('.').append(linkConstant.elementname).append(' ').append(linkConstant.descriptor);
    }
  }

  public static boolean hideConstructor(ClassNode node, boolean init, boolean throwsExceptions, int paramCount, int methodAccessFlags) {
    if (!init || throwsExceptions || paramCount > 0 || !DecompilerContext.getOption(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR)) {
      return false;
    }

    ClassWrapper wrapper = node.getWrapper();
	  StructClass cl = wrapper.getClassStruct();

	  int classAccessFlags = node.type == ClassNode.CLASS_ROOT ? cl.getAccessFlags() : node.access;
    boolean isEnum = cl.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);

    // default constructor requires same accessibility flags. Exception: enum constructor which is always private
  	if(!isEnum && ((classAccessFlags & ACCESSIBILITY_FLAGS) != (methodAccessFlags & ACCESSIBILITY_FLAGS))) {
  	  return false;
  	}

    int count = 0;
    for (StructMethod mt : cl.getMethods()) {
      if (CodeConstants.INIT_NAME.equals(mt.getName())) {
        if (++count > 1) {
          return false;
        }
      }
    }

    return true;
  }

  public static Map.Entry<VarType, GenericFieldDescriptor> getFieldTypeData(StructField fd) {
    VarType fieldType = new VarType(fd.getDescriptor(), false);

    GenericFieldDescriptor descriptor = fd.getSignature();
    return new AbstractMap.SimpleImmutableEntry<>(fieldType, descriptor);
  }

  public static boolean containsDeprecatedAnnotation(StructMember mb) {
    for (StructGeneralAttribute.Key<?> key : ANNOTATION_ATTRIBUTES) {
      StructAnnotationAttribute attribute = (StructAnnotationAttribute) mb.getAttribute(key);
      if (attribute != null) {
        for (AnnotationExprent annotation : attribute.getAnnotations()) {
          if (annotation.getClassName().equals("java/lang/Deprecated")) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private static void appendDeprecation(TextBuffer buffer, int indent) {
    buffer.appendIndent(indent).append("/** @deprecated */").appendLineSeparator();
  }

  private enum MType {CLASS, FIELD, METHOD}

  private static void appendRenameComment(TextBuffer buffer, String oldName, MType type, int indent) {
    if (oldName == null) return;

    buffer.appendIndent(indent);
    buffer.append("// $FF: renamed from: ");

    switch (type) {
      case CLASS:
        buffer.append(ExprProcessor.buildJavaClassName(oldName));
        break;

      case FIELD:
        String[] fParts = oldName.split(" ");
        FieldDescriptor fd = FieldDescriptor.parseDescriptor(fParts[2]);
        buffer.append(fParts[1]);
        buffer.append(' ');
        buffer.append(getTypePrintOut(fd.type));
        break;

      default:
        String[] mParts = oldName.split(" ");
        MethodDescriptor md = MethodDescriptor.parseDescriptor(mParts[2]);
        buffer.append(mParts[1]);
        buffer.append(" (");
        boolean first = true;
        for (VarType paramType : md.params) {
          if (!first) {
            buffer.append(", ");
          }
          first = false;
          buffer.append(getTypePrintOut(paramType));
        }
        buffer.append(") ");
        buffer.append(getTypePrintOut(md.ret));
    }

    buffer.appendLineSeparator();
  }

  private static String getTypePrintOut(VarType type) {
    String typeText = ExprProcessor.getCastTypeName(type, false);
    if (ExprProcessor.UNDEFINED_TYPE_STRING.equals(typeText) &&
        DecompilerContext.getOption(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT)) {
      typeText = ExprProcessor.getCastTypeName(VarType.VARTYPE_OBJECT, false);
    }
    return typeText;
  }

  public static List<String> getErrorComment() {
    return Arrays.stream(((String) DecompilerContext.getProperty(IFernflowerPreferences.ERROR_MESSAGE)).split("\n")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

  private static void appendComment(TextBuffer buffer, String comment, int indent) {
    buffer.appendIndent(indent).append("// $FF: ").append(comment).appendLineSeparator();
  }
  
  private static void appendJavadoc(TextBuffer buffer, String javaDoc, int indent) {
    if (javaDoc == null) return;
    buffer.appendIndent(indent).append("/**").appendLineSeparator();
    for (String s : javaDoc.split("\n")) {
      buffer.appendIndent(indent).append(" * ").append(s).appendLineSeparator();
    }
    buffer.appendIndent(indent).append(" */").appendLineSeparator();
  }

  static final StructGeneralAttribute.Key<?>[] ANNOTATION_ATTRIBUTES = {
    StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS, StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS};
  static final StructGeneralAttribute.Key<?>[] PARAMETER_ANNOTATION_ATTRIBUTES = {
    StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS};
  static final StructGeneralAttribute.Key<?>[] TYPE_ANNOTATION_ATTRIBUTES = {
    StructGeneralAttribute.ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS, StructGeneralAttribute.ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS};

  static void appendAnnotations(TextBuffer buffer, int indent, StructMember mb, int targetType) {
    Set<String> filter = new HashSet<>();

    for (StructGeneralAttribute.Key<?> key : ANNOTATION_ATTRIBUTES) {
      StructAnnotationAttribute attribute = (StructAnnotationAttribute)mb.getAttribute(key);
      if (attribute != null) {
        for (AnnotationExprent annotation : attribute.getAnnotations()) {
          String text = annotation.toJava(indent).convertToStringAndAllowDataDiscard();
          filter.add(text);
          buffer.append(text);
          if (indent < 0) {
            buffer.append(' ');
          }
          else {
            buffer.appendLineSeparator();
          }
        }
      }
    }

    appendTypeAnnotations(buffer, indent, mb, targetType, -1, filter);
  }

  public static boolean hasAnnotation(StructMember mb, String annotation) {
    for (StructGeneralAttribute.Key<?> key : ANNOTATION_ATTRIBUTES) {
      StructAnnotationAttribute attribute = (StructAnnotationAttribute)mb.getAttribute(key);
      if (attribute != null && !attribute.getAnnotations().isEmpty()) {
        for (AnnotationExprent annotationExprent : attribute.getAnnotations()) {
          if (annotationExprent.getClassName().equals(annotation)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private static void appendTypeAnnotations(TextBuffer buffer, int indent, StructMember mb, int targetType, int index, Set<String> filter) {
    for (StructGeneralAttribute.Key<?> key : TYPE_ANNOTATION_ATTRIBUTES) {
      StructTypeAnnotationAttribute attribute = (StructTypeAnnotationAttribute)mb.getAttribute(key);
      if (attribute != null) {
        for (TypeAnnotation annotation : attribute.getAnnotations()) {
          if (annotation.isTopLevel() && annotation.getTargetType() == targetType && (index < 0 || annotation.getIndex() == index)) {
            String text = annotation.getAnnotation().toJava(indent).convertToStringAndAllowDataDiscard();
            if (!filter.contains(text)) {
              buffer.append(text);
              if (indent < 0) {
                buffer.append(' ');
              }
              else {
                buffer.appendLineSeparator();
              }
            }
          }
        }
      }
    }
  }

  public static final Map<Integer, String> MODIFIERS;
  static {
    MODIFIERS = new LinkedHashMap<>();
    MODIFIERS.put(CodeConstants.ACC_PUBLIC, "public");
    MODIFIERS.put(CodeConstants.ACC_PROTECTED, "protected");
    MODIFIERS.put(CodeConstants.ACC_PRIVATE, "private");
    MODIFIERS.put(CodeConstants.ACC_ABSTRACT, "abstract");
    MODIFIERS.put(CodeConstants.ACC_STATIC, "static");
    MODIFIERS.put(CodeConstants.ACC_FINAL, "final");
    MODIFIERS.put(CodeConstants.ACC_STRICT, "strictfp");
    MODIFIERS.put(CodeConstants.ACC_TRANSIENT, "transient");
    MODIFIERS.put(CodeConstants.ACC_VOLATILE, "volatile");
    MODIFIERS.put(CodeConstants.ACC_SYNCHRONIZED, "synchronized");
    MODIFIERS.put(CodeConstants.ACC_NATIVE, "native");
  }

  public static final int CLASS_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_ABSTRACT |
    CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL | CodeConstants.ACC_STRICT;
  public static final int FIELD_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_STATIC |
    CodeConstants.ACC_FINAL | CodeConstants.ACC_TRANSIENT | CodeConstants.ACC_VOLATILE;
  public static final int METHOD_ALLOWED =
    CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE | CodeConstants.ACC_ABSTRACT |
    CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL | CodeConstants.ACC_SYNCHRONIZED | CodeConstants.ACC_NATIVE |
    CodeConstants.ACC_STRICT;

  public static final int CLASS_EXCLUDED = CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_STATIC;
  public static final int FIELD_EXCLUDED = CodeConstants.ACC_PUBLIC | CodeConstants.ACC_STATIC | CodeConstants.ACC_FINAL;
  public static final int METHOD_EXCLUDED = CodeConstants.ACC_PUBLIC | CodeConstants.ACC_ABSTRACT;

  public static final int ACCESSIBILITY_FLAGS = CodeConstants.ACC_PUBLIC | CodeConstants.ACC_PROTECTED | CodeConstants.ACC_PRIVATE;

  private static void appendModifiers(TextBuffer buffer, int flags, int allowed, boolean isInterface, int excluded) {
    flags &= allowed;
    if (!isInterface) excluded = 0;
    for (int modifier : MODIFIERS.keySet()) {
      if ((flags & modifier) == modifier && (modifier & excluded) == 0) {
        buffer.append(MODIFIERS.get(modifier)).append(' ');
      }
    }
  }

  public static String getModifiers(int flags) {
    return MODIFIERS.entrySet().stream().filter(e -> (e.getKey() & flags) != 0).map(Map.Entry::getValue).collect(Collectors.joining(" "));
  }

  public static void appendTypeParameters(TextBuffer buffer, List<String> parameters, List<List<VarType>> bounds) {
    buffer.append('<');

    for (int i = 0; i < parameters.size(); i++) {
      if (i > 0) {
        buffer.append(", ");
      }

      buffer.append(parameters.get(i));

      List<VarType> parameterBounds = bounds.get(i);
      if (parameterBounds.size() > 1 || !"java/lang/Object".equals(parameterBounds.get(0).value)) {
        buffer.append(" extends ");
        buffer.append(ExprProcessor.getCastTypeName(parameterBounds.get(0)));
        for (int j = 1; j < parameterBounds.size(); j++) {
          buffer.append(" & ");
          buffer.append(ExprProcessor.getCastTypeName(parameterBounds.get(j)));
        }
      }
    }

    buffer.append('>');
  }

  private static void appendFQClassNames(TextBuffer buffer, List<String> names) {
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i);
      buffer.appendIndent(2).append(name);
      if (i < names.size() - 1) {
        buffer.append(',').appendLineSeparator();
      }
    }
  }
}
