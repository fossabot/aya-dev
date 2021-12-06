// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.commonmark.node.Code;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record CodeOptions(
  @NotNull NormalizeMode mode,
  @NotNull DistillerOptions options,
  @NotNull ShowCode showCode
) {
  public static final @NotNull CodeOptions DEFAULT =
    new CodeOptions(NormalizeMode.NULL, DistillerOptions.pretty(), ShowCode.Core);

  public static @NotNull Literate.Code analyze(@NotNull Code code, @NotNull Expr expr) {
    var distillOpts = new DistillerOptions();
    if (code.getFirstChild() instanceof CodeAttrProcessor.Attr attr) {
      return new Literate.Code(expr, attr.options);
    } else return new Literate.Code(expr, DEFAULT);
  }

  public enum ShowCode {
    Concrete, Core, Type
  }
}
