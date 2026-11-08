package org.vwtfafa.lockend.core.history;

import java.util.List;

/**
 * Builds plain {@code JSON} and {@code CSV} export texts for history
 * entries. Field names and document shapes match the Paper plugin's exports
 * so artifacts from every platform look identical.
 */
public final class HistoryExport {

    private HistoryExport() {
    }

    /**
     * Builds the JSON document. {@code null} optionals serialize as
     * JSON {@code null}, matching the Paper plugin.
     *
     * @param entries entries to export, oldest first
     * @return the JSON text; never {@code null}
     */
    public static String buildJson(List<HistoryEntry> entries) {
        StringBuilder output = new StringBuilder();
        output.append("[\n");
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry entry = entries.get(i);
            output.append("  {\"timestamp\":\"").append(escape(entry.timestamp().toString()))
                    .append("\",\"actor\":\"").append(escape(entry.actor()))
                    .append("\",\"actorUuid\":").append(jsonString(
                            entry.actorUuid() == null ? null : entry.actorUuid().toString()))
                    .append(",\"action\":\"").append(escape(entry.action()))
                    .append("\",\"source\":\"").append(escape(entry.source()))
                    .append("\",\"subjectUuid\":").append(jsonString(
                            entry.subjectUuid() == null ? null : entry.subjectUuid().toString()))
                    .append(",\"result\":\"").append(escape(entry.result()))
                    .append("\",\"previousState\":").append(jsonBoolean(entry.previousState()))
                    .append(",\"newState\":").append(jsonBoolean(entry.newState())).append("}");
            if (i < entries.size() - 1) {
                output.append(',');
            }
            output.append('\n');
        }
        output.append(']');
        return output.toString();
    }

    /**
     * Builds the CSV document with a fixed header row and quoted values,
     * matching the Paper plugin.
     *
     * @param entries entries to export, oldest first
     * @return the CSV text; never {@code null}
     */
    public static String buildCsv(List<HistoryEntry> entries) {
        StringBuilder output = new StringBuilder();
        output.append("timestamp,actor,actorUuid,action,source,subjectUuid,result,"
                + "previousState,newState\n");
        for (HistoryEntry entry : entries) {
            output.append(csv(entry.timestamp().toString())).append(',')
                    .append(csv(entry.actor())).append(',')
                    .append(csv(entry.actorUuid() == null ? "" : entry.actorUuid().toString())).append(',')
                    .append(csv(entry.action())).append(',')
                    .append(csv(entry.source())).append(',')
                    .append(csv(entry.subjectUuid() == null ? "" : entry.subjectUuid().toString())).append(',')
                    .append(csv(entry.result())).append(',')
                    .append(entry.previousState() == null ? "" : entry.previousState()).append(',')
                    .append(entry.newState() == null ? "" : entry.newState()).append('\n');
        }
        return output.toString();
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static String jsonBoolean(Boolean value) {
        return value == null ? "null" : value.toString();
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
