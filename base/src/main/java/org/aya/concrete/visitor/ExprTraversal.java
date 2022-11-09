// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

public interface ExprTraversal<P> {
  default @NotNull Expr visitExpr(@NotNull Expr expr, P p) {
    switch (expr) {
      case Expr.App app -> {
        visitExpr(app.function(), p);
        visitExpr(app.argument().term(), p);
      }
      case Expr.New neo -> {
        neo.fields().forEach(e -> visitExpr(e.body(), p));
        visitExpr(neo.struct(), p);
      }
      case Expr.BinOpSeq seq -> seq.seq().forEach(e -> visitExpr(e.term(), p));
      case Expr.Sigma sig -> sig.params().forEach(e -> visitParam(e, p));
      case Expr.Lambda lam -> {
        visitParam(lam.param(), p);
        visitExpr(lam.body(), p);
      }
      case Expr.Tuple tup -> tup.items().forEach(i -> visitExpr(i, p));
      case Expr.Proj proj -> visitExpr(proj.tup(), p);
      case Expr.Match match -> {
        var discriminant = match.discriminant().map(i -> visitExpr(i, p));
        var clauses = match.clauses().map(c -> {
          var patterns = c.patterns.map(arg -> visitArg(p, arg));
          var body = c.expr.map(i -> visitExpr(i, p));
          return new Pattern.Clause(c.sourcePos, patterns, body);
        });
        return new Expr.Match(match.sourcePos(), discriminant, clauses);
      }
      case Expr.RawProj proj -> {
        visitExpr(proj.tup(), p);
        if (proj.cubicalArg() != null) visitExpr(proj.cubicalArg(), p);
        if (proj.restr() != null) visitExpr(proj.restr(), p);
      }
      case Expr.Coe coe -> {
        visitExpr(coe.type(), p);
        visitExpr(coe.restr(), p);
      }
      case Expr.Lift lift -> visitExpr(lift.expr(), p);
      case Expr.Hole hole -> {
        if (hole.filling() != null) visitExpr(hole.filling(), p);
      }
      case Expr.Pi pi -> {
        visitParam(pi.param(), p);
        visitExpr(pi.last(), p);
      }
      case Expr.PartEl el -> el.clauses().forEach(cl -> {
        visitExpr(cl._1, p);
        visitExpr(cl._2, p);
      });
      case Expr.Path path -> {
        visitExpr(path.type(), p);
        visitExpr(path.partial(), p);
      }
      default -> {}
    }
    return expr;
  }

  default @NotNull Expr visitParam(Expr.Param e, P pp) {
    return visitExpr(e.type(), pp);
  }

  default @NotNull Pattern visitPattern(@NotNull Pattern pattern, P pp) {
    return switch (pattern) {
      case Pattern.BinOpSeq(var pos, var seq, var as) ->
        new Pattern.BinOpSeq(pos, seq.map(arg -> visitArg(pp, arg)), as);
      case Pattern.Ctor(var pos, var resolved, var params, var as) ->
        new Pattern.Ctor(pos, resolved, params.map(arg -> visitArg(pp, arg)), as);
      case Pattern.Tuple(var pos, var patterns, var as) ->
        new Pattern.Tuple(pos, patterns.map(arg -> visitArg(pp, arg)), as);
      case Pattern.List(var pos, var patterns, var as) ->
        new Pattern.List(pos, patterns.map(arg -> arg.descent(p -> visitPattern(p, pp))), as);
      default -> pattern;
    };
  }
  private @NotNull Arg<Pattern> visitArg(P pp, Arg<Pattern> arg) {
    return arg.descent(p -> visitPattern(p, pp));
  }
}
