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
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.yuc.io.IO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Optional.of;
import static java.util.stream.Collectors.joining;

@Command(name = "auto-logs", description = "" +
        "Tries to format ND-JSON output (logs often) automatically testing common patterns. " +
        "Intended to be used in a piped command for now. " +
        "Intent is to make it a one command to browse Kubernetes logs whatever framework issued it.")
public class AutoLogsCommand implements Runnable {
    private final List<LineAnalyzer> analyzers = List.of(
            // yupiik-logging json
            new LineAnalyzer("timestamp", "level", "message", "exception"),
            // spark - its log4j config
            new LineAnalyzer("ts", "level", "msg", "exception"),
            // .net default json console formatter (microsoft logging extension)
            new LineAnalyzer("Timestamp", "LogLevel", "Message", "Exception")
    );

    private final Conf conf;
    private final IO io;
    private final JsonMapper jsonMapper;

    public AutoLogsCommand(final Conf conf, final IO io, final JsonMapper jsonMapper) {
        this.conf = conf;
        this.io = io;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run() {
        final var charset = StandardCharsets.UTF_8;
        try (final var input = io.openInput(jsonMapper, charset, "-", 128, false);
             final var writer = io.openOutput(charset, "-")) {
            String line;
            while ((line = input.readLine()) != null) {
                line = line.strip();
                try {
                    if (isDataLine(line)) {
                        final var data = jsonMapper.fromString(Object.class, line);
                        writer.write(format(data).orElse(line));
                    } else {
                        writer.write(line);
                    }
                } catch (final RuntimeException | IOException e) {
                    writer.write(line);
                }
                writer.write('\n');
                writer.flush();
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Optional<String> format(final Object data) {
        if (!(data instanceof Map<?, ?> map)) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked") final var input = (Map<String, Object>) map;

        for (final var analyzer : analyzers) {
            final var timestamp = analyzer.timestamp().apply(input);
            if (timestamp == null) {
                continue;
            }

            final var level = analyzer.level().apply(input);
            if (level == null) {
                continue;
            }

            final var message = analyzer.message().apply(input);
            if (message == null) {
                continue;
            }

            return of(format(timestamp, level, message, analyzer.exception().apply(input)));
        }

        return Optional.empty();
    }

    private String format(final Object timestamp, final Object level, final Object message, final Object exception) {
        return formatDate(timestamp) + " [" + formatLevel(level) + "] " + message + (exception != null ? formatException(exception) : "");
    }

    private String formatException(final Object exception) {
        if (exception instanceof String s) {
            final var v = s.strip();
            return v.isBlank() ? "" : ('\n' + v);
        }
        if (exception instanceof Map<?, ?> map) {
            final var msg = map.get("msg");
            final var clazz = map.get("class");
            final var stacktrace = map.get("stacktrace");
            if (msg != null && clazz != null) {
                return '\n' + clazz.toString() + ": " + msg + (stacktrace != null ? formatStack(stacktrace) : "");
            }
        }
        return String.valueOf(exception);
    }

    private String formatStack(final Object stacktrace) {
        if (stacktrace instanceof List<?> l && l.stream().allMatch(it -> it instanceof Map)) {
            @SuppressWarnings("unchecked") final var m = (List<Map<String, Object>>) l;
            return m.stream()
                    .map(it -> {
                        final var clazz = it.get("class");
                        final var method = it.get("method");
                        final var file = it.get("file");
                        final var line = it.get("line");
                        if (clazz != null) {
                            return "  at " + clazz + (method != null ? "." + method : "") + (file != null ? " (" + file : "") + (line != null ? ":" + line : "") + (file != null ? ")" : "");
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(joining("\n", "\n", "\n"));
        }
        return String.valueOf(stacktrace);
    }

    // for now we dont change the level name to respect the app semantic but we could normalize it
    private String formatLevel(final Object level) {
        return LogColorScheme.PREFIX + switch (level.toString().toLowerCase(Locale.ROOT)) {
            case "err", "error", "severe" -> conf.colorScheme().error();
            case "warn", "warning" -> conf.colorScheme().warning();
            case "info", "information", "infos" -> conf.colorScheme().info();
            case "fine", "finer", "debug" -> conf.colorScheme().finer();
            // finest but will also match config etc..
            default -> conf.colorScheme().finest();
        } + 'm' + level + LogColorScheme.PREFIX + conf.colorScheme().reset() + 'm';
    }

    private String formatDate(final Object timestamp) {
        return timestamp instanceof Number n ? toInstant(n.longValue()).toString() : timestamp.toString() /*assume format is readable*/;
    }

    private Instant toInstant(final long value) {
        if (value > 1_000_000_000_000_000_000L) {
            return Instant.ofEpochSecond(0, value);
        }
        if (value > 1_000_000_000_000L) {
            return Instant.ofEpochMilli(value);
        }
        return Instant.ofEpochSecond(value);
    }

    private boolean isDataLine(final String line) {
        return (line.startsWith("{") && line.endsWith("}"));
    }

    @RootConfiguration("-")
    public record Conf(
            // reuse JSON formatting but can be worth a specific model
            @Property(value = "color-scheme", defaultValue = "io.yupiik.yuc.command.AutoLogsCommand.LogColorScheme.DEFAULT", documentation = "If `true` output is colorized. Default: `DEFAULT`.") LogColorScheme colorScheme) {
    }

    private record LineAnalyzer(
            Function<Map<String, Object>, Object> timestamp,
            Function<Map<String, Object>, Object> level,
            Function<Map<String, Object>, Object> message,
            Function<Map<String, Object>, Object> exception) {
        private LineAnalyzer(final String timestamp, final String level, final String message, final String exception) {
            this(read(timestamp), read(level), read(message), read(exception));
        }

        private static Function<Map<String, Object>, Object> read(final String key) {
            return map -> map.get(key);
        }
    }

    public record LogColorScheme(
            @Property(defaultValue = "\"0\"", documentation = "Reset color code. Default: `0`.") String reset,
            @Property(defaultValue = "\"1;31\"", documentation = "Error color prefix. Default: `1;31`.") String error,
            @Property(defaultValue = "\"1;33\"", documentation = "Warning color prefix. Default: `1;33`.") String warning,
            @Property(defaultValue = "\"1;37\"", documentation = "Info color prefix. Default: `1;37`.") String info,
            @Property(defaultValue = "\"1;30\"", documentation = "Finer color prefix. Default: `1;30`.") String finer,
            @Property(defaultValue = "\"3;37\"", documentation = "Finest color prefix. Default: `3;37`.") String finest
    ) {
        private static final String PREFIX = new String(new char[]{27, '['});
        public static final LogColorScheme DEFAULT = new LogColorScheme(
                "0",    // reset
                "1;31", // error   — bold red
                "1;33", // warning — bold yellow
                "1;37", // info    — bold white
                "1;30", // finer   — dark gray
                "3;37"  // finest  — italic gray
        );
        public static final LogColorScheme NONE = new LogColorScheme(
                "", "", "", "", "", "");
    }
}
