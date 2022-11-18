// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public record HighlightInfo(
  @NotNull SourcePos sourcePos,
  @NotNull HighlightInfoType type
  ) {
  public static class Ord implements Comparator<HighlightInfo> {
    public final static Ord INSTANCE = new Ord();

    private Ord() {
    }

    @Override
    public int compare(HighlightInfo lhs, HighlightInfo rhs) {
      // TODO: nullable?
      assert lhs != null;
      assert rhs != null;

      return lhs.sourcePos().compareTo(rhs.sourcePos());
    }
  }
}
