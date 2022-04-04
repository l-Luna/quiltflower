package org.jetbrains.java.decompiler.langs;

import org.jetbrains.java.decompiler.ast.AstNode;
import org.jetbrains.java.decompiler.langs.cyclic.CyclicLanguage;
import org.jetbrains.java.decompiler.langs.java.JavaLanguage;
import org.jetbrains.java.decompiler.langs.quiltflower.QuiltflowerContributor;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Languages {

  public static final JavaLanguage JAVA_LANGUAGE = new JavaLanguage();

  // TODO: find languages from external jars (e.g. by service loader)
  // Java goes last to ensure other languages take precedence
  public static final List<Language> LANGUAGES = Arrays.asList(
    new CyclicLanguage(), JAVA_LANGUAGE
  );

  public static final List<LanguageContributor> CONTRIBUTORS = Arrays.asList(
    new QuiltflowerContributor()
  );

  public static Language languageFor(ClassNode clazz) {
    if (clazz == null) {
      return JAVA_LANGUAGE;
    }
    for (Language language : LANGUAGES) {
      if (language.appliesTo(clazz)) {
        return language;
      }
    }
    return JAVA_LANGUAGE;
  }

  public static List<LanguageContributor> contributorsFor(Language language) {
    List<LanguageContributor> result = new ArrayList<>();
    for (LanguageContributor contributor : CONTRIBUTORS) {
      if (contributor.contributeTo(language)) {
        result.add(contributor);
      } else { // so dialects of one language can use features of their parent
        for (Language other : language.alsoUseContributorsFrom()) {
          if (contributor.contributeTo(other)) {
            result.add(contributor);
          }
        }
      }
    }
    return result;
  }

  public static AstNode buildUsingInferredLanguage(ClassNode clazz) {
    return languageFor(clazz).getBuilder().build(clazz);
  }

  public static boolean isMethodHidden(StructMethod method, ClassNode in, Language language) {
    return contributorsFor(language).stream()
      .flatMap(x -> x.hiders().stream())
      .anyMatch(x -> x.isMethodHidden(method, in));
  }

  public static boolean isFieldHidden(StructField field, ClassNode in, Language language) {
    return contributorsFor(language).stream()
      .flatMap(x -> x.hiders().stream())
      .anyMatch(x -> x.isFieldHidden(field, in));
  }
}