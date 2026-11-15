/*
 * This file is part of Jpsonic.
 *
 * Jpsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Jpsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * (C) 2009 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Miscellaneous string utility methods.
 *
 * @author Sindre Mehus
 */
/*
 * /** Provides common string utility operations for Jpsonic.
 *
 * <p>This class serves as the Jpsonic-facing utility API for string processing
 * and formatting. Operations shared with the legacy Subsonic-compatible
 * implementation are delegated to {@link StringUtilBase}.</p>
 *
 * <p>Disallow external instantiation.</p>
 */
public final class StringUtil {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");
    private static final long DURATION_FORMAT_THRESHOLD = 3600;

    /**
     * Disallow external instantiation.
     */
    private StringUtil() {
    }

    public static String formatBytes(long byteCount, Locale locale) {
        return StringUtilBase.formatBytes(byteCount, locale);
    }

    public static @Nullable String utf8HexEncode(String s) {
        return StringUtilBase.utf8HexEncode(s);
    }

    public static @Nullable String utf8HexDecode(String s) throws DecoderException {
        return StringUtilBase.utf8HexDecode(s);
    }

    /**
     * Formats a duration with minutes and seconds, e.g., "4:34" or "93:45"
     */
    public static String formatDurationMSS(long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds must be >= 0");
        }
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Formats a duration with H:MM:SS, e.g., "1:33:45"
     */
    public static String formatDurationHMMSS(final long sec) {
        long seconds = sec;
        long hours = seconds == 0 ? 0 : seconds / 3600;
        seconds -= hours * 3600;
        return String
            .format("%d:%s%s", hours, seconds < 600 ? "0" : "", formatDurationMSS(seconds));
    }

    /**
     * Formats a duration to M:SS or H:MM:SS
     */
    public static String formatDuration(int seconds) {
        if (seconds >= DURATION_FORMAT_THRESHOLD) {
            return formatDurationHMMSS(seconds);
        }
        return formatDurationMSS(seconds);
    }

    /**
     * Splits the input string. White space is interpreted as separator token.
     * Double quotes are interpreted as grouping operator. <br/>
     * For instance, the input <code>"u2 rem "greatest hits""</code> will return an
     * array with three elements: <code>{"u2", "rem", "greatest hits"}</code>
     *
     * @param input The input string.
     *
     * @return Array of elements.
     */
    public static List<String> split(String input) {
        if (input == null) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        Matcher m = SPLIT_PATTERN.matcher(input);
        while (m.find()) {
            if (m.group(1) == null) {
                result.add(m.group(2)); // unquoted string
            } else {
                result.add(m.group(1)); // quoted string
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Reads lines from the given input stream. All lines are trimmed. Empty lines
     * and lines starting with "#" are skipped. The input stream is always closed by
     * this method.
     *
     * @param in The input stream to read from.
     *
     * @return Array of lines.
     *
     * @throws IOException If an I/O error occurs.
     */
    public static List<String> readLines(@NonNull InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> result = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String trimed = line.trim();
                if (!trimed.isEmpty() && trimed.charAt(0) != '#') {
                    result.add(trimed);
                }
            }
            return result;
        }
    }

    /**
     * Converts the given string of whitespace-separated integers to an
     * <code>int</code> array.
     *
     * @param s String consisting of integers separated by whitespace.
     *
     * @return The corresponding array of ints.
     *
     * @throws NumberFormatException If string contains non-parseable text.
     */
    public static @NonNull int[] parseInts(String s) {
        if (s == null) {
            return new int[0];
        }

        return Stream.of(StringUtils.split(s)).mapToInt(Integer::parseInt).toArray();
    }
}
