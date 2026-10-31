package com.internetshop.webapi.http;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Мінімальний JSON-парсер і серіалізатор (у стандартній бібліотеці Java його немає).
 * Технічна деталь вебшару: Domain та Application про JSON нічого не знають.
 * Об'єкт → Map, масив → List, число → BigDecimal, рядок → String, true/false → Boolean, null → null.
 * <p>
 * ПР6 (DTO): DTO-записи (record) серіалізуються в JSON-об'єкт з полями рівно в тому складі й порядку,
 * що й компоненти запису, тому DTO і є контрактом відповіді. Доменні об'єкти (пакет com.internetshop.domain)
 * серіалізувати заборонено: спроба віддати їх назовні завершується помилкою (клієнт отримає 500 без деталей),
 * а невідомі типи не перетворюються «як вийде» через toString(), щоб випадково не розкрити внутрішні дані.
 */
public final class Json {

    private static final int MAX_DEPTH = 20;
    private static final String DOMAIN_PACKAGE = "com.internetshop.domain";

    private Json() { }

    // ---------- серіалізація ----------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof BigDecimal d) {
            sb.append(d.toPlainString());
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                write(list.get(i), sb);
            }
            sb.append(']');
        } else if (value.getClass().getPackageName().startsWith(DOMAIN_PACKAGE)) {
            throw new IllegalArgumentException("Доменний об'єкт " + value.getClass().getSimpleName()
                    + " не можна передавати за межі застосунку: потрібен DTO.");
        } else if (value instanceof Record r) {
            writeRecord(r, sb);
        } else if (value instanceof UUID || value instanceof Instant) {
            writeString(value.toString(), sb);
        } else {
            throw new IllegalArgumentException("Тип " + value.getClass().getName() + " не підтримується серіалізацією.");
        }
    }

    /** DTO-запис → JSON-об'єкт: ім'я компонента → значення, у порядку оголошення. */
    private static void writeRecord(Record r, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (RecordComponent c : r.getClass().getRecordComponents()) {
            Object componentValue;
            try {
                componentValue = c.getAccessor().invoke(r);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Не вдалося прочитати поле " + c.getName(), e);
            }
            if (!first) sb.append(',');
            first = false;
            writeString(c.getName(), sb);
            sb.append(':');
            write(componentValue, sb);
        }
        sb.append('}');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                // < > & екрануються, щоб JSON не можна було інтерпретувати як HTML
                case '<', '>', '&' -> sb.append(String.format("\\u%04x", (int) c));
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------- розбір ----------

    /** @throws IllegalArgumentException якщо текст не є коректним JSON */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.readValue(0);
        p.skipWhitespace();
        if (!p.atEnd()) throw new IllegalArgumentException("Зайві символи після JSON.");
        return value;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            if (atEnd()) throw new IllegalArgumentException("Неочікуваний кінець JSON.");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw new IllegalArgumentException("Очікувався символ '" + c + "'.");
            pos++;
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) throw new IllegalArgumentException("Занадто глибока вкладеність JSON.");
            char c = peek();
            return switch (c) {
                case '{' -> readObject(depth);
                case '[' -> readArray(depth);
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        Map<String, Object> readObject(int depth) {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, readValue(depth + 1));
                skipWhitespace();
                if (peek() == ',') { pos++; continue; }
                expect('}');
                return map;
            }
        }

        List<Object> readArray(int depth) {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                skipWhitespace();
                list.add(readValue(depth + 1));
                skipWhitespace();
                if (peek() == ',') { pos++; continue; }
                expect(']');
                return list;
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') return sb.toString();
                if (c < 0x20) throw new IllegalArgumentException("Керівний символ у рядку JSON.");
                if (c != '\\') { sb.append(c); continue; }
                char e = peek();
                pos++;
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw new IllegalArgumentException("Некоректна послідовність \\u.");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("Некоректна послідовність \\u.");
                        }
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Некоректне екранування в рядку JSON.");
                }
            }
        }

        Object readLiteral(String word, Object value) {
            if (!s.startsWith(word, pos)) throw new IllegalArgumentException("Некоректне значення JSON.");
            pos += word.length();
            return value;
        }

        BigDecimal readNumber() {
            int start = pos;
            while (!atEnd() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            if (start == pos || pos - start > 40) throw new IllegalArgumentException("Некоректне значення JSON.");
            try {
                return new BigDecimal(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Некоректне число в JSON.");
            }
        }
    }
}
