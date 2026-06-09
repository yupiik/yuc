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

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.yuc.io.IO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class AutoLogsCommandTest {
    private static final String ESC = "\u001B[";

    @Test
    void yupiikLoggingAnalyzer() {
        assertEquals(
                "1970-01-01T00:00:01Z [" + ESC + "mINFO" + ESC + "m] test message\nerr\n",
                execute("{\"timestamp\":1,\"level\":\"INFO\",\"message\":\"test message\",\"exception\":\"err\"}"));
    }

    @Test
    void sparkLog4jAnalyzer() {
        assertEquals(
                "1970-01-01T00:00:01Z [" + ESC + "mWARN" + ESC + "m] test message\n",
                execute("{\"ts\":1,\"level\":\"WARN\",\"msg\":\"test message\"}"));
    }

    @Test
    void dotNetAnalyzer() {
        assertEquals(
                "1970-01-01T00:00:01Z [" + ESC + "mERROR" + ESC + "m] test message\nerr\n",
                execute("{\"Timestamp\":1,\"LogLevel\":\"ERROR\",\"Message\":\"test message\",\"Exception\":\"err\"}"));
    }

    @Test
    void gcsfuseAnalyzer() {
        assertEquals(
                "1970-01-01T00:00:01Z [" + ESC + "mDEBUG" + ESC + "m] test message\n",
                execute("{\"timestamp\":1,\"severity\":\"DEBUG\",\"message\":\"test message\"}"));
    }

    @Test
    void zapAnalyzer() {
        assertEquals(
                "1970-01-01T00:00:01Z [" + ESC + "mINFO" + ESC + "m] test message\n" + "an error occurred\n",
                execute("{\"time\":1,\"level\":\"INFO\",\"message\":\"test message\",\"error\":\"an error occurred\"}"));
    }

    @Test
    void logstashAnalyzer() {
        assertEquals(
                "1970-01-01T00:00:01Z [" + ESC + "mERROR" + ESC + "m] test message\n" + "stacktrace content\n",
                execute("{\"@timestamp\":1,\"level\":\"ERROR\",\"message\":\"test message\",\"stack_trace\":\"stacktrace content\"}"));
    }

    @Test
    void structuredException() {
        assertEquals(
                "1970-01-01T00:00:01Z [" + ESC + "mERROR" + ESC + "m] test message\n" +
                        "java.lang.RuntimeException: test error\n" +
                        "  at x.Y.z (Y.java:5)\n" +
                        "  at a.b.main (Main.java:42)\n\n",
                execute("{\"timestamp\":1,\"level\":\"ERROR\",\"message\":\"test message\"," +
                        "\"exception\":{\"class\":\"java.lang.RuntimeException\",\"msg\":\"test error\"," +
                        "\"stacktrace\":[{\"class\":\"x.Y\",\"method\":\"z\",\"file\":\"Y.java\",\"line\":5}," +
                        "{\"class\":\"a.b\",\"method\":\"main\",\"file\":\"Main.java\",\"line\":42}]}}"));
    }

    @Test
    void kubeBuilder() {
        final var instant = Instant.ofEpochSecond(1780996046L);
        assertEquals(
                instant + " [" + ESC + "merror" + ESC + "m] Reconciler error\n" +
                        "foo: not found\n" +
                        "sigs.k8s.io/controller-runtime/pkg/internal/controller.(*Controller[...]).reconcileHandler\n" +
                        "\t/home/vsts/go/pkg/mod/sigs.k8s.io/controller-runtime@v0.21.0/pkg/internal/controller/controller.go:353\n" +
                        "sigs.k8s.io/controller-runtime/pkg/internal/controller.(*Controller[...]).processNextWorkItem\n" +
                        "\t/home/vsts/go/pkg/mod/sigs.k8s.io/controller-runtime@v0.21.0/pkg/internal/controller/controller.go:300\n" +
                        "sigs.k8s.io/controller-runtime/pkg/internal/controller.(*Controller[...]).Start.func2.1\n" +
                        "\t/home/vsts/go/pkg/mod/sigs.k8s.io/controller-runtime@v0.21.0/pkg/internal/controller/controller.go:202\n",
                execute("{\"level\":\"error\",\"ts\":1780996046.8853111,\"msg\":\"Reconciler error\"," +
                        "\"controller\":\"mycontroller\"," +
                        "\"controllerGroup\":\"foo.yupiik.io\"," +
                        "\"controllerKind\":\"My\"," +
                        "\"My\":{\"name\":\"demo\",\"namespace\":\"yupiik\"}," +
                        "\"namespace\":\"yupiik\",\"name\":\"demo\"," +
                        "\"reconcileID\":\"12096fcb-9c83-4562-9106-86d61f077944\"," +
                        "\"error\":\"foo: not found\"," +
                        "\"stacktrace\":\"sigs.k8s.io/controller-runtime/pkg/internal/controller.(*Controller[...]).reconcileHandler\\n" +
                        "\\t/home/vsts/go/pkg/mod/sigs.k8s.io/controller-runtime@v0.21.0/pkg/internal/controller/controller.go:353\\n" +
                        "sigs.k8s.io/controller-runtime/pkg/internal/controller.(*Controller[...]).processNextWorkItem\\n" +
                        "\\t/home/vsts/go/pkg/mod/sigs.k8s.io/controller-runtime@v0.21.0/pkg/internal/controller/controller.go:300\\n" +
                        "sigs.k8s.io/controller-runtime/pkg/internal/controller.(*Controller[...]).Start.func2.1\\n" +
                        "\\t/home/vsts/go/pkg/mod/sigs.k8s.io/controller-runtime@v0.21.0/pkg/internal/controller/controller.go:202\"}"));
    }

    @Test
    void nonJsonPassthrough() {
        assertEquals("plain text log line\n", execute("plain text log line"));
    }

    @Test
    void nonMatchingJsonPassthrough() {
        assertEquals("{\"foo\":\"bar\"}\n", execute("{\"foo\":\"bar\"}"));
    }

    @Test
    void malformedJsonPassthrough() {
        assertEquals("{{invalid}\n", execute("{{invalid}"));
    }

    private String execute(final String input) {
        final var output = new StringWriter();
        final var jsonMapper = new JsonMapperImpl(List.of(), c -> Optional.empty());
        final var io = new IO() {
            @Override
            public BufferedReader openInput(final JsonMapper jm, final Charset c, final String value,
                                            final int bufferSize, final boolean autoList) {
                return new BufferedReader(new StringReader(input));
            }

            @Override
            public Writer openOutput(final Charset c, final String value) {
                return output;
            }
        };
        new AutoLogsCommand(
                new AutoLogsCommand.Conf(AutoLogsCommand.LogColorScheme.NONE), io, jsonMapper)
                .run();
        return output.toString();
    }
}
