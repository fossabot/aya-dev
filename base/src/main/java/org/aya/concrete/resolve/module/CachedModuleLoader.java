// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableTreeMap;
import org.aya.api.error.Reporter;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.QualifiedID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @author re-xyr
 */
public final class CachedModuleLoader implements ModuleLoader {
  private final @NotNull MutableMap<@NotNull String, ResolveInfo> cache = MutableTreeMap.of();
  final @NotNull ModuleLoader loader;

  @Override public @NotNull Reporter reporter() {
    return loader.reporter();
  }

  public CachedModuleLoader(@NotNull ModuleLoader loader) {
    this.loader = loader;
  }

  @Override
  public @Nullable ResolveInfo load(@NotNull ImmutableSeq<String> path) {
    return cachedOrLoad(path, () -> loader.load(path));
  }

  @ApiStatus.Internal
  public @Nullable ResolveInfo cachedOrLoad(@NotNull ImmutableSeq<String> path, @NotNull Supplier<ResolveInfo> supplier) {
    var qualified = QualifiedID.join(path);
    return cache.getOrPut(qualified, supplier);
  }
}
