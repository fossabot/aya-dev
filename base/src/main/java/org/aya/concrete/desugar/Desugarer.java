// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.error.BadFreezingWarn;
import org.aya.concrete.error.DoNotationError;
import org.aya.concrete.error.LevelProblem;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.core.def.PrimDef;
import org.aya.generic.SortKind;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.ResolveInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record Desugarer(@NotNull ResolveInfo info) implements StmtConsumer {
  private int levelVar(@NotNull Expr expr) throws DesugarInterruption {
    return switch (expr) {
      case Expr.BinOpSeq binOpSeq -> levelVar(pre(binOpSeq));
      case Expr.LitInt(var pos, var i) -> i;
      default -> {
        info.opSet().reporter.report(new LevelProblem.BadLevelExpr(expr));
        throw new DesugarInterruption();
      }
    };
  }

  public static class DesugarInterruption extends Exception {}

  @Override public @NotNull Expr pre(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.App(var pos, Expr.RawSort(var uPos, var kind), var arg)when kind == SortKind.Type -> {
        try {
          yield new Expr.Type(uPos, levelVar(arg.term()));
        } catch (DesugarInterruption e) {
          yield new Expr.Error(pos, expr);
        }
      }
      case Expr.App(var pos, Expr.RawSort(var uPos, var kind), var arg)when kind == SortKind.Set -> {
        try {
          yield new Expr.Set(uPos, levelVar(arg.term()));
        } catch (DesugarInterruption e) {
          yield new Expr.Error(pos, expr);
        }
      }
      case Expr.RawProj proj -> {
        if (proj.resolvedVar() instanceof DefVar<?, ?> defVar
          && defVar.core instanceof PrimDef primDef
          && PrimDef.ID.projSyntax(primDef.id)) {
          var restr = proj.restr() != null ? proj.restr() : new Expr.LitInt(proj.sourcePos(), 0);
          var coe = new Expr.Coe(proj.sourcePos(), proj.id(), defVar, proj.tup(), restr);
          yield pre(proj.coeLeft() != null
            ? new Expr.App(proj.sourcePos(), coe, new Expr.NamedArg(true, proj.coeLeft()))
            : coe);
        }
        if (proj.restr() != null) info.opSet().reporter.report(new BadFreezingWarn(proj.restr()));
        var projExpr = new Expr.Proj(proj.sourcePos(), proj.tup(), Either.right(proj.id()), proj.resolvedVar(), MutableValue.create());
        yield pre(proj.coeLeft() != null
          ? new Expr.App(proj.sourcePos(), projExpr, new Expr.NamedArg(true, proj.coeLeft()))
          : projExpr);
      }
      case Expr.RawSort(var pos, var kind) -> switch (kind) {
        case Type -> new Expr.Type(pos, 0);
        case Set -> new Expr.Set(pos, 0);
        case Prop -> new Expr.Prop(pos);
        case ISet -> new Expr.ISet(pos);
      };
      case Expr.BinOpSeq(var pos, var seq) -> {
        assert seq.isNotEmpty() : pos.toString();
        yield pre(new BinExprParser(info, seq.view()).build(pos));
      }
      case Expr.Do doNotation -> {
        var last = doNotation.binds().last();
        if (last.var() != LocalVar.IGNORED) {
          info.opSet().reporter.report(new DoNotationError(last.sourcePos(), expr));
        }
        var rest = doNotation.binds().view().dropLast(1);
        yield pre(rest.foldRight(last.expr(),
          // Upper: x <- a from last line
          // Lower: current line
          // Goal: >>=(a, \x -> rest)
          (upper, lower) -> new Expr.App(upper.sourcePos(),
            new Expr.App(
              upper.sourcePos(), doNotation.bindName(),
              new Expr.NamedArg(true, upper.expr())),
            new Expr.NamedArg(true, new Expr.Lambda(lower.sourcePos(),
              new Expr.Param(lower.sourcePos(), upper.var(), true),
              lower)))));
      }
      case Expr.Idiom(
        var pos, Expr.IdiomNames(var empty, var or, var ap, var pure), var barred
      ) -> barred.view().map(app -> {
        var list = MutableList.<Expr.NamedArg>create();
        var pre = Expr.unapp(pre(app), list);
        var head = new Expr.App(pos, pure, new Expr.NamedArg(true, pre));
        return list.foldLeft(head, (e, arg) -> new Expr.App(e.sourcePos(),
          new Expr.App(e.sourcePos(), ap,
            new Expr.NamedArg(true, e)), arg));
      }).foldLeft(empty, (e, arg) ->
        new Expr.App(e.sourcePos(), new Expr.App(e.sourcePos(),
          or, new Expr.NamedArg(true, e)),
          new Expr.NamedArg(true, arg)));
      case Expr.Array arrayExpr -> arrayExpr.arrayBlock().fold(
        left -> {
          // desugar `[ expr | x <- xs, y <- ys ]` to `do; x <- xs; y <- ys; return expr`

          // just concat `bindings` and `return expr`
          var returnApp = new Expr.App(left.pureName().sourcePos(), left.pureName(), new Expr.NamedArg(true, left.generator()));
          var lastBind = new Expr.DoBind(left.generator().sourcePos(), LocalVar.IGNORED, returnApp);
          var doNotation = new Expr.Do(arrayExpr.sourcePos(), left.bindName(), left.binds().appended(lastBind));

          // desugar do-notation
          return pre(doNotation);
        },
        // do not desugar
        right -> arrayExpr);
      case Expr misc -> StmtConsumer.super.pre(misc);
    };
  }

  /**
   * Desugaring patterns
   *
   * @param pattern the pattern
   * @return desugared pattern
   */
  @Override public @NotNull Pattern pre(@NotNull Pattern pattern) {
    return switch (pattern) {
      case Pattern.BinOpSeq(var pos, var seq, var as) -> {
        assert seq.isNotEmpty() : pos.toString();
        yield pre(new BinPatternParser(info, seq.view(), as).build(pos));
      }
      default -> StmtConsumer.super.pre(pattern);
    };
  }
}
