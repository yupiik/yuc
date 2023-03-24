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
package io.yupiik.yuc.cli;

import io.yupiik.fusion.cli.internal.CliCommand;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@DefaultScoped
public class ArgsProcessor {
    private final RuntimeContainer container;
    private final OptionAliases aliases;

    public ArgsProcessor(final RuntimeContainer container, final OptionAliases aliases) {
        this.container = container;
        this.aliases = aliases;
    }

    public void onStart(@OnEvent final Start start) {
        try (final var original = container.lookup(Args.class)) {
            final var overriden = new Args(process(original.instance().args()));
            container.getBeans().doRegister(new BaseBean<Args>(Args.class, DefaultScoped.class, 1001, Map.of()) {
                @Override
                public Args create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                    return overriden;
                }
            });
        }
    }

    private List<String> process(final List<String> args) {
        final var commands = knownCommands();
        final List<String> withCommand = enforceCommand(args, commands);
        return withCommand.size() == 1 ?
                withCommand :
                Stream.concat(
                                Stream.of(withCommand.get(0)),
                                withAliases(
                                        requireNonNull(commands.get(withCommand.get(0)), () -> "Unknown command '" + withCommand.get(0) + "'"),
                                        withCommand.stream().skip(1)))
                        .toList();
    }

    private List<String> enforceCommand(final List<String> args, final Map<String, CliCommand<?>> commands) {
        return args.size() == 0 ?
                List.of("default") :
                commands.containsKey(args.get(0)) ?
                        args :
                        Stream.concat(Stream.of("default"), args.stream()).toList();
    }

    private Stream<String> withAliases(final CliCommand<?> command, final Stream<String> args) {
        final var additionalMappings = aliases.aliases(command.name());
        return args.map(it -> additionalMappings.getOrDefault(it, it));
    }

    private Map<String, CliCommand<?>> knownCommands() {
        try (final var cmds = container.lookups(CliCommand.class, i -> i.stream().map(Instance::instance).toList())) {
            return cmds.instance().stream().map(it -> (CliCommand<?>) it).collect(toMap(CliCommand::name, identity()));
        }
    }
}
