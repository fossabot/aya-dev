// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.pat.Pat;
import org.jetbrains.annotations.NotNull;

public record MetaPatTerm(@NotNull Pat.Meta ref) implements Term {
  public @NotNull Term inline() {
    var sol = ref.solution().get();
    return sol != null ? sol.toTerm() : this;
  }
}
