// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.env;

import kala.collection.SeqView;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr, ice1000
 */
public record MapLocalCtx(
  @NotNull MutableMap<LocalVar, Term> localMap,
  @Override @Nullable LocalCtx parent
) implements LocalCtx {
  public MapLocalCtx() {
    this(MutableLinkedHashMap.of(), null);
  }

  @Override public void remove(@NotNull SeqView<LocalVar> vars) {
    localMap.removeAll(vars);
  }

  @Override public @Nullable Term getLocal(@NotNull LocalVar var) {
    return localMap.getOrNull(var);
  }

  @Override public void putUnchecked(@NotNull LocalVar var, @NotNull Term term) {
    localMap.put(var, term);
  }

  @Override public boolean isEmpty() {
    return localMap.isEmpty() && (parent == null || parent.isEmpty());
  }

  @Override public void extractToLocal(@NotNull MutableList<Term.Param> dest) {
    localMap.mapTo(dest, (k, v) -> new Term.Param(k, v, false));
  }
}
