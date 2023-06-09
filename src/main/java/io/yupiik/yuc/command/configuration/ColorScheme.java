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
package io.yupiik.yuc.command.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

// to get all colors (or install colortest):
// for x in {0..8}; do for i in {30..37}; do for a in {40..47}; do echo -ne "\e[$x;$i;$a""m\$x;$i;$a""m\e[0;37;40m "; done; echo; done; done; echo ""
public record ColorScheme(
        @Property(defaultValue = "\"0\"", documentation = "Reset color suffix. Default: `0`.") String reset,
        @Property(defaultValue = "\"1;37\"", documentation = "Structure color prefix. Default: `1;37`.") String object,
        @Property(defaultValue = "\"1;37\"", documentation = "Structure color prefix. Default: `1;37`.") String array,
        @Property(defaultValue = "\"1;37\"", documentation = "Comma color prefix. Default: `1;37`.") String comma,
        @Property(value = "null", defaultValue = "\"1;30\"", documentation = "null color prefix. Default: `1;30`.") String nullColor,
        @Property(value = "true", defaultValue = "\"3;37\"", documentation = "True color prefix. Default: `3,37`.") String trueColor,
        @Property(value = "false", defaultValue = "\"3;37\"", documentation = "False color prefix. Default: `3;37`.") String falseColor,
        @Property(defaultValue = "\"1;34\"", documentation = "Key color prefix. Default: `1;34`.") String key,
        @Property(defaultValue = "\"0;32\"", documentation = "string color prefix. Default: `0;32`.") String string,
        @Property(defaultValue = "\"3;37\"", documentation = "Number color prefix. Default: `3;37`.") String number
) {
    public static final ColorScheme DEFAULT = new ColorScheme(
            "0", "1;37", "1;37", "1;37",
            "1;30m", "3;37m", "3;37",
            "1;34", "0;32", "3;37");
    public static final ColorScheme NONE = new ColorScheme(
            "", "", "", "", "", "", "", "", "", "");
    private static final String PREFIX = new String(new char[]{27, '['});

    private boolean enabled() {
        return NONE != this;
    }

    public String onArray(final String value) {
        return format(array, value);
    }

    public String onObject(final String value) {
        return format(object, value);
    }

    public String onTrue() {
        return format(trueColor, "true");
    }

    public String onFalse() {
        return format(falseColor, "false");
    }

    public String onString(final String value) {
        return format(string, value);
    }

    public String onKey(final String value) {
        return format(key, value);
    }

    public String onNull() {
        return format(nullColor, "null");
    }

    public String onNumber(final String value) {
        return format(number, value);
    }

    public String onComma() {
        return format(comma, ",");
    }

    private String format(final String marker, final String value) {
        return (enabled() ? PREFIX + marker + 'm' : "") + value + (enabled() ? PREFIX + reset + 'm' : "");
    }
}
