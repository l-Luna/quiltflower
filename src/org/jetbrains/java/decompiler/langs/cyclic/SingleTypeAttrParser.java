package org.jetbrains.java.decompiler.langs.cyclic;

import org.jetbrains.java.decompiler.langs.AttributeParser;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;

/**
 * Parses the Cyclic:IsSingle attribute.
 */
public class SingleTypeAttrParser implements AttributeParser<SingleTypeAttrParser.IsSingleTypeStruct> {

  public static final String ATTR_NAME = "Cyclic:IsSingle";
  public static final IsSingleTypeStruct MARKER = new IsSingleTypeStruct();

  public boolean appliesTo(String attributeName) {
    return ATTR_NAME.equals(attributeName);
  }

  public IsSingleTypeStruct parse(String attributeName, byte[] data) {
    return MARKER; // always true if present
  }

  public static class IsSingleTypeStruct extends StructGeneralAttribute {
    public IsSingleTypeStruct() {}
  }
}