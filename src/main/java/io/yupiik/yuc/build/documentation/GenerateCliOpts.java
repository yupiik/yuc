/*
 * Copyright (c) 2023 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.yuc.build.documentation;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public class GenerateCliOpts implements Runnable {
    private final Map<String, String> configuration;

    public GenerateCliOpts(final Map<String, String> configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        final var doc = requireNonNull(Path.of(configuration.get("output.doc")), "Missing output.doc");
        final var help = requireNonNull(Path.of(configuration.get("output.help")), "Missing output.help");
        try (final var container = ConfiguringContainer.of().start();
             final var commands = container.lookups(CliCommand.class, i -> i.stream().map(Instance::instance).toList())) {
            if (doc.getParent() != null) {
                Files.createDirectories(doc.getParent());
            }
            if (help.getParent() != null) {
                Files.createDirectories(help.getParent());
            }
            Files.writeString(doc, doc(commands.instance()));
            Files.writeString(help, help(commands.instance()));
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    // todo: better formatting for a CLI
    private String help(final List<CliCommand> commands) {
        return commands.stream()
                .sorted(comparing(CliCommand::name))
                .map(it -> (CliCommand<?>) it)
                .map(command -> "" + // todo: reflow, max width = 120?
                        "== " + command.name() + "\n" +
                        "\n" +
                        command.description().strip() + "\n" +
                        "\n" +
                        "=== Parameters\n" +
                        "\n" +
                        command.parameters().stream()
                                .map(p -> p.cliName() + "::\n" + p.description())
                                .collect(joining("\n")))
                .collect(joining("\n\n"));
    }

    private String doc(final List<CliCommand> commands) {
        return commands.stream()
                .sorted(comparing(CliCommand::name))
                .map(it -> (CliCommand<?>) it)
                .map(command -> "" +
                        "== " + command.name() + "\n" +
                        "\n" +
                        command.description().strip() + "\n" +
                        "\n" +
                        "=== Parameters\n" +
                        "\n" +
                        command.parameters().stream()
                                .map(p -> p.cliName() + ":: " + p.description())
                                .collect(joining("\n")))
                .collect(joining("\n\n"));
    }
}
