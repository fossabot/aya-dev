// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.parse.AyaProducer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @author ice1000
 */
public record CodeOptions(
  @NotNull NormalizeMode mode,
  @NotNull DistillerOptions options,
  @NotNull ShowCode showCode
) {
  public static final @NotNull Pattern PARSER = Pattern.compile(
    "\\A(([\\w ]*)(\\|([\\w ]*)\\|([\\w ]*))?:)?(.*)\\z");

  public static @NotNull Literate.Code analyze(@NotNull String literal, @NotNull AyaProducer producer) {
    var distillOpts = new DistillerOptions();
    var matcher = PARSER.matcher(literal);
    var found = matcher.find();
    assert found;
    var commonOpt = matcher.group(2);
    var mode = NormalizeMode.NULL;
    ShowCode showCode = ShowCode.Concrete;
    if (commonOpt != null) {
      commonOpt = commonOpt.toUpperCase(Locale.ROOT);
      if (commonOpt.contains("C")) {
        showCode = ShowCode.Core;
      } else if (commonOpt.contains("T")) {
        showCode = ShowCode.Type;
      }
      if (commonOpt.contains("W")) {
        mode = NormalizeMode.WHNF;
      } else if (commonOpt.contains("N")) {
        mode = NormalizeMode.NF;
      }
    }
    var open = matcher.group(4);
    var close = matcher.group(5);
    if (open != null && close != null) {
      open = open.toUpperCase(Locale.ROOT);
      close = close.toUpperCase(Locale.ROOT);
      distillOpts.map.put(DistillerOptions.Key.ShowImplicitArgs, !close.contains("I"));
      distillOpts.map.put(DistillerOptions.Key.ShowImplicitPats, !close.contains("P"));
      distillOpts.map.put(DistillerOptions.Key.ShowLevels, open.contains("U"));
      distillOpts.map.put(DistillerOptions.Key.ShowLambdaTypes, open.contains("L"));
    } else {
      distillOpts.map.put(DistillerOptions.Key.ShowImplicitArgs, true);
      distillOpts.map.put(DistillerOptions.Key.ShowImplicitPats, true);
    }
    var expr = producer.visitExpr(AyaParsing.parser(matcher.group(6)).expr());
    var options = new CodeOptions(mode, distillOpts, showCode);
    return new Literate.Code(expr, options);
  }

  public enum ShowCode {
    Concrete, Core, Type
  }
}
