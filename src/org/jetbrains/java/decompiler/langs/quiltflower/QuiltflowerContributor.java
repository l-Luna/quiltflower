package org.jetbrains.java.decompiler.langs.quiltflower;

import org.jetbrains.java.decompiler.langs.Language;
import org.jetbrains.java.decompiler.langs.LanguageContributor;
import org.jetbrains.java.decompiler.langs.MemberHider;

import java.util.Collections;
import java.util.List;

public class QuiltflowerContributor implements LanguageContributor {

  public boolean contributeTo(Language language) {
    return true;
  }

  @Override
  public List<MemberHider> hiders() {
    return Collections.singletonList(new QuiltflowerMemberHider());
  }
}