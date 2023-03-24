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
package io.yupiik.yuc;

import io.yupiik.fusion.framework.api.main.Launcher;

import java.util.stream.Stream;

public final class Yuc {
    private Yuc() {
        // no-op
    }

    public static void main(final String... args) {
        // force default command for now
        final var implicitCommand = "default";
        Launcher.main(args.length == 0 ?
                new String[]{implicitCommand} :
                isCommand(args[0]) ? args : Stream.concat(Stream.of(implicitCommand), Stream.of(args)).toArray(String[]::new));
    }

    private static boolean isCommand(final String name) {
        return "help".equals(name) || "default".equals(name);
    }
}
