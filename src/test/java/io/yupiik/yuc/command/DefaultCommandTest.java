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

import io.yupiik.fusion.testing.launcher.FusionCLITest;
import io.yupiik.fusion.testing.launcher.Stdout;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultCommandTest {
    @FusionCLITest(args = {
            "default", "--input", "src/test/resources/some.json",
            "--append-eol", "false",
            "--output-type", "HANDLEBARS", "--handlebars", "{{a-string}}"})
    void handlebars(final Stdout stdout) {
        assertEquals("value1", stdout.content());
    }

    @FusionCLITest(args = {
            "default", "--input", "src/test/resources/some.json",
            "--append-eol", "false",
            "--output-type", "HANDLEBARS", "--handlebars", "{{json a-string}}"})
    void handlebarsJsonHelper(final Stdout stdout) {
        assertEquals("\"value1\"", stdout.content());
    }

    @FusionCLITest(args = {
            "default", "--input", "src/test/resources/some.json",
            "--append-eol", "false",
            "--output-type", "HANDLEBARS", "--handlebars", "{{json .}}"})
    void handlebarsJsonHelperObj(final Stdout stdout) {
        assertEquals("{\"a-string\":\"value1\",\"a-number\":1234,\"a-boolean\":true,\"a-null\":null,\"a-nested-object\":{\"nested\":true},\"a-list\":[\"s1\"]}", stdout.content());
    }

    @FusionCLITest(args = {
            "default", "--input", "src/test/resources/some.json",
            "--append-eol", "false",
            "--output-type", "HANDLEBARS", "--handlebars", "{{jsonPretty this}}"})
    void handlebarsJsonPrettyHelperObj(final Stdout stdout) {
        assertEquals("""
                {
                  "a-string": "value1",
                  "a-number": 1234,
                  "a-boolean": true,
                  "a-null": null,
                  "a-nested-object": {
                    "nested": true
                  },
                  "a-list": [
                    "s1"
                  ]
                }""", stdout.content());
    }

    @FusionCLITest(args = {
            "default", "--input", "src/test/resources/some.json",
            "--append-eol", "false",
            "--output-type", "HANDLEBARS", "--handlebars", "{{jsonPretty a-nested-object}}"})
    void handlebarsJsonPrettyFilterObj(final Stdout stdout) {
        assertEquals("""
                {
                  "nested": true
                }""", stdout.content());
    }

    @FusionCLITest(args = {"default", "--input", "src/test/resources/some.json", "--colored", "true", "--output-type", "INLINE"})
    void colored(final Stdout stdout) {
        assertEquals("" +
                "\u001B[1;37m{\u001B[0m\u001B[1;34m\"a-string\"\u001B[0m: \u001B[0;32m\"value1\"\u001B[0m\u001B[1;37m," +
                "\u001B[0m \u001B[1;34m\"a-number\"\u001B[0m: \u001B[3;37m1234\u001B[0m\u001B[1;37m," +
                "\u001B[0m \u001B[1;34m\"a-boolean\"\u001B[0m: \u001B[3;37mtrue\u001B[0m\u001B[1;37m," +
                "\u001B[0m \u001B[1;34m\"a-null\"\u001B[0m: \u001B[1;30mnull\u001B[0m\u001B[1;37m," +
                "\u001B[0m \u001B[1;34m\"a-nested-object\"\u001B[0m: \u001B[1;37m{\u001B[0m\u001B[1;34m\"nested\"\u001B[0m: \u001B[3;37mtrue\u001B[0m\u001B[1;37m}\u001B[0m\u001B[1;37m," +
                "\u001B[0m \u001B[1;34m\"a-list\"\u001B[0m: \u001B[1;37m[\u001B[0m\u001B[0;32m\"s1\"\u001B[0m\u001B[1;37m]\u001B[0m\u001B[1;37m}\u001B[0m\n", stdout.content());
    }

    @FusionCLITest(args = {"default", "--input", "src/test/resources/some.json", "--colored", "true"})
    void coloredFormatted(final Stdout stdout) {
        assertEquals("" +
                """
                        [1;37m{[0m
                          [1;34m"a-string"[0m: [0;32m"value1"[0m,
                          [1;34m"a-number"[0m: [3;37m1234[0m,
                          [1;34m"a-boolean"[0m: [3;37mtrue[0m,
                          [1;34m"a-null"[0m: [1;30mnull[0m,
                          [1;34m"a-nested-object"[0m: [1;37m{[0m
                            [1;34m"nested"[0m: [3;37mtrue[0m
                          [1;37m}[0m,
                          [1;34m"a-list"[0m: [1;37m[[0m
                            [0;32m"s1"[0m
                          [1;37m][0m
                        [1;37m}[0m
                        """, stdout.content());
    }

    @FusionCLITest(args = {"default", "--input", "src/test/resources/some.json", "--colored", "false", "--output-type", "INLINE"})
    void inline(final Stdout stdout) {
        assertEquals("{\"a-string\": \"value1\", \"a-number\": 1234, \"a-boolean\": true, \"a-null\": null, \"a-nested-object\": {\"nested\": true}, \"a-list\": [\"s1\"]}\n", stdout.content());
    }

    @FusionCLITest(args = {"default", "--input", "src/test/resources/some.json", "--colored", "false"})
    void formatted(final Stdout stdout) {
        assertEquals("""
                {
                  "a-string": "value1",
                  "a-number": 1234,
                  "a-boolean": true,
                  "a-null": null,
                  "a-nested-object": {
                    "nested": true
                  },
                  "a-list": [
                    "s1"
                  ]
                }
                """, stdout.content());
    }

    @FusionCLITest(args = {"default", "--input", "src/test/resources/simple.xml", "--colored", "false"})
    void formattedXml(final Stdout stdout) {
        assertEquals("""
                {
                  "a-string": "value1",
                  "a-string-cdata": "some value\\n\\n  with\\n\\n  lines",
                  "a-number": "1234",
                  "a-boolean": "true",
                  "a-null": null,
                  "a-nested-object": {
                    "nested": "true"
                  }
                }
                """, stdout.content());
    }
}
