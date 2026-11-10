package org.wildlife.Storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Csv {
    private Csv() {
    }
    public static List<List<String>> parse(String input) throws IOException {
        if (input.startsWith("\uFEFF")) input = input.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int state = 0; // 0=start, 1=plain, 2=quoted, 3=closed
        for (int index = 0; index < input.length(); index++) {
            char c = input.charAt(index);
            if (state == 2) {
                if (c == '"') {
                    if (index + 1 < input.length() && input.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else state = 3;
                } else field.append(c);
            }
        }

        return List.copyOf(rows);
    }
}
