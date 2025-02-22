// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class DistillerTest {
  @Test
  public void listPattern() {
    parseAndPretty("""
      def foo (l : List Nat) : List Nat
        | [ ] => nil
        | [ 1 ] => 1 :< nil
        | [ 1, e2 ] => 1 :< e2 :< nil
        | _ => l
      """, """
      def foo (l : List Nat) : List Nat
        | [  ] => nil
        | [ 1 ] => 1 :< nil
        | [ 1, e2 ] => 1 :< e2 :< nil
        | _ => l
      """);
  }

  // we test pretty instead of parsing
  public static void parseAndPretty(@NotNull @NonNls @Language("Aya") String code, @NotNull @NonNls @Language("Aya") String pretty) {
    ParseTest.parseAndPretty(code, pretty);
  }
}
