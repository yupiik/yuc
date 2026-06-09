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
import java.util.concurrent.TimeUnit;
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
            new LineAnalyzer("timestamp", "level", "message", "exception", null),
            // spark - its log4j config
            new LineAnalyzer("ts", "level", "msg", "exception", null),
            // .net default json console formatter (microsoft logging extension)
            new LineAnalyzer("Timestamp", "LogLevel", "Message", "Exception", null),
            // gcsfuse
            new LineAnalyzer("timestamp", "severity", "message", "exception", null),
            // zap
            new LineAnalyzer("time", "level", "message", "error", null),
            // kubebuilder
            new LineAnalyzer("ts", "level", "msg", "error", "stacktrace"),
            // logstash
            new LineAnalyzer("@timestamp", "level", "message", null, "stack_trace")
    );

    private final Conf conf;
    private final IO io;
    private final JsonMapper jsonMapper;
    private LineAnalyzer cachedAnalyzer;
    private LineAnalyzer lastWinner;
    private int matchCount;

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

    private static final int MIN_STREAK = 5;

    private Optional<String> format(final Object data) {
        if (!(data instanceof Map<?, ?> map)) {
            matchCount = 0;
            return Optional.empty();
        }

        @SuppressWarnings("unchecked") final var input = (Map<String, Object>) map;

        if (cachedAnalyzer != null) {
            final var ts = cachedAnalyzer.timestamp().apply(input);
            if (ts != null) {
                final var lvl = cachedAnalyzer.level().apply(input);
                if (lvl != null) {
                    final var msg = cachedAnalyzer.message().apply(input);
                    if (msg != null) {
                        return of(format(ts, lvl, msg,
                                cachedAnalyzer.exception().apply(input),
                                cachedAnalyzer.stacktrace().apply(input)));
                    }
                }
            }
            cachedAnalyzer = null;
            matchCount = 0;
        }

        LineAnalyzer best = null;
        int bestScore = -1;
        for (final var analyzer : analyzers) {
            int score = 0;
            if (analyzer.timestamp().apply(input) != null) {
                score++;
            }
            if (analyzer.level().apply(input) != null) {
                score++;
            }
            if (analyzer.message().apply(input) != null) {
                score++;
            }
            if (analyzer.exception().apply(input) != null) {
                score++;
            }
            if (analyzer.stacktrace().apply(input) != null) {
                score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = analyzer;
            }
        }
        if (best == null || bestScore < 3 ||
            best.timestamp().apply(input) == null ||
            best.level().apply(input) == null ||
            best.message().apply(input) == null) {
            matchCount = 0;
            return Optional.empty();
        }

        if (best == lastWinner) {
            matchCount++;
            if (matchCount >= MIN_STREAK) {
                cachedAnalyzer = best;
            }
        } else {
            lastWinner = best;
            matchCount = 1;
        }

        return of(format(
                best.timestamp().apply(input),
                best.level().apply(input),
                best.message().apply(input),
                best.exception().apply(input),
                best.stacktrace().apply(input)));
    }

    private String format(final Object timestamp, final Object level, final Object message,
                          final Object exception, final Object stacktrace) {
        return formatDate(timestamp) + " [" + formatLevel(level) + "] " + message
                + (exception != null ? formatException(exception) : "")
                + (stacktrace != null ? formatStacktrace(stacktrace) : "");
    }

    private String formatStacktrace(final Object stacktrace) {
        if (stacktrace instanceof String s) {
            final var v = s.strip();
            return v.isBlank() ? "" : ('\n' + v);
        }
        return formatException(stacktrace);
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
        final var lowerCase = level.toString().toLowerCase(Locale.ROOT);
        return LogColorScheme.PREFIX + switch (lowerCase) {
            case "err", "error", "severe", "critical", "alert", "securityalert" -> conf.colorScheme().error();
            case "warn", "warning" -> conf.colorScheme().warning();
            case "info", "information", "infos" -> conf.colorScheme().info();
            case "fine", "finer", "debug", "verbose" -> conf.colorScheme().finer();
            // finest but will also match config etc..
            default -> lowerCase.contains("error") ? conf.colorScheme().error() : conf.colorScheme().finest();
        } + 'm' + level + LogColorScheme.PREFIX + conf.colorScheme().reset() + 'm';
    }

    private String formatDate(final Object timestamp) {
        if (timestamp instanceof Map<?,?> map && map.get("seconds") instanceof Number seconds) {
            if (map.get("nanos") instanceof Number nanos) {
                return formatDate(TimeUnit.SECONDS.toNanos(seconds.longValue()) + nanos.longValue());
            }
            return formatDate(seconds);
        }
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
            Function<Map<String, Object>, Object> exception,
            Function<Map<String, Object>, Object> stacktrace) {
        private LineAnalyzer(final String timestamp, final String level, final String message,
                             final String exception, final String stacktrace) {
            this(read(timestamp), read(level), read(message), read(exception), read(stacktrace));
        }

        private static Function<Map<String, Object>, Object> read(final String key) {
            if (key == null) {
                return map -> null;
            }
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
