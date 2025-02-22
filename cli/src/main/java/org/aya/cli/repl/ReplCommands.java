// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import com.google.gson.JsonParseException;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.render.RenderOptions;
import org.aya.distill.Codifier;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.repl.Command;
import org.aya.repl.ReplUtil;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface ReplCommands {
  record Code(@NotNull String code) {}

  record Prompt(@NotNull String prompt) {}

  @NotNull Command CHANGE_PROMPT = new Command(ImmutableSeq.of("prompt"), "Change the REPL prompt text") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Prompt argument) {
      var prompt = argument.prompt;
      if (prompt.startsWith("\"") && prompt.endsWith("\"")) prompt = prompt.substring(1, prompt.length() - 1);
      repl.config.prompt = prompt;
      return Result.ok("Changed prompt to `" + prompt + "`", true);
    }
  };

  @NotNull Command SHOW_TYPE = new Command(ImmutableSeq.of("type"), "Show the type of the given expression") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Code code) {
      var type = repl.replCompiler.computeType(code.code(), repl.config.normalizeMode);
      return type != null ? new Result(Output.stdout(repl.render(type)), true)
        : Result.err("Failed to get expression type", true);
    }
  };

  @NotNull Command CODIFY = new Command(ImmutableSeq.of("codify"), "Generate Java code that builds certain function's body") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Code code) {
      var fn = repl.replCompiler.codificationObject(code.code());
      return fn != null ? new Result(Output.stdout(Doc.plain(
        Codifier.sweet(fn).toString())), true)
        : Result.err("Expect just a simple function's (no clauses) name!", true);
    }
  };

  @NotNull Command SHOW_PARSE_TREE = new Command(ImmutableSeq.of("parse-tree"), "Show the parse tree of the given expression") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Code code) {
      var parseTree = new AyaParserImpl(repl.replCompiler.reporter).parseNode(code.code());
      return Result.ok(parseTree.toDebugString(), true);
    }
  };

  @NotNull Command LOAD = new Command(ImmutableSeq.of("load"), "Load a file or library into REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Path path) {
      try {
        repl.replCompiler.loadToContext(path);
      } catch (IOException e) {
        return Result.err("Unable to load file or library: " + e.getLocalizedMessage(), true);
      }
      // SingleFileCompiler would print result to REPL.
      return new Result(Output.empty(), true);
    }
  };

  @NotNull Command CHANGE_CWD = new Command(ImmutableSeq.of("cd"), "Change current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Path path) {
      if (!Files.isDirectory(path)) return Result.err("cd: no such file or directory: " + path, true);
      repl.cwd = path;
      // for jline completer to work properly, but it does not have any effect actually
      System.setProperty("user.dir", path.toAbsolutePath().toString());
      return new Result(Output.empty(), true);
    }
  };

  @NotNull Command PRINT_CWD = new Command(ImmutableSeq.of("pwd"), "Print current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl) {
      return new Result(Output.stdout(repl.cwd.toAbsolutePath().toString()), true);
    }
  };

  @NotNull Command CHANGE_PP_WIDTH = new Command(ImmutableSeq.of("print-width"), "Set printed output width") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable Integer width) {
      if (width == null) return Result.err("print-width: invalid width", true);
      repl.prettyPrintWidth = width;
      return Result.ok("Printed output width set to " + width, true);
    }
  };

  @NotNull Command QUIT = new Command(ImmutableSeq.of("quit", "exit"), "Quit the REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl) {
      return Result.ok(repl.config.silent ? "" :
        "See you space cow woof woof :3", false);
    }
  };

  @NotNull Command CHANGE_NORM_MODE = new Command(ImmutableSeq.of("normalize"), "Set or display the normalization mode") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable NormalizeMode normalizeMode) {
      if (normalizeMode == null) return Result.ok("Normalization mode: " + repl.config.normalizeMode, true);
      else {
        repl.config.normalizeMode = normalizeMode;
        return Result.ok("Normalization mode set to " + normalizeMode, true);
      }
    }
  };

  @NotNull Command TOGGLE_DISTILL = new Command(ImmutableSeq.of("print-toggle"), "Toggle a pretty printing option") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable DistillerOptions.Key key) {
      var builder = new StringBuilder();
      var map = repl.config.distillerOptions.map;
      if (key == null) {
        builder.append("Current pretty printing options:");
        for (var k : DistillerOptions.Key.values())
          builder
            .append("\n").append(k.name()).append(": ")
            .append(map.get(k));
      } else {
        var newValue = !map.get(key);
        map.put(key, newValue);
        builder.append(key.name()).append(" changed to ").append(newValue);
      }
      return Result.ok(builder.toString(), true);
    }
  };

  @NotNull Command TOGGLE_UNICODE = new Command(ImmutableSeq.of("unicode"), "Enable or disable unicode in REPL output") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable Boolean enable) {
      var enableUnicode = enable != null ? enable : !repl.config.enableUnicode;
      repl.config.enableUnicode = enableUnicode;
      return Result.ok("Toggled Unicode to be " + (enableUnicode ? "enabled" : "disabled"), true);
    }
  };

  @NotNull Command HELP = new Command(ImmutableSeq.of("?", "help"), "Describe a selected command or show all commands") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable ReplUtil.HelpItem argument) {
      return ReplUtil.invokeHelp(repl.commandManager, argument);
    }
  };

  @NotNull Command COLOR = new Command(ImmutableSeq.of("color"), "Display the current color scheme or switch to another") {
    private static Command.Result displayColorScheme(@NotNull AyaRepl repl) {
      var options = repl.config.renderOptions;
      var name = options.colorScheme;

      if (name == null) {
        return Command.Result.ok(RenderOptions.DEFAULT_COLOR_SCHEME.name() + " (null detected)", true);
      }
      if (name == RenderOptions.ColorSchemeName.Custom) {
        return Command.Result.ok(RenderOptions.ColorSchemeName.Custom.name() + " (" + options.path + ")", true);
      } else {
        return Command.Result.ok(name.name(), true);
      }
    }

    private static Command.Result invalidColorScheme(@NotNull String argument) {
      var valids = ImmutableArray.from(RenderOptions.ColorSchemeName.values())
        .view()
        .filter(x -> x != RenderOptions.ColorSchemeName.Custom)
        .map(Enum::name)
        .concat(Seq.of("\"<Path>\""))
        .joinToString(", ");

      return Command.Result.err("Invalid color scheme: " + argument + " (valid: " + valids + ")", true);
    }

    /**
     * Goal:
     * <pre>
     * :color
     * Emacs
     * :color intellij
     * IntelliJ
     * :color
     * IntelliJ
     * :color "/home/cirno/vscode/some_color_scheme.json"
     * Custom (/home/cirno/vscode/some_color_scheme.json)
     * :color
     * Custom (/home/cirno/vscode/some_color_scheme.json)
     * :color Custom
     * Invalid color scheme: Custom (valid: Emacs, IntelliJ, "&lt;Path&gt;")
     * </pre>
     */
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable String argument) {
      var nameOrPath = argument == null ? "" : argument.trim();

      // I don't like to extract the common part...
      if (nameOrPath.isEmpty()) {
        // Display color scheme name
        return displayColorScheme(repl);
      } else {
        var old = repl.config.clone();

        if (nameOrPath.startsWith("\"") && nameOrPath.endsWith("\"")) {
          // Path case
          try {
            var path = Paths.get(nameOrPath.substring(1, nameOrPath.length() - 1));

            old.path = path.toAbsolutePath().toString();
            old.colorScheme = RenderOptions.ColorSchemeName.Custom;
          } catch (InvalidPathException e) {
            return invalidColorScheme(nameOrPath);
          }
        } else {
          // Name case
          var matches = ImmutableArray.from(RenderOptions.ColorSchemeName.values())
            .firstOption(x -> x.name().equalsIgnoreCase(nameOrPath));

          if (matches.isEmpty() || matches.get() == RenderOptions.ColorSchemeName.Custom) {
            return invalidColorScheme(nameOrPath);
          }

          old.colorScheme = matches.get();
        }

        try {
          repl.config.setRenderOptions(old);

          return displayColorScheme(repl);
        } catch (IOException | JsonParseException ex) {
          return Command.Result.err("Failed to switching color scheme, cause: " + ex.getLocalizedMessage(), true);
        }
      }
    }
  };

  @NotNull Command STYLE = new Command(ImmutableSeq.of("style"), "Display the current style/Switch to another style") {
    private static @NotNull Command.Result displayStyleFamily(@NotNull AyaRepl repl) {
      var styleFamily = repl.config.renderOptions.styleFamily;

      if (styleFamily == null) {
        return Command.Result.ok(RenderOptions.DEFAULT_STYLE_FAMILY.name() + " (null detected)", true);
      } else {
        return Command.Result.ok(styleFamily.name(), true);
      }
    }

    private static @NotNull Command.Result invalidStyleFamily(@NotNull String argument) {
      var valid = ImmutableArray.from(RenderOptions.StyleFamilyName.values())
        .view()
        .map(Enum::name)
        .joinToString(", ");

      return Command.Result.err("Invalid style family: " + argument + " (valid: " + valid + ")", true);
    }

    /**
     * Goal:
     * <pre>
     * :style
     * Cli
     * :style default
     * Default
     * :style
     * Default
     * :style "/home/foo.json"
     * Invalid style family: "/home/foo.json" (valid: Default, Cli)
     * </pre>
     */
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable String name) {
      if (name == null || name.isBlank()) return displayStyleFamily(repl);
      var matches = ImmutableArray.from(RenderOptions.StyleFamilyName.values())
        .view()
        .firstOption(x -> name.equalsIgnoreCase(x.name()));

      if (matches.isEmpty()) return invalidStyleFamily(name);

      // do switch
      var options = repl.config.clone();
      options.styleFamily = matches.get();

      try {
        repl.config.setRenderOptions(options);

        return displayStyleFamily(repl);
      } catch (IOException ex) {
        return Command.Result.err("Failed to switching style family, cause: " + ex.getLocalizedMessage(), true);
      }
    }
  };
}
