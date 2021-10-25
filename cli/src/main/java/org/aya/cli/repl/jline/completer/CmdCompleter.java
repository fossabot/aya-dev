// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import kala.collection.View;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.command.Command;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

public class CmdCompleter implements Completer {
  public final @NotNull ImmutableSeq<Candidate> names;

  public CmdCompleter(@NotNull View<String> commandNames) {
    names = commandNames.map(name -> new Candidate(Command.PREFIX + name)).toImmutableSeq();
  }

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    if (line.wordIndex() == 0) {
      var word = line.word();
      if (word.startsWith(Command.PREFIX) || word.isEmpty()) candidates.addAll(names.asJava());
    }
  }
}
