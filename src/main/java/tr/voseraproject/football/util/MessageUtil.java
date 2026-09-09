package tr.voseraproject.football.util;

import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {
    private static final Pattern AMP_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern ANGLE_HEX = Pattern.compile("(?i)<#([0-9a-f]{6})>");
    private static final Pattern BRACE_HEX = Pattern.compile("(?i)\\{#([0-9a-f]{6})}");
    private static final Pattern PLAIN_HEX = Pattern.compile("(?i)(?<![0-9a-z])#([0-9a-f]{6})(?![0-9a-z])");
    private static final Pattern AMP_X_HEX = Pattern.compile("(?i)&x(?:&[0-9a-f]){6}");
    private static final Pattern SIMPLE_TAG = Pattern.compile("(?i)</?([a-z_]+)>");

    private static final Map<String, Character> TAG_CODES = Map.ofEntries(
            Map.entry("black", '0'),
            Map.entry("dark_blue", '1'),
            Map.entry("dark_green", '2'),
            Map.entry("dark_aqua", '3'),
            Map.entry("dark_red", '4'),
            Map.entry("dark_purple", '5'),
            Map.entry("gold", '6'),
            Map.entry("gray", '7'),
            Map.entry("grey", '7'),
            Map.entry("dark_gray", '8'),
            Map.entry("dark_grey", '8'),
            Map.entry("blue", '9'),
            Map.entry("green", 'a'),
            Map.entry("aqua", 'b'),
            Map.entry("red", 'c'),
            Map.entry("light_purple", 'd'),
            Map.entry("yellow", 'e'),
            Map.entry("white", 'f'),
            Map.entry("obfuscated", 'k'),
            Map.entry("magic", 'k'),
            Map.entry("bold", 'l'),
            Map.entry("strikethrough", 'm'),
            Map.entry("strike", 'm'),
            Map.entry("underlined", 'n'),
            Map.entry("underline", 'n'),
            Map.entry("italic", 'o'),
            Map.entry("reset", 'r')
    );

    private MessageUtil() {
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = replaceHexPattern(text, AMP_HEX);
        result = replaceHexPattern(result, ANGLE_HEX);
        result = replaceHexPattern(result, BRACE_HEX);
        result = replaceHexPattern(result, PLAIN_HEX);
        result = replaceAmpXHex(result);
        result = replaceSimpleTags(result);
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    public static String replace(String text, Map<String, ?> values) {
        String result = text == null ? "" : text;
        for (var entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return color(result);
    }

    private static String replaceHexPattern(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toMinecraftHex(matcher.group(1))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceAmpXHex(String input) {
        Matcher matcher = AMP_X_HEX.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String raw = matcher.group();
            StringBuilder converted = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                converted.append(c == '&' ? ChatColor.COLOR_CHAR : c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(converted.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceSimpleTags(String input) {
        Matcher matcher = SIMPLE_TAG.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            boolean closing = matcher.group().startsWith("</");
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            Character code = TAG_CODES.get(key);
            String replacement;
            if (closing) {
                replacement = String.valueOf(ChatColor.RESET);
            } else if (code != null) {
                replacement = new String(new char[]{ChatColor.COLOR_CHAR, code});
            } else {
                replacement = matcher.group();
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String toMinecraftHex(String hex) {
        String normalized = hex.toUpperCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(14).append(ChatColor.COLOR_CHAR).append('x');
        for (char c : normalized.toCharArray()) {
            builder.append(ChatColor.COLOR_CHAR).append(c);
        }
        return builder.toString();
    }
}
