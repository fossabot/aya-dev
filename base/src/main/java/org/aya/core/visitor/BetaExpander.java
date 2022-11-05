// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.core.pat.PatMatcher;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static org.aya.guest0x0.cubical.CofThy.isOne;

/**
 * We think of all cubical reductions as beta reductions.
 *
 * @author wsx
 * @see DeltaExpander
 */
public interface BetaExpander extends EndoTerm {
  private static @NotNull Partial<Term> partial(@NotNull Partial<Term> partial) {
    return partial.flatMap(Function.identity());
  }
  static @NotNull Term simplFormula(@NotNull FormulaTerm mula) {
    return Restr.formulae(mula.asFormula(), FormulaTerm::new);
  }
  static @NotNull PartialTyTerm partialType(@NotNull PartialTyTerm ty) {
    return new PartialTyTerm(ty.type(), ty.restr().normalize());
  }
  static @NotNull Option<Term> tryMatch(@NotNull ImmutableSeq<Term> scrutinee, @NotNull ImmutableSeq<Term.Matching> clauses) {
    for (var clause : clauses) {
      var subst = PatMatcher.tryBuildSubstTerms(null, clause.patterns(), scrutinee.view());
      if (subst.isOk()) {
        return Option.some(clause.body().rename().subst(subst.get()));
      } else if (subst.getErr()) return Option.none();
    }
    return Option.none();
  }
  @Override default @NotNull Term post(@NotNull Term term) {
    return switch (term) {
      case FormulaTerm mula -> simplFormula(mula);
      case PartialTyTerm ty -> partialType(ty);
      case MetaPatTerm metaPat -> metaPat.inline();
      case AppTerm app -> {
        var result = AppTerm.make(app);
        yield result == term ? result : apply(result);
      }
      case ProjTerm proj -> ProjTerm.proj(proj);
      case MatchTerm match -> {
        var result = tryMatch(match.discriminant(), match.clauses());
        yield result.isDefined() ? result.get() : match;
      }
      case PAppTerm(var of, var args, PathTerm.Cube(var xi, var type, var partial)) -> {
        if (of instanceof ErasedTerm) {
          var ui = args.map(Arg::term);
          yield new ErasedTerm(type.subst(new Subst(xi, ui)));
        }
        if (of instanceof PLamTerm lam) {
          var ui = args.map(Arg::term);
          var subst = new Subst(lam.params(), ui);
          yield apply(lam.body().subst(subst));
        }
        yield switch (partial(partial)) {
          case Partial.Split<Term> hap -> new PAppTerm(of, args, new PathTerm.Cube(xi, type, hap));
          case Partial.Const<Term> sad -> sad.u();
        };
      }
      case PartialTerm partial -> new PartialTerm(partial(partial.partial()), partial.rhsType());
      case CoeTerm coe -> {
        if (coe.restr() instanceof Restr.Const<Term> c && c.isOne()) {
          var var = new LocalVar("x");
          yield new LamTerm(coeDom(var, coe.type()), new RefTerm(var));
        }

        var varI = new LocalVar("i");
        var codom = apply(AppTerm.make(coe.type(), new Arg<>(new RefTerm(varI), true)));

        yield switch (codom) {
          case PathTerm path -> coe;
          case PiTerm pi -> {
            var u0Var = new LocalVar("u0");
            var vVar = new LocalVar("v");
            var A = new LamTerm(new Term.Param(varI, IntervalTerm.INSTANCE, true), pi.param().type());
            var B = new LamTerm(new Term.Param(varI, IntervalTerm.INSTANCE, true), pi.body());
            var vType = AppTerm.make(A, new Arg<>(FormulaTerm.RIGHT, true));
            var w = AppTerm.make(coeFillInv(A, coe.restr(), new RefTerm(varI)), new Arg<>(new RefTerm(vVar), true));
            var BSubsted = B.subst(pi.param().ref(), w.rename());
            var wSubsted = w.subst(varI, FormulaTerm.LEFT).rename();
            yield new LamTerm(coeDom(u0Var, coe.type()),
              new LamTerm(new Term.Param(vVar, vType, true),
                AppTerm.make(new CoeTerm(BSubsted, coe.restr()),
                  new Arg<>(AppTerm.make(new RefTerm(u0Var), new Arg<>(wSubsted, true)), true))));
          }
          case SigmaTerm sigma -> {
            var u0Var = new LocalVar("u0");
            var A = new LamTerm(new Term.Param(varI, IntervalTerm.INSTANCE, true), sigma.params().first().type());

            var B = sigma.params().sizeEquals(2) ?
              new LamTerm(new Term.Param(varI, IntervalTerm.INSTANCE, true), sigma.params().get(1).type()) :
              new LamTerm(new Term.Param(varI, IntervalTerm.INSTANCE, true), new SigmaTerm(sigma.params().drop(1)));

            var u00 = new ProjTerm(new RefTerm(u0Var), 1);
            var u01 = new ProjTerm(new RefTerm(u0Var), 2);
            var v = AppTerm.make(coeFill(A, coe.restr(), new RefTerm(varI)), new Arg<>(u00, true));

            var Bsubsted = B.subst(sigma.params().first().ref(), v);
            var coe0 = AppTerm.make(new CoeTerm(A, coe.restr()), new Arg<>(u00, true));
            var coe1 = AppTerm.make(new CoeTerm(Bsubsted, coe.restr()), new Arg<>(u01, true));
            yield new LamTerm(coeDom(u0Var, coe.type()), new TupTerm(ImmutableSeq.of(coe0, coe1)));
          }
          case FormTerm.Type type -> {
            var A = new LocalVar("A");
            yield new LamTerm(new Term.Param(A, type, true), new RefTerm(A));
          }
          default -> coe;
        };
      }
      default -> term;
    };
  }
  @NotNull private static Term.Param coeDom(LocalVar u0Var, Term type) {
    return new Term.Param(u0Var, AppTerm.make(type, new Arg<>(FormulaTerm.LEFT, true)), true);
  }

  // forward (A: I -> Type) (r: I): A r -> A 1
  private static @NotNull Term forward(@NotNull Term A, @NotNull Term r) {
    var varI = new LocalVar("i");
    var varU = new LocalVar("u");

    var iOrR = FormulaTerm.or(new RefTerm(varI), r);
    var cofib = isOne(r);
    var Ar = AppTerm.make(A, new Arg<>(r, true));
    var AiOrR = AppTerm.make(A, new Arg<>(iOrR, true));
    var lam = new LamTerm(new Term.Param(varI, IntervalTerm.INSTANCE, true), AiOrR);
    var transp = new CoeTerm(lam, cofib);
    var body = AppTerm.make(transp, new Arg<>(new RefTerm(varU), true));
    return new LamTerm(new Term.Param(LocalVar.IGNORED, Ar, true), body);
  }

  /**
   * <code>coeFill (A: I -> Type) (phi: I) : Path A u (coe A phi u)</code>
   *
   * @param ri the interval abstraction or its inverse
   */
  private static @NotNull Term coeFill(@NotNull Term type, @NotNull Restr<Term> phi, Term ri) {
    var cofib = phi.or(new Restr.Cond<>(ri, false));
    var varY = new LocalVar("y");
    var paramY = new Term.Param(varY, IntervalTerm.INSTANCE, true);
    var xAndY = FormulaTerm.and(ri, new RefTerm(varY));
    var a = new LamTerm(paramY, AppTerm.make(type, new Arg<>(xAndY, true)));

    return new CoeTerm(a, cofib);
  }

  /**
   * inverts interval in A : I -> Type
   *
   * @param A pi type I -> Type
   * @return inverted A
   */
  private static @NotNull Term invertA(@NotNull Term A) {
    var i = new LocalVar("i");
    var invertedI = FormulaTerm.inv(new RefTerm(i));
    return new LamTerm(
      new Term.Param(i, IntervalTerm.INSTANCE, true),
      AppTerm.make(A, new Arg<>(invertedI, true)));
  }

  // coeInv (A : I -> Type) (phi: I) (u: A 1) : A 0
  private static @NotNull Term coeInv(@NotNull Term A, @NotNull Restr<Term> phi, @NotNull Term u) {
    return AppTerm.make(new CoeTerm(invertA(A), phi), new Arg<>(u, true));
  }

  // coeFillInv
  private static @NotNull Term coeFillInv(@NotNull Term type, @NotNull Restr<Term> phi, @NotNull Term ri) {
    return coeFill(invertA(type), phi, FormulaTerm.inv(ri));
  }
}
