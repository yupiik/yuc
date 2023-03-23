package io.yupiik.yuc.command.configuration;

import io.yupiik.fusion.framework.build.api.configuration.Property;

// to get all colors (or install colortest):
// for x in {0..8}; do for i in {30..37}; do for a in {40..47}; do echo -ne "\e[$x;$i;$a""m\$x;$i;$a""m\e[0;37;40m "; done; echo; done; done; echo ""
public record ColorScheme(
        @Property(defaultValue = "\"0\"", documentation = "Reset color suffix.") String reset,
        @Property(defaultValue = "\"1;37\"", documentation = "Structure color prefix.") String object,
        @Property(defaultValue = "\"1;37\"", documentation = "Structure color prefix.") String array,
        @Property(defaultValue = "\"1;37\"", documentation = "Comma color prefix.") String comma,
        @Property(value = "null", defaultValue = "\"1;30\"", documentation = "null color prefix.") String nullColor,
        @Property(value = "true", defaultValue = "\"3;37\"", documentation = "True color prefix.") String trueColor,
        @Property(value = "false", defaultValue = "\"3;37\"", documentation = "False color prefix.") String falseColor,
        @Property(defaultValue = "\"1;34\"", documentation = "Key color prefix.") String key,
        @Property(defaultValue = "\"0;32\"", documentation = "string color prefix.") String string,
        @Property(defaultValue = "\"3;37\"", documentation = "Number color prefix.") String number
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
