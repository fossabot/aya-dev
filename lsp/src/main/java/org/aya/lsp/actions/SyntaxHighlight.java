// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.tuple.Unit;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.core.def.*;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.utils.LspRange;
import org.aya.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SyntaxHighlight implements StmtConsumer<@NotNull MutableList<HighlightResult.Symbol>> {
  public static final SyntaxHighlight INSTANCE = new SyntaxHighlight();

  private @NotNull Range rangeOf(@NotNull Signatured signatured) {
    return LspRange.toRange(signatured.sourcePos());
  }

  // region def, data, struct, prim, levels
  @Override public Unit visitData(@NotNull Decl.DataDecl decl, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(decl), HighlightResult.Symbol.Kind.DataDef));
    visitBind(buffer, decl.bindBlock);
    return StmtConsumer.super.visitData(decl, buffer);
  }

  @Override public Unit visitCtor(@NotNull Decl.DataCtor ctor, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(ctor), HighlightResult.Symbol.Kind.ConDef));
    return StmtConsumer.super.visitCtor(ctor, buffer);
  }

  @Override public Unit visitStruct(@NotNull Decl.StructDecl decl, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(decl), HighlightResult.Symbol.Kind.StructDef));
    visitBind(buffer, decl.bindBlock);
    return StmtConsumer.super.visitStruct(decl, buffer);
  }

  @Override public Unit visitFn(@NotNull Decl.FnDecl decl, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(decl), HighlightResult.Symbol.Kind.FnDef));
    visitBind(buffer, decl.bindBlock);
    return StmtConsumer.super.visitFn(decl, buffer);
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(decl), HighlightResult.Symbol.Kind.PrimDef));
    return StmtConsumer.super.visitPrim(decl, buffer);
  }

  @Override
  public Unit visitGeneralize(@NotNull Generalize variables, @NotNull MutableList<HighlightResult.Symbol> symbols) {
    for (var generalized : variables.variables)
      symbols.append(new HighlightResult.Symbol(LspRange.toRange(generalized.sourcePos),
        HighlightResult.Symbol.Kind.Generalize));
    return StmtConsumer.super.visitGeneralize(variables, symbols);
  }

  // endregion

  // region call terms
  @Override public Unit visitRef(@NotNull Expr.RefExpr expr, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    if (expr.resolvedVar() instanceof DefVar<?, ?> defVar)
      visitCall(defVar, expr.sourcePos(), buffer);
    return StmtConsumer.super.visitRef(expr, buffer);
  }

  @Override public Unit visitProj(@NotNull Expr.ProjExpr expr, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    if (expr.resolvedIx() instanceof DefVar<?, ?> defVar)
      visitCall(defVar, expr.ix().getRightValue().sourcePos(), buffer);
    return StmtConsumer.super.visitProj(expr, buffer);
  }

  @Override public Unit visitNew(@NotNull Expr.NewExpr expr, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    expr.fields().forEach(field -> {
      if (field.resolvedField().value instanceof DefVar<?,?> defVar)
        visitCall(defVar, field.name().sourcePos(), buffer);
    });
    return StmtConsumer.super.visitNew(expr, buffer);
  }

  @Override public Unit visitError(Expr.@NotNull ErrorExpr error, @NotNull MutableList<HighlightResult.Symbol> symbols) {
    return Unit.unit();
  }

  private void visitCall(@NotNull DefVar<?, ?> ref, @NotNull SourcePos headPos, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    var kind = kindOf(ref);
    if (kind != null) buffer.append(new HighlightResult.Symbol(LspRange.toRange(headPos), kind));
  }

  // endregion

  // region pattern
  @Override public void visitPattern(@NotNull Pattern pattern, @NotNull MutableList<HighlightResult.Symbol> symbols) {
    switch (pattern) {
      case Pattern.Ctor ctor -> {
        if (ctor.resolved().data() instanceof DefVar<?, ?> defVar)
          visitCall(defVar, ctor.resolved().sourcePos(), symbols);
        ctor.params().forEach(param -> visitPattern(param, symbols));
      }
      case Pattern.Tuple tup -> tup.patterns().forEach(p -> visitPattern(p, symbols));
      case Pattern.BinOpSeq seq -> seq.seq().forEach(p -> visitPattern(p, symbols));
      default -> {}
    }
  }
  // endregion

  // region import, open, module

  @Override public Unit visitImport(Command.@NotNull Import cmd, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(LspRange.toRange(cmd.path().sourcePos()), HighlightResult.Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitImport(cmd, buffer);
  }

  @Override public Unit visitOpen(Command.@NotNull Open cmd, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(LspRange.toRange(cmd.path().sourcePos()), HighlightResult.Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitOpen(cmd, buffer);
  }

  @Override public Unit visitModule(Command.@NotNull Module mod, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(LspRange.toRange(mod.sourcePos()), HighlightResult.Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitModule(mod, buffer);
  }

  private HighlightResult.Symbol.@Nullable Kind kindOf(@NotNull DefVar<?, ?> ref) {
    if (ref.core instanceof FnDef || ref.concrete instanceof Decl.FnDecl)
      return HighlightResult.Symbol.Kind.FnCall;
    else if (ref.core instanceof StructDef || ref.concrete instanceof Decl.StructDecl)
      return HighlightResult.Symbol.Kind.StructCall;
    else if (ref.core instanceof PrimDef || ref.concrete instanceof Decl.PrimDecl)
      return HighlightResult.Symbol.Kind.PrimCall;
    else if (ref.core instanceof DataDef || ref.concrete instanceof Decl.DataDecl)
      return HighlightResult.Symbol.Kind.DataCall;
    else if (ref.core instanceof CtorDef || ref.concrete instanceof Decl.DataCtor)
      return HighlightResult.Symbol.Kind.ConCall;
    return null;
  }

  private void visitOperator(@NotNull MutableList<HighlightResult.Symbol> buffer, @NotNull SourcePos sourcePos, @Nullable DefVar<?, ?> op) {
    Option.of(op).filter(DefVar::isInfix).mapNotNull(this::kindOf)
      .forEach(kind -> buffer.append(new HighlightResult.Symbol(LspRange.toRange(sourcePos), kind)));
  }

  private void visitBind(@NotNull MutableList<HighlightResult.Symbol> buffer, @NotNull BindBlock bindBlock) {
    if (bindBlock == BindBlock.EMPTY) return;
    var loosers = bindBlock.resolvedLoosers().value;
    var tighters = bindBlock.resolvedTighters().value;
    if (loosers != null) bindBlock.loosers().view().zip(loosers.view())
      .forEach(tup -> visitOperator(buffer, tup._1.sourcePos(), tup._2));
    if (tighters != null) bindBlock.tighters().view().zip(tighters.view())
      .forEach(tup -> visitOperator(buffer, tup._1.sourcePos(), tup._2));
  }
  // endregion
}
