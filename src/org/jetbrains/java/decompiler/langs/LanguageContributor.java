package org.jetbrains.java.decompiler.langs;

import java.util.Collections;
import java.util.List;

public interface LanguageContributor {

  boolean contributeTo(Language language);

  default List<MemberHider> hiders() {
    return Collections.emptyList();
  }
}