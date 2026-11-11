package com.danicaju.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser para consultas de búsqueda avanzada estilo Discord:
 * soporta modificadores from:usuario, in:canal, has:link, has:file y texto libre.
 */
public record SearchQueryParser(
        String fromUser,
        String inChannel,
        boolean hasLink,
        boolean hasFile,
        List<String> textTerms
) {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    public static SearchQueryParser parse(String rawQuery) {
        String fromUser = null;
        String inChannel = null;
        boolean hasLink = false;
        boolean hasFile = false;
        List<String> terms = new ArrayList<>();

        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return new SearchQueryParser(null, null, false, false, terms);
        }

        Matcher matcher = TOKEN_PATTERN.matcher(rawQuery.trim());
        while (matcher.find()) {
            String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (token == null || token.isBlank()) {
                continue;
            }
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.startsWith("from:") && token.length() > 5) {
                fromUser = token.substring(5).trim();
            } else if (lower.startsWith("in:") && token.length() > 3) {
                String ch = token.substring(3).trim();
                inChannel = ch.startsWith("#") ? ch : "#" + ch;
            } else if (lower.equals("has:link") || lower.equals("has:url") || lower.equals("has:embed")) {
                hasLink = true;
            } else if (lower.equals("has:file") || lower.equals("has:image") || lower.equals("has:attachment")) {
                hasFile = true;
            } else {
                terms.add(lower);
            }
        }
        return new SearchQueryParser(fromUser, inChannel, hasLink, hasFile, terms);
    }
}
