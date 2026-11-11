package org.example.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê os comandos {@code INSERT INTO tabela (colunas) VALUES (...), (...);} do script oficial
 * {@code schema_and_data_postgres.sql} e devolve cada linha como um mapa coluna → valor.
 * <p>
 * Serve apenas para popular os dados mock do Checkpoint 1 com exatamente os mesmos registros
 * do banco lineup_santos — o front-end não se conecta ao PostgreSQL (RN022).
 */
public final class SqlSeedReader {

    private static final Pattern INSERT = Pattern.compile(
            "INSERT INTO\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*VALUES\\s*", Pattern.CASE_INSENSITIVE);

    private final Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();

    public SqlSeedReader(String sql) {
        Matcher m = INSERT.matcher(sql);
        int pos = 0;
        while (m.find(pos)) {
            String table = m.group(1).toLowerCase();
            String[] columns = m.group(2).split(",");
            pos = parseValues(sql, m.end(), table, columns);
        }
    }

    public List<Map<String, String>> rows(String table) {
        return tables.getOrDefault(table.toLowerCase(), List.of());
    }

    /** Percorre as tuplas "(...), (...);" respeitando aspas simples e o escape ''. */
    private int parseValues(String sql, int start, String table, String[] columns) {
        List<Map<String, String>> out = tables.computeIfAbsent(table, t -> new ArrayList<>());
        int i = start;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == ';') {
                return i + 1;
            }
            if (c != '(') {
                i++;
                continue;
            }
            List<String> values = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            boolean wasQuoted = false;
            i++;
            for (; i < sql.length(); i++) {
                char ch = sql.charAt(i);
                if (quoted) {
                    if (ch == '\'') {
                        if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                            cur.append('\'');
                            i++;
                        } else {
                            quoted = false;
                        }
                    } else {
                        cur.append(ch);
                    }
                } else if (ch == '\'') {
                    quoted = true;
                    wasQuoted = true;
                } else if (ch == ',' || ch == ')') {
                    String v = wasQuoted ? cur.toString() : cur.toString().strip();
                    values.add("NULL".equalsIgnoreCase(v) && !wasQuoted ? null : v);
                    cur.setLength(0);
                    wasQuoted = false;
                    if (ch == ')') {
                        i++;
                        break;
                    }
                } else if (!Character.isWhitespace(ch) || cur.length() > 0) {
                    cur.append(ch);
                }
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int k = 0; k < columns.length && k < values.size(); k++) {
                row.put(columns[k].strip(), values.get(k));
            }
            out.add(row);
        }
        return i;
    }
}
