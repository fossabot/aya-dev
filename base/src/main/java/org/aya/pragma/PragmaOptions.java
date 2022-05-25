// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pragma;

import kala.control.Either;
import kala.control.Option;
import kala.control.primitive.BooleanOption;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;

public record PragmaOptions(
  @NotNull EnumMap<Key, Either<Object, Boolean>> pragma
) {
  public void set(@NotNull Key key, boolean value) {
    assert key.type == Boolean.class;
    pragma.put(key, Either.right(value));
  }

  public <T> void set(@NotNull Key key, @NotNull T value) {
    assert key.type == value.getClass();
    pragma.put(key, Either.left(value));
  }

  public @NotNull BooleanOption getBoolean(@NotNull Key key) {
    assert key.type == Boolean.class;
    return BooleanOption.fromOption(Option.of(pragma.get(key)).map(Either::getRightValue));
  }

  public <T> @NotNull Option<T> get(@NotNull Key key, @NotNull Class<T> clazz) {
    assert key.type == clazz;
    assert key.type != Boolean.class;
    //noinspection unchecked
    return Option.of(pragma.get(key)).map(Either::getLeftValue).map(o -> (T) o);
  }

  public enum Key {
    SkipTerck("terminates", Boolean.class);

    public final @NotNull String key;
    public final @NotNull Class<?> type;

    Key(@NotNull String key, @NotNull Class<?> type) {
      this.key = key;
      this.type = type;
    }
  }
}
