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
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.yuc.cli.OptionAliases;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparing;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class GenerateCliOpts implements Runnable {
    private final Map<String, String> configuration;

    public GenerateCliOpts(final Map<String, String> configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        final var doc = requireNonNull(Path.of(configuration.get("output.doc")), "Missing output.doc");
        final var help = requireNonNull(Path.of(configuration.get("output.help")), "Missing output.help");
        final var aliases = requireNonNull(Path.of(configuration.get("output.aliases")), "Missing output.aliases")
                .resolve("io/yupiik/yuc/generated/Aliases.java");
        try (final var container = docContainer();
             final var commands = container.lookups(CliCommand.class, i -> i.stream().map(Instance::instance).toList())) {
            if (doc.getParent() != null) {
                Files.createDirectories(doc.getParent());
            }
            if (help.getParent() != null) {
                Files.createDirectories(help.getParent());
            }
            if (aliases.getParent() != null) {
                Files.createDirectories(aliases.getParent());
            }

            final var cmds = commands.instance().stream()
                    .map(it -> {
                        final var shortNames = toShortNames(it);
                        return new Cmd(it, shortNames, shortNames.entrySet().stream()
                                .collect(groupingBy(Map.Entry::getValue, mapping(Map.Entry::getKey, toList()))));
                    })
                    .toList();
            Files.writeString(doc, doc(cmds));
            Files.writeString(help, help(cmds));
            Files.writeString(aliases, aliases(cmds));
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private RuntimeContainer docContainer() {
        return ConfiguringContainer.of()
                // we'll generate it so mock it for now
                .register(new ProvidedInstanceBean<>(DefaultScoped.class, OptionAliases.class, () -> command -> Map.of()))
                // handled by Launcher and needed for ArgsProcessor
                .register(new ProvidedInstanceBean<>(DefaultScoped.class, Args.class, () -> new Args(List.of())))
                .start();
    }

    // todo: better formatting for a CLI
    private String help(final List<Cmd> commands) {
        return commands.stream()
                .sorted(comparing(it -> it.command().name()))
                .map(command -> "" + // todo: reflow/reformat, max width = 120? man style?
                        "== " + command.command().name() + "\n" +
                        "\n" +
                        command.command().description().strip() + "\n" +
                        "\n" +
                        "=== Parameters\n" +
                        "\n" +
                        command.command().parameters().stream()
                                .map(p -> {
                                    final var aliases = command.reversedAliases().get(p.cliName());
                                    return p.cliName() + "::\n" + p.description() + (aliases.isEmpty() ?
                                            "" :
                                            (" Aliases: " + aliases.stream().map(it -> "`" + it + "`").collect(joining(", ", "", "."))));
                                })
                                .collect(joining("\n")))
                .collect(joining("\n\n"));
    }

    private String aliases(final List<Cmd> commands) {
        return "" +
                "package io.yupiik.yuc.generated;\n" +
                "\n" +
                "import java.util.Map;\n" +
                "import io.yupiik.yuc.cli.OptionAliases;\n" +
                "import io.yupiik.fusion.framework.api.scope.DefaultScoped;\n" +
                "\n" +
                "@DefaultScoped\n" +
                "public class Aliases implements OptionAliases {\n" +
                "    @Override\n" +
                "    public Map<String, String> aliases(final String command) {\n" +
                "        return switch (command) {\n" +
                commands.stream()
                        .map(command -> {
                            final var inline = command.aliases().size() <= 10;
                            return "" +
                                    "            case \"" + command.command().name() + "\" -> Map.of" + (inline ? "" : "Entries") + "(\n" +
                                    command.aliases().entrySet().stream()
                                            .sorted(Map.Entry.comparingByKey())
                                            .map(e -> {
                                                final var values = "\"" + e.getKey() + "\", \"" + e.getValue() + "\"";
                                                return "                    " + (inline ? values : ("Map.entry(" + values + ")"));
                                            })
                                            .collect(joining(",\n", "", "\n")) +
                                    "            );\n";
                        })
                        .collect(joining()) +
                "            default -> throw new IllegalArgumentException(\"Unknown command '\" + command + \"'\");\n" +
                "        };\n" +
                "    }\n" +
                "}\n" +
                "\n";
    }

    private Map<String, String> toShortNames(final CliCommand<?> command) {
        final var additionalMappings = new HashMap<String, String>();
        command.parameters().stream()
                .map(CliCommand.Parameter::cliName)
                .forEach(name -> {
                    var key = toShortName(name);
                    if (additionalMappings.putIfAbsent(key, name) != null) {
                        // retry with a bit longer name
                        final char lastChar = key.charAt(key.length() - 1);
                        additionalMappings.putIfAbsent((key + name.substring(name.lastIndexOf(lastChar) + 1))
                                .replace("number", "nbr"), name);
                    }
                });
        // ignore case - convention is to use java naming for properties to we can upper case short names too for convenience
        additionalMappings.putAll(additionalMappings.entrySet().stream()
                .collect(toMap(it -> it.getKey().toUpperCase(ROOT), Map.Entry::getValue)));
        return additionalMappings;
    }

    private String doc(final List<Cmd> commands) {
        return commands.stream()
                .sorted(comparing(it -> it.command().name()))
                .map(command -> "" +
                        "== " + command.command().name() + "\n" +
                        "\n" +
                        command.command().description().strip() + "\n" +
                        "\n" +
                        "=== Parameters\n" +
                        "\n" +
                        command.command().parameters().stream()
                                .map(p -> {
                                    final var aliases = command.reversedAliases().get(p.cliName());
                                    return p.cliName() + "::\n" + p.description() + (aliases.isEmpty() ?
                                            "" :
                                            (" Aliases: " + aliases.stream().map(it -> "`" + it + "`").collect(joining(", ", "", "."))));
                                })
                                .collect(joining("\n")))
                .collect(joining("\n\n"));
    }

    private String toSimpleShortName(final String substring) {
        final var out = new StringBuilder().append(substring.charAt(0));
        boolean useNext = false;
        for (final char c : substring.substring(1).toCharArray()) {
            if (Character.isUpperCase(c)) {
                out.append(Character.toLowerCase(c));
            } else if (c == '-') {
                useNext = true;
            } else if (useNext) {
                out.append(Character.toLowerCase(c));
                useNext = false;
            }
        }
        return out.toString();
    }

    private String toShortName(final String name) {
        return name.startsWith("--") ?
                "-" + toSimpleShortName(name.substring("--".length())) :
                name;
    }

    private record Cmd(CliCommand<?> command, Map<String, String> aliases, Map<String, List<String>> reversedAliases) {
    }
}
