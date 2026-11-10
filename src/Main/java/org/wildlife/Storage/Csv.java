package org.wildlife.Storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** RFC-4180-style quoting, escaped quotes and embedded newlines; strict field syntax. */
public final class Csv {
    private Csv() { }
    public static List<List<String>> parse(String input) throws IOException {
        if (input.startsWith("\uFEFF")) input = input.substring(1);
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder(); int state = 0; // 0=start, 1=plain, 2=quoted, 3=closed
        for (int index = 0; index < input.length(); index++) {
            char c = input.charAt(index);
            if (state == 2) {
                if (c == '"') {
                    if (index + 1 < input.length() && input.charAt(index + 1) == '"') { field.append('"'); index++; }
                    else state = 3;
                } else field.append(c);
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(field.toString()); field.setLength(0); state = 0;
                if (c != ',') {
                    rows.add(List.copyOf(row)); row.clear();
                    if (c == '\r' && index + 1 < input.length() && input.charAt(index + 1) == '\n') index++;
                }
            } else if (state == 3) {
                throw new IOException("Unexpected character after closing CSV quote at character " + index);
            } else if (c == '"') {
                if (state != 0) throw new IOException("Quote inside unquoted CSV field at character " + index);
                state = 2;
            } else { field.append(c); state = 1; }
        }
        if (state == 2) throw new IOException("Unclosed quoted CSV field.");
        if (state != 0 || field.length() > 0 || !row.isEmpty()) { row.add(field.toString()); rows.add(List.copyOf(row)); }
        return List.copyOf(rows);
    }
    public static String encode(List<? extends List<String>> rows) {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(',');
                out.append('"').append(row.get(i).replace("\"", "\"\"")).append('"');
            }
            out.append('\n');
        }
        return out.toString();
    }
}
