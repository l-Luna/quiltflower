package org.jetbrains.java.decompiler.langs;

// TODO: allow access to other struct members & parsing non-class attributes

import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;

public interface AttributeParser<T extends StructGeneralAttribute> {

  boolean appliesTo(String attributeName);

  T parse(String attributeName, byte[] data);
}