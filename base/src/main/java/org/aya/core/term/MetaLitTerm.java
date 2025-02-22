// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple2;
import org.aya.core.def.GenericDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record MetaLitTerm(
  @NotNull SourcePos sourcePos,
  @NotNull Object repr,
  @NotNull ImmutableSeq<Tuple2<GenericDef, ShapeRecognition>> candidates,
  @NotNull Term type
) implements Term {
  @SuppressWarnings("unchecked") public @NotNull Term inline() {
    if (!(type instanceof DataCall dataCall)) return this;
    return candidates.find(t -> t._1.ref() == dataCall.ref()).flatMap(t -> {
      var shape = t._2.shape();
      if (shape == AyaShape.NAT_SHAPE) return Option.some(new IntegerTerm((int) repr, t._2, dataCall));
      if (shape == AyaShape.LIST_SHAPE) return Option.some(new ListTerm((ImmutableSeq<Term>) repr, t._2, dataCall));
      return Option.<Term>none();
    }).getOrDefault(this);
  }
}
