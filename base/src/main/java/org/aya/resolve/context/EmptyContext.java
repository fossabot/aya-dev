// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.ref.AnyVar;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * @author re-xyr
 * @apiNote in each file's dependency tree there should be one and only one EmptyContext which is also the tree root.
 * @implNote EmptyContext is the context storing the underlying file, and its Reporter in the resolving stage.
 */
public record EmptyContext(
  @NotNull Reporter reporter,
  @NotNull Path underlyingFile
) implements Context {
  @Override public @Nullable Context parent() {
    return null;
  }

  @Override public @Nullable AnyVar getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    return null;
  }

  @Override
  public @Nullable AnyVar getQualifiedLocalMaybe(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    return null;
  }

  @Override
  public @Nullable MutableMap<String, AnyVar> getModuleLocalMaybe(@NotNull ImmutableSeq<String> modName) {
    return null;
  }
}
