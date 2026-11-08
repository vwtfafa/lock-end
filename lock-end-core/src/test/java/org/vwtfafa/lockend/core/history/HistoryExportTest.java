package org.vwtfafa.lockend.core.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the shared JSON/CSV export shapes against the Paper plugin's
 * export format.
 */
class HistoryExportTest {

    @Test
    void jsonExportMatchesThePaperFieldOrder() {
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID subjectUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        HistoryEntry entry = new HistoryEntry(
                Instant.parse("2026-11-05T12:00:00Z"),
                "Alice", actorUuid, "LOCK", "LOCK", subjectUuid, "SUCCESS", true, null);

        String json = HistoryExport.buildJson(List.of(entry));

        assertEquals("[\n  {\"timestamp\":\"2026-11-05T12:00:00Z\",\"actor\":\"Alice\","
                + "\"actorUuid\":\"00000000-0000-0000-0000-000000000001\","
                + "\"action\":\"LOCK\",\"source\":\"LOCK\","
                + "\"subjectUuid\":\"00000000-0000-0000-0000-000000000002\","
                + "\"result\":\"SUCCESS\",\"previousState\":true,\"newState\":null}\n]", json);
    }

    @Test
    void jsonExportEscapesQuotesAndControlCharacters() {
        HistoryEntry entry = new HistoryEntry(
                Instant.parse("2026-11-05T12:00:00Z"),
                "Ev\"il\nName", "LOCK", "LOCK", true);

        String json = HistoryExport.buildJson(List.of(entry));

        assertEquals("[\n  {\"timestamp\":\"2026-11-05T12:00:00Z\",\"actor\":\"Ev\\\"il\\nName\","
                + "\"actorUuid\":null,\"action\":\"LOCK\",\"source\":\"LOCK\","
                + "\"subjectUuid\":null,\"result\":\"RECORDED\","
                + "\"previousState\":true,\"newState\":null}\n]", json);
    }

    @Test
    void csvExportQuotesValuesAndOmitsNullOptionals() {
        HistoryEntry entry = new HistoryEntry(
                Instant.parse("2026-11-05T12:00:00Z"),
                "Alice", "LOCK", "LOCK", true);

        String csv = HistoryExport.buildCsv(List.of(entry));

        assertEquals("timestamp,actor,actorUuid,action,source,subjectUuid,result,"
                        + "previousState,newState\n"
                        + "\"2026-11-05T12:00:00Z\",\"Alice\",\"\",\"LOCK\",\"LOCK\","
                        + "\"\",\"RECORDED\",true,\n",
                csv);
    }

    @Test
    void emptyExportsProduceHeadersOnlyOrAnEmptyArray() {
        assertEquals("[\n]", HistoryExport.buildJson(List.of()));
        assertEquals("timestamp,actor,actorUuid,action,source,subjectUuid,result,"
                        + "previousState,newState\n",
                HistoryExport.buildCsv(List.of()));
    }
}
