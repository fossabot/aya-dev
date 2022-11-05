// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.guest0x0.cubical.Formula;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author ice1000
 */
public sealed interface SerTerm extends Serializable, Restr.TermLike<SerTerm> {
  record DeState(
    @NotNull MutableMap<Seq<String>, MutableMap<String, DefVar<?, ?>>> defCache,
    @NotNull MutableMap<Integer, LocalVar> localCache,
    @NotNull PrimDef.Factory primFactory
  ) {
    public DeState(@NotNull PrimDef.Factory primFactory) {
      this(MutableMap.create(), MutableMap.create(), primFactory);
    }

    public @NotNull LocalVar var(@NotNull SimpVar var) {
      return localCache.getOrPut(var.var, () -> new LocalVar(var.name));
    }

    @SuppressWarnings("unchecked")
    public <V extends DefVar<?, ?>>
    @NotNull V resolve(@NotNull SerDef.QName name) {
      // We assume this cast to be safe
      var dv = (V) defCache
        .getOrThrow(name.mod(), () -> new SerDef.DeserializeException("Unable to find module: " + name.mod()))
        .getOrThrow(name.name(), () -> new SerDef.DeserializeException("Unable to find DefVar: " + name));
      assert Objects.equals(name.name(), dv.name());
      return dv;
    }

    @SuppressWarnings("unchecked") <V extends DefVar<?, ?>>
    @NotNull V newDef(@NotNull SerDef.QName name) {
      // We assume this cast to be safe
      var defVar = DefVar.empty(name.name());
      var old = defCache
        .getOrPut(name.mod(), MutableHashMap::new)
        .put(name.name(), defVar);
      if (old.isDefined()) throw new SerDef.DeserializeException("Same definition deserialized twice: " + name);
      defVar.module = name.mod();
      return (V) defVar;
    }

    public void putPrim(
      @NotNull ImmutableSeq<String> mod,
      @NotNull PrimDef.ID id,
      @NotNull DefVar<?, ?> defVar
    ) {
      var old = defCache.getOrPut(mod, MutableHashMap::new).put(id.id, defVar);
      if (old.isDefined()) throw new SerDef.DeserializeException("Same prim deserialized twice: " + id.id);
      defVar.module = mod;
    }
  }

  record SimpVar(int var, @NotNull String name) implements Serializable {
    public @NotNull LocalVar de(@NotNull DeState state) {
      return state.var(this);
    }
  }

  @NotNull Term de(@NotNull DeState state);

  record SerParam(boolean explicit, @NotNull SimpVar var, @NotNull SerTerm term) implements Serializable {
    public @NotNull Term.Param de(@NotNull DeState state) {
      return new Term.Param(var.de(state), term.de(state), explicit);
    }
  }

  record Pi(@NotNull SerTerm.SerParam param, @NotNull SerTerm body) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PiTerm(param.de(state), body.de(state));
    }
  }

  record Sigma(@NotNull ImmutableSeq<SerParam> params) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new SigmaTerm(params.map(p -> p.de(state)));
    }
  }

  sealed interface Sort extends SerTerm {
    @Override @NotNull FormTerm.Sort de(@NotNull DeState state);
  }

  record Type(int ulift) implements Sort {
    @Override public @NotNull FormTerm.Type de(@NotNull DeState state) {
      return new FormTerm.Type(ulift);
    }
  }

  record Set(int ulift) implements Sort {
    @Override public @NotNull FormTerm.Set de(@NotNull DeState state) {
      return new FormTerm.Set(ulift);
    }
  }

  record Prop() implements Sort {
    @Override public @NotNull FormTerm.Prop de(@NotNull DeState state) {
      return FormTerm.Prop.INSTANCE;
    }
  }

  record ISet() implements Sort {
    @Override public @NotNull FormTerm.ISet de(@NotNull DeState state) {
      return FormTerm.ISet.INSTANCE;
    }
  }

  record Ref(@NotNull SimpVar var) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new RefTerm(var.de(state));
    }
  }

  record Lam(@NotNull SerParam param, @NotNull SerTerm body) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new LamTerm(param.de(state), body.de(state));
    }
  }

  record New(@NotNull StructCall call, @NotNull ImmutableMap<SerDef.QName, SerTerm> map) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new NewTerm(call.de(state), ImmutableMap.from(map.view().map((k, v) ->
        Tuple.of(state.resolve(k), v.de(state)))));
    }
  }

  record Proj(@NotNull SerTerm of, int ix) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new ProjTerm(of.de(state), ix);
    }
  }

  record Match(@NotNull ImmutableSeq<SerTerm> of, ImmutableSeq<SerPat.Clause> clauses) implements SerTerm {
    @Override
    public @NotNull Term de(@NotNull DeState state) {
      return new MatchTerm(of.map(t -> t.de(state)), clauses.map(c -> c.de(state)));
    }
  }

  record SerArg(@NotNull SerTerm arg, boolean explicit) implements Serializable {
    public @NotNull Arg<Term> de(@NotNull DeState state) {
      return new Arg<>(arg.de(state), explicit);
    }
  }

  record App(@NotNull SerTerm of, @NotNull SerArg arg) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new AppTerm(of.de(state), arg.de(state));
    }
  }

  record CallData(
    int ulift,
    @NotNull ImmutableSeq<SerArg> args
  ) implements Serializable {
    public @NotNull ImmutableSeq<Arg<Term>> de(@NotNull DeState state) {
      return args.map(arg -> arg.de(state));
    }
  }

  record StructCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull org.aya.core.term.StructCall de(@NotNull DeState state) {
      return new org.aya.core.term.StructCall(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record FnCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new org.aya.core.term.FnCall(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record DataCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull org.aya.core.term.DataCall de(@NotNull DeState state) {
      return new org.aya.core.term.DataCall(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record PrimCall(@NotNull SerDef.QName name, @NotNull PrimDef.ID id, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new org.aya.core.term.PrimCall(state.resolve(name), id, data.ulift, data.de(state));
    }
  }

  record ConCall(
    @NotNull SerDef.QName dataRef, @NotNull SerDef.QName selfRef,
    @NotNull CallData dataArgs, @NotNull ImmutableSeq<SerArg> conArgs
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new org.aya.core.term.ConCall(
        state.resolve(dataRef), state.resolve(selfRef),
        dataArgs.de(state), dataArgs.ulift,
        conArgs.map(arg -> arg.de(state)));
    }
  }

  record Tup(@NotNull ImmutableSeq<SerTerm> components) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new TupTerm(components.map(t -> t.de(state)));
    }
  }

  record Access(
    @NotNull SerTerm of,
    @NotNull SerDef.QName ref,
    @NotNull ImmutableSeq<@NotNull SerArg> structArgs,
    @NotNull ImmutableSeq<@NotNull SerArg> fieldArgs
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FieldTerm(
        of.de(state), state.resolve(ref),
        structArgs.map(arg -> arg.de(state)),
        fieldArgs.map(arg -> arg.de(state)));
    }
  }

  record FieldRef(@NotNull SerDef.QName name) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new RefTerm.Field(state.resolve(name));
    }
  }

  record Interval() implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return IntervalTerm.INSTANCE;
    }
  }

  record Mula(@NotNull Formula<SerTerm> formula) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormulaTerm(formula.fmap(t -> t.de(state)));
    }
  }

  record ShapedInt(
    int integer,
    @NotNull SerDef.SerAyaShape shape,
    @NotNull SerTerm type
  ) implements SerTerm {
    @Override public @NotNull Term de(SerTerm.@NotNull DeState state) {
      return new IntegerTerm(integer, shape.de(), type.de(state));
    }
  }

  record ShapedList(
    @NotNull ImmutableSeq<SerTerm> repr,
    @NotNull SerDef.SerAyaShape shape,
    @NotNull SerTerm type
  ) implements SerTerm {
    @Override
    public @NotNull Term de(@NotNull DeState state) {
      var termDesered = repr.map(x -> x.de(state));
      var shape = shape().de();
      var type = type().de(state);

      return new ListTerm(termDesered, shape, type);
    }
  }

  record Str(@NotNull String string) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new StringTerm(string);
    }
  }

  record PartEl(@NotNull Partial<SerTerm> partial, @NotNull SerTerm rhsType) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PartialTerm(partial.fmap(t -> t.de(state)), rhsType.de(state));
    }
  }

  record PartTy(@NotNull SerTerm type, @NotNull Restr<SerTerm> restr) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PartialTyTerm(type.de(state), restr.fmap(t -> t.de(state)));
    }
  }

  record Path(@NotNull SerCube cube) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PathTerm(cube.de(state));
    }
  }

  record PathLam(
    @NotNull ImmutableSeq<SimpVar> params,
    @NotNull SerTerm body
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PLamTerm(params.map(p -> p.de(state)), body.de(state));
    }
  }

  record PathApp(
    @NotNull SerTerm of,
    @NotNull ImmutableSeq<SerArg> args,
    @NotNull SerCube cube
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PAppTerm(of.de(state), args.map(arg -> arg.de(state)), cube.de(state));
    }
  }

  record Coe(@NotNull SerTerm type, @NotNull Restr<SerTerm> restr) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CoeTerm(type.de(state), restr.fmap(t -> t.de(state)));
    }
  }

  record SerCube(
    @NotNull ImmutableSeq<SimpVar> params,
    @NotNull SerTerm type,
    @NotNull Partial<SerTerm> partial
  ) implements Serializable {
    public @NotNull PathTerm.Cube de(@NotNull DeState state) {
      return new PathTerm.Cube(
        params.map(p -> p.de(state)),
        type.de(state),
        partial.fmap(t -> t.de(state)));
    }
  }

  record Erased(@NotNull SerTerm type, boolean isProp) implements SerTerm {
    /**
     * {@link ErasedTerm} with a Prop type might safely appear in introduction term.
     * {@link ErasedTerm} with a non-Prop type or elimination term with `of` erased are disallowed.
     */
    public static @Nullable ErasedTerm underlyingIllegalErasure(@NotNull Term term) {
      if (term instanceof Elimination elim) return ErasedTerm.underlyingErased(elim.of());
      if (term instanceof FieldTerm elim) return ErasedTerm.underlyingErased(elim.of());
      return term instanceof ErasedTerm erased && !erased.isProp() ? erased : null;
    }

    public @NotNull ErasedTerm de(@NotNull DeState state) {
      return new ErasedTerm(type.de(state), isProp);
    }
  }
}
