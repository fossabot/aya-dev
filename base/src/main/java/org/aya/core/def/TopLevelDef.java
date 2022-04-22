// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * Top-level definitions.
 *
 * @author ice1000
 */
public sealed abstract class TopLevelDef implements Def permits UserDef, PrimDef {
  public final @NotNull Term result;

  protected TopLevelDef(@NotNull Term result) {
    this.result = result;
  }

  @Override public @NotNull Term result() {
    return result;
  }
}
