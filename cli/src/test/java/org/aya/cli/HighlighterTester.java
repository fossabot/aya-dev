// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import com.intellij.openapi.util.TextRange;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.literate.HighlightInfo;
import org.aya.cli.literate.HighlightInfoHolder;
import org.aya.cli.literate.HighlightInfoType;
import org.aya.cli.literate.utils.HighlighterUtils;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.PrimDef;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.EmptyModuleLoader;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.DelayedReporter;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.*;

public class HighlighterTester {
  public static class PriorityQueueIterator<T> implements Iterator<T> {
    public @NotNull PriorityQueue<T> source;
    public PriorityQueueIterator(@NotNull PriorityQueue<T> source) {
      this.source = new PriorityQueue<>(source);
    }

    @Override
    public boolean hasNext() {
      return ! source.isEmpty();
    }

    @Override
    public T next() {
      return source.remove();
    }
  }

  record Expected(@NotNull TextRange range, @NotNull ExpectedHighlight expected) {}

  public sealed interface ExpectedHighlight {
    @NotNull String display();

    record Def(@Override @NotNull String display, @Nullable String name, @Nullable HighlightInfoType.DefKind kind)
      implements ExpectedHighlight {
    }

    record Ref(@Override @NotNull String display, @Nullable String name, @Nullable HighlightInfoType.DefKind kind) implements ExpectedHighlight {}

    record Keyword(@Override @NotNull String display) implements ExpectedHighlight {}

    record LitString(@NotNull String expected) implements ExpectedHighlight {
      @Override
      public @NotNull String display() {
        return '\"' + expected + '\"';
      }
    }

    record LitInt(int expected) implements ExpectedHighlight {
      @Override
      public @NotNull String display() {
        return Integer.toString(expected);
      }
    }
  }

  public final @NotNull String sourceCode;
  public final @NotNull HighlightInfoHolder actual;

  // NotNull array with Nullable element
  public final Expected[] expected;

  // TODO[hoshino]: Inductive Defined (allow scope)
  // (User-Defined Name, (Unique ID, Nullable Def Kind))
  public final MutableMap<String, Tuple2<String, Option<HighlightInfoType.DefKind>>> defMap = MutableMap.create();
  public final MutableMap<String, Option<HighlightInfoType.DefKind>> defSet = MutableMap.create();

  public HighlighterTester(@NotNull String sourceCode, @NotNull HighlightInfoHolder actual, @NotNull Expected[] expected) {
    this.sourceCode = sourceCode;
    this.actual = actual;
    this.expected = expected;
  }

  public void runTest() {
    runTest(new PriorityQueueIterator<>(actual.getQueue()), Arrays.stream(expected).iterator());
  }

  public void runTest(@NotNull Iterator<HighlightInfo> actuals, @NotNull Iterator<Expected> expecteds) {
    while (actuals.hasNext() && expecteds.hasNext()) {
      var actual = actuals.next();
      var expected = expecteds.next();

      if (expected == null) {
        switch (actual.type()) {
          case HighlightInfoType.Def def -> assertDef(actual.sourcePos(), def);
          case HighlightInfoType.Ref ref -> assertRef(actual.sourcePos(), ref);
          default -> {}
        }

        continue;
      }

      assertEquals(expected.range(), actual.sourcePos());

      var sourcePos = actual.sourcePos();
      var expectedText = expected.expected.display();
      var actualText = sourcePos.substring(sourceCode);

      assertEquals(expectedText, actualText,
        "expected: '" + expectedText + "', but actual: '" + actualText + "' at " + sourcePos);

      switch (actual.type()) {
        case HighlightInfoType.Keyword ignored
          when expected.expected() instanceof ExpectedHighlight.Keyword-> {
        }

        case HighlightInfoType.LitInt litInt
          when expected.expected() instanceof ExpectedHighlight.LitInt -> {
        }

        case HighlightInfoType.LitString litString
          when expected.expected() instanceof ExpectedHighlight.LitString -> {
        }

        case HighlightInfoType.Def def
          when expected.expected() instanceof ExpectedHighlight.Def expectedDef ->
          assertDef(sourcePos, def, expectedDef);

        case HighlightInfoType.Ref ref
          when expected.expected() instanceof ExpectedHighlight.Ref expectedRef ->
          assertRef(sourcePos, ref, expectedRef);

        case HighlightInfoType.Error error -> throw new UnsupportedOperationException("unreachable");   // TODO[hoshino]: unreachable?

        default -> fail("expected: " + expected.getClass().getSimpleName() + ", but actual: " + actual.getClass().getSimpleName());
      }
    }

    if (actuals.hasNext() || expecteds.hasNext()) {
      var noMoreExpected = actuals.hasNext();

      if (noMoreExpected) {
        fail("No more 'expecteds' data");
      } else {
        fail("No more 'actuals' data");
      }
    }
  }

  public void assertDef(@NotNull TextRange sourcePos, @NotNull HighlightInfoType.Def def) {
    var existDef = defSet.containsKey(def.target());
    assertFalse(existDef, "Duplicated def: " + def.target() + " at " + sourcePos);

    defSet.put(def.target(), Option.ofNullable(def.kind()));
  }

  public void assertDef(@NotNull TextRange sourcePos, @NotNull HighlightInfoType.Def actualDef, @NotNull ExpectedHighlight.Def expectedDef) {
    assertDef(sourcePos, actualDef);

    assertEquals(expectedDef.kind(), actualDef.kind());

    var name = expectedDef.name();

    if (name != null) {
      var existName = defMap.getOption(name);
      assertFalse(existName.isDefined(), "Duplicated name: " + expectedDef.name());

      defMap.put(name, Tuple.of(actualDef.target(), Option.ofNullable(actualDef.kind())));
    }
  }

  public void assertRef(@NotNull TextRange sourcePos, @NotNull HighlightInfoType.Ref ref) {
    var defData = defSet.getOrNull(ref.target());

    assertNotNull(defData, "Expected def: " + ref.target() + " at " + sourcePos);
    assertEquals(defData.getOrNull(), ref.kind());
  }

  public void assertRef(@NotNull TextRange sourcePos, @NotNull HighlightInfoType.Ref actualRef, @NotNull ExpectedHighlight.Ref expectedRef) {
    assertRef(sourcePos, actualRef);

    var name = expectedRef.name();

    if (name != null) {
      var existName = defMap.getOption(name);
      assertTrue(existName.isDefined(), "Undefined name: " + expectedRef.name());

      var defData = existName.get();
      assertEquals(defData._2.getOrNull(), actualRef.kind());
    }

    assertEquals(actualRef.kind(), expectedRef.kind());
  }

  public static void highlightAndTest(@Language("Aya") @NotNull String code, @Nullable Expected... expected) {
    var fileName = "null";
    var reporter = newReporter();

    var lexer = AyaParserDefinitionBase.createLexer(false);
    lexer.reset(code, 0, code.length(), 0);
    var tokens = lexer.allTheWayDown();

    var parser = new AyaParserImpl(reporter);
    var stmts = parser.program(new SourceFile(fileName, Option.none(), code));
    var resolveInfo = new ResolveInfo(
      new PrimDef.Factory(),
      new EmptyContext(reporter, Path.of(".")).derive(fileName),
      stmts, new AyaBinOpSet(reporter)
    );

    Stmt.resolveWithoutDesugar(stmts, resolveInfo, EmptyModuleLoader.INSTANCE);

    var anyError = reporter.anyError();
    reporter.close();

    if (anyError) {
      throw new AssertionError("expected: no error, but actual: error");
    }

    var result = HighlighterUtils.highlight(stmts, DistillerOptions.debug());
    HighlighterUtils.highlightKeywords(result, tokens);

    doTest(code, result, expected);
  }

  public static void doTest(@NotNull String sourceCode, @NotNull HighlightInfoHolder actual, @Nullable Expected... expected) {
    new HighlighterTester(sourceCode, actual, expected).runTest();
  }

  public static @NotNull DelayedReporter newReporter() {
    return new DelayedReporter(problem ->
      System.err.println(ThrowingReporter.errorMessage(
        problem, DistillerOptions.informative(), false, false, 80))
    );
  }

  /// region Helper

  public static @NotNull Expected keyword(int begin, int end, @NotNull String display) {
    return new Expected(new TextRange(begin, end), new ExpectedHighlight.Keyword(display));
  }

  public static @NotNull Expected def(int begin, int end, @NotNull String display, @Nullable String name, @Nullable HighlightInfoType.DefKind defKind) {
    return new Expected(new TextRange(begin, end), new ExpectedHighlight.Def(display, name, defKind));
  }

  public static @NotNull Expected def(int begin, int end, @NotNull String display, @Nullable HighlightInfoType.DefKind defKind) {
    return def(begin, end, display, null, defKind);
  }

  public static @NotNull Expected localDef(int begin, int end, @NotNull String display, @Nullable String name) {
    return def(begin, end, display, name, HighlightInfoType.DefKind.Local);
  }

  public static @NotNull Expected localDef(int begin, int end, @NotNull String display) {
    return localDef(begin, end, display, null);
  }

  public static @NotNull Expected ref(int begin, int end, @NotNull String display, @Nullable String name, HighlightInfoType.DefKind defKind) {
    return new Expected(new TextRange(begin, end), new ExpectedHighlight.Ref(display, name, defKind));
  }

  public static @NotNull Expected ref(int begin, int end, @NotNull String display, HighlightInfoType.DefKind defKind) {
    return ref(begin, end, display, null, defKind);
  }

  public static @NotNull Expected localRef(int begin, int end, @NotNull String display, @Nullable String name) {
    return ref(begin, end, display, name, HighlightInfoType.DefKind.Local);
  }

  public static @NotNull Expected localRef(int begin, int end, @NotNull String display) {
    return localRef(begin, end, display, null);
  }

  public static @NotNull Expected litInt(int begin, int end, int display) {
    return new Expected(new TextRange(begin, end), new ExpectedHighlight.LitInt(display));
  }

  @Contract(" -> null")
  public static @Nullable Expected whatever() {
    return null;
  }

  /// endregion
}
