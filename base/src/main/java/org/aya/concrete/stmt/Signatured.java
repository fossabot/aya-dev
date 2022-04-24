// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.def.Def;
import org.aya.ref.DefVar;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item in the signature, with telescope and result type.
 *
 * @author ice1000
 */
public sealed abstract class Signatured implements SourceNode, OpDecl, TyckUnit permits Decl, Decl.DataCtor, Decl.StructField {
  public final @NotNull SourcePos sourcePos;
  public final @NotNull SourcePos entireSourcePos;
  public final @Nullable OpInfo opInfo;
  public final @NotNull BindBlock bindBlock;

  // will change after resolve
  public @Nullable Def.Signature signature;

  @Override public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  protected Signatured(
    @NotNull SourcePos sourcePos,
    @NotNull SourcePos entireSourcePos,
    @Nullable OpDecl.OpInfo opInfo,
    @NotNull BindBlock bindBlock
  ) {
    this.sourcePos = sourcePos;
    this.entireSourcePos = entireSourcePos;
    this.opInfo = opInfo;
    this.bindBlock = bindBlock;
  }

  @Override public @Nullable OpInfo opInfo() {
    return opInfo;
  }

  @Contract(pure = true)
  abstract public @NotNull DefVar<?, ?> ref();

  @Override public boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return ref().isInModule(currentMod) && ref().core == null;
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "[" + ref().name() + "]";
  }

  public sealed interface SignaturedWithTelescope permits Decl.DataCtor, Decl.DataDecl, Decl.FnDecl, Decl.PrimDecl, Decl.StructDecl, Decl.StructField {
    @NotNull ImmutableSeq<Expr.Param> telescope();
    void setTelescope(ImmutableSeq<Expr.Param> telescope);
  }
}
