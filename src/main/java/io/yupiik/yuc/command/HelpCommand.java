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
package io.yupiik.yuc.command;

import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.yuc.io.IO;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@Command(name = "help", description = "Shows this text.")
public class HelpCommand implements Runnable {
    private final Conf conf;
    private final IO io;

    public HelpCommand(final Conf conf,
                       final IO io) {
        this.conf = conf;
        this.io = io;
    }

    @Override
    public void run() {
        try (final var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(
                getClass().getName().replace('.', '/') + ".txt");
             final var out = io.openOutput(UTF_8, conf.output())) {
            out.write(new String(requireNonNull(in, "Help not found.").readAllBytes(), UTF_8));
            out.write('\n');
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // todo: enable to filter a particular command - for now we have "1" (2 with help) so not that relevant?
    @RootConfiguration("-")
    public record Conf(
            @Property(defaultValue = "\"-\"", documentation = "Output the command should use, default to `stdout` if set to `-` else a file path. Default: `-`.") String output) {
    }
}
