// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.util.InternalException;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record Serializer(@NotNull Serializer.State state) {
  public @NotNull SerDef serialize(@NotNull GenericDef def) {
    return switch (def) {
      case ClassDef classDef -> throw new UnsupportedOperationException("TODO");
      case FnDef fn -> new SerDef.Fn(
        state.def(fn.ref),
        serializeParams(fn.telescope),
        fn.body.map(this::serialize, matchings -> matchings.map(this::serialize)),
        fn.modifiers,
        serialize(fn.result)
      );
      case FieldDef field -> new SerDef.Field(
        state.def(field.structRef),
        state.def(field.ref),
        serializeParams(field.ownerTele),
        serializeParams(field.selfTele),
        serialize(field.result),
        field.body.map(this::serialize),
        field.coerce
      );
      case StructDef struct -> new SerDef.Struct(
        state.def(struct.ref()),
        serializeParams(struct.telescope),
        serialize(struct.result),
        struct.fields.map(field -> (SerDef.Field) serialize(field))
      );
      case DataDef data -> new SerDef.Data(
        state.def(data.ref),
        serializeParams(data.telescope),
        serialize(data.result),
        data.body.map(ctor -> (SerDef.Ctor) serialize(ctor))
      );
      case PrimDef prim -> {
        assert prim.ref.module != null;
        yield new SerDef.Prim(prim.ref.module, prim.id);
      }
      case CtorDef ctor -> new SerDef.Ctor(
        state.def(ctor.dataRef),
        state.def(ctor.ref),
        serializePats(ctor.pats),
        serializeParams(ctor.ownerTele),
        serializeParams(ctor.selfTele),
        ctor.clauses.map(this::serialize),
        serialize(ctor.result),
        ctor.coerce
      );
    };
  }

  private @NotNull SerTerm.Sort serialize(@NotNull FormTerm.Sort term) {
    return switch (term) {
      case FormTerm.Type ty -> new SerTerm.Type(ty.lift());
      case FormTerm.Set set -> new SerTerm.Set(set.lift());
      case FormTerm.Prop prop -> new SerTerm.Prop();
      case FormTerm.ISet iset -> new SerTerm.ISet();
    };
  }

  private @NotNull SerTerm serialize(@NotNull Term term) {
    return switch (term) {
      case LitTerm.ShapedInt lit ->
        new SerTerm.ShapedInt(lit.repr(), SerDef.SerAyaShape.serialize(lit.shape()), serialize(lit.type()));
      case LitTerm.ShapedList lit ->
        new SerTerm.ShapedList(
          lit.repr().map(this::serialize).toImmutableSeq(),
          SerDef.SerAyaShape.serialize(lit.shape()),
          serialize(lit.type()));
      case PrimTerm.Mula end -> new SerTerm.Mula(end.asFormula().fmap(this::serialize));
      case PrimTerm.Str str -> new SerTerm.Str(str.string());
      case RefTerm ref -> new SerTerm.Ref(state.local(ref.var()));
      case RefTerm.Field ref -> new SerTerm.FieldRef(state.def(ref.ref()));
      case PrimTerm.Interval interval -> new SerTerm.Interval();
      case FormTerm.Pi pi -> new SerTerm.Pi(serialize(pi.param()), serialize(pi.body()));
      case FormTerm.Sigma sigma -> new SerTerm.Sigma(serializeParams(sigma.params()));
      case CallTerm.Con conCall -> new SerTerm.ConCall(
        state.def(conCall.head().dataRef()), state.def(conCall.head().ref()),
        serializeCall(conCall.head().ulift(), conCall.head().dataArgs()),
        serializeArgs(conCall.conArgs()));
      case CallTerm.Struct structCall -> serializeStructCall(structCall);
      case CallTerm.Data dataCall -> serializeDataCall(dataCall);
      case CallTerm.Prim prim -> new SerTerm.PrimCall(
        state.def(prim.ref()),
        prim.id(),
        serializeCall(prim.ulift(), prim.args()));
      case CallTerm.Access access -> new SerTerm.Access(
        serialize(access.of()), state.def(access.ref()),
        serializeArgs(access.structArgs()),
        serializeArgs(access.fieldArgs()));
      case CallTerm.Fn fnCall -> new SerTerm.FnCall(
        state.def(fnCall.ref()),
        serializeCall(fnCall.ulift(), fnCall.args()));
      case ElimTerm.Proj proj -> new SerTerm.Proj(serialize(proj.of()), proj.ix());
      case ElimTerm.App app -> new SerTerm.App(serialize(app.of()), serialize(app.arg()));
      case ElimTerm.Match match ->
        new SerTerm.Match(match.discriminant().map(this::serialize), match.clauses().map(this::serialize));
      case IntroTerm.Tuple tuple -> new SerTerm.Tup(tuple.items().map(this::serialize));
      case IntroTerm.Lambda lambda -> new SerTerm.Lam(serialize(lambda.param()), serialize(lambda.body()));
      case IntroTerm.New newTerm -> new SerTerm.New(serializeStructCall(newTerm.struct()), ImmutableMap.from(
        newTerm.params().view().map((k, v) -> Tuple.of(state.def(k), serialize(v)))));

      case IntroTerm.PartEl el -> new SerTerm.PartEl(el.partial().fmap(this::serialize), serialize(el.rhsType()));
      case FormTerm.PartTy ty -> new SerTerm.PartTy(serialize(ty.type()), ty.restr().fmap(this::serialize));
      case FormTerm.Path path -> new SerTerm.Path(serialize(path.cube()));
      case IntroTerm.PathLam path -> new SerTerm.PathLam(serializeIntervals(path.params()), serialize(path.body()));
      case ElimTerm.PathApp app -> new SerTerm.PathApp(serialize(app.of()),
        serializeArgs(app.args()), serialize(app.cube()));
      case PrimTerm.Coe coe -> new SerTerm.Coe(serialize(coe.type()), coe.restr().fmap(this::serialize));

      case CallTerm.Hole hole -> throw new InternalException("Shall not have holes serialized.");
      case RefTerm.MetaPat metaPat -> throw new InternalException("Shall not have metaPats serialized.");
      case ErrorTerm err -> throw new InternalException("Shall not have error term serialized.");
      case FormTerm.Sort sort -> serialize(sort);
      case PrimTerm.HComp hComp -> throw new InternalException("TODO");
      case ErasedTerm erased -> new SerTerm.Erased(serialize(erased.type()), erased.isProp());
    };
  }

  private @NotNull SerPat serialize(@NotNull Pat pat) {
    return switch (pat) {
      case Pat.Absurd absurd -> new SerPat.Absurd(absurd.explicit());
      case Pat.Ctor ctor -> new SerPat.Ctor(
        ctor.explicit(),
        state.def(ctor.ref()),
        serializePats(ctor.params()),
        serializeDataCall(ctor.type()));
      case Pat.Tuple tuple -> new SerPat.Tuple(tuple.explicit(), serializePats(tuple.pats()));
      case Pat.Bind bind -> new SerPat.Bind(bind.explicit(), state.local(bind.bind()), serialize(bind.type()));
      case Pat.End end -> new SerPat.End(end.isOne(), end.explicit());
      case Pat.Meta meta -> throw new InternalException(meta + " is illegal here");
      case Pat.ShapedInt lit -> new SerPat.ShapedInt(
        lit.repr(), lit.explicit(),
        SerDef.SerAyaShape.serialize(lit.shape()),
        serializeDataCall(lit.type()));
    };
  }

  private @NotNull SerTerm.SerCube serialize(@NotNull FormTerm.Cube cube) {
    return new SerTerm.SerCube(
      serializeIntervals(cube.params()),
      serialize(cube.type()),
      cube.partial().fmap(this::serialize));
  }

  private @NotNull SerTerm.DataCall serializeDataCall(CallTerm.@NotNull Data dataCall) {
    return new SerTerm.DataCall(
      state.def(dataCall.ref()),
      serializeCall(dataCall.ulift(), dataCall.args()));
  }

  private @NotNull SerTerm.StructCall serializeStructCall(CallTerm.@NotNull Struct structCall) {
    return new SerTerm.StructCall(
      state.def(structCall.ref()),
      serializeCall(structCall.ulift(), structCall.args()));
  }

  private @NotNull SerPat.Clause serialize(@NotNull Term.Matching matchy) {
    return new SerPat.Clause(serializePats(matchy.patterns()), serialize(matchy.body()));
  }

  private SerTerm.SerArg serialize(@NotNull Arg<@NotNull Term> termArg) {
    return new SerTerm.SerArg(serialize(termArg.term()), termArg.explicit());
  }

  public record State(
    @NotNull MutableMap<LocalVar, Integer> localCache,
    @NotNull MutableMap<DefVar<?, ?>, Integer> defCache
  ) {
    public State() {
      this(MutableMap.create(), MutableMap.create());
    }

    public @NotNull SerTerm.SimpVar local(@NotNull LocalVar var) {
      return new SerTerm.SimpVar(localCache.getOrPut(var, localCache::size), var.name());
    }

    public @NotNull SerDef.QName def(@NotNull DefVar<?, ?> var) {
      assert var.module != null;
      return new SerDef.QName(var.module, var.name());
    }
  }

  @Contract("_ -> new") private SerTerm.SerParam serialize(Term.@NotNull Param param) {
    return new SerTerm.SerParam(param.explicit(), serialize(param.ref()), serialize(param.type()));
  }

  private @NotNull SerTerm.SimpVar serialize(@NotNull LocalVar localVar) {
    return state.local(localVar);
  }

  private @NotNull ImmutableSeq<SerTerm.SerParam> serializeParams(ImmutableSeq<Term.@NotNull Param> params) {
    return params.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerTerm.SimpVar> serializeIntervals(ImmutableSeq<LocalVar> params) {
    return params.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerTerm.SerArg> serializeArgs(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerPat> serializePats(@NotNull ImmutableSeq<Pat> pats) {
    return pats.map(this::serialize);
  }

  private @NotNull SerTerm.CallData serializeCall(
    int ulift,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
    return new SerTerm.CallData(ulift, serializeArgs(args));
  }
}
