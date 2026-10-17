package com.gregory.truelle.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lecture / écriture d'objets JSON « plats » : toutes les valeurs sont des textes.
 * Volontairement minimal et sans dépendance Android, pour pouvoir être testé sur ordinateur.
 */
public final class JsonLite {

    private JsonLite() {}

    public static String write(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(",\n");
            first = false;
            sb.append("  ");
            quote(sb, e.getKey());
            sb.append(": ");
            quote(sb, e.getValue());
        }
        return sb.append("\n}\n").toString();
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    /** Lit un objet JSON plat. Les valeurs non textuelles (nombres, booléens) sont gardées en texte. */
    public static Map<String, String> read(String json) {
        Parser p = new Parser(json);
        return p.object();
    }

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) { this.s = s; }

        Map<String, String> object() {
            Map<String, String> map = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') { i++; return map; }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                skipWs();
                String value = peek() == '"' ? string() : bare();
                map.put(key, value);
                skipWs();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw new IllegalArgumentException("JSON invalide à la position " + i);
            }
            return map;
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private String bare() {
            int start = i;
            while (i < s.length() && ",}\n\r\t ".indexOf(s.charAt(i)) < 0) i++;
            return s.substring(start, i);
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("JSON incomplet");
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw new IllegalArgumentException("Caractère « " + c + " » attendu à la position " + (i - 1));
        }
    }
}
