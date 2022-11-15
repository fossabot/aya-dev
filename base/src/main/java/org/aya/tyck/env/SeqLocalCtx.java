// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.env;

import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record SeqLocalCtx(
  @NotNull MutableList<P> localSeq,
  @Override @Nullable LocalCtx parent
) implements LocalCtx {
  public SeqLocalCtx() {
    this(MutableList.create(), null);
  }

  public record P(@NotNull LocalVar var, @NotNull Term type) {
  }

  @Override public void remove(@NotNull SeqView<LocalVar> vars) {
    localSeq.removeIf(p -> vars.contains(p.var));
  }

  @Override public void extractToLocal(@NotNull MutableList<Term.Param> dest) {
    localSeq.mapTo(dest, p -> new Term.Param(p.var, p.type, false));
  }

  @Override public @Nullable Term getLocal(@NotNull LocalVar var) {
    return localSeq.firstOption(p -> p.var.equals(var)).map(p -> p.type).getOrNull();
  }

  @Override public void putUnchecked(@NotNull LocalVar var, @NotNull Term term) {
    localSeq.append(new P(var, term));
  }

  @Override public boolean isEmpty() {
    return localSeq.isEmpty();
  }
}
