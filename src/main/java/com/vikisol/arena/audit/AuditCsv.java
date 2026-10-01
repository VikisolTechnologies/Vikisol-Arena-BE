package com.vikisol.arena.audit;

import java.util.List;

/** Audit rows as CSV, for the company and platform exports. */
public final class AuditCsv {

    private AuditCsv() {
    }

    public static String write(List<AuditEventResponse> rows) {
        StringBuilder csv = new StringBuilder("Time,Actor,Action,Target,Metadata\n");
        for (AuditEventResponse r : rows) {
            csv.append(cell(r.createdAt())).append(',')
                    .append(cell(r.actorName())).append(',')
                    .append(cell(r.action())).append(',')
                    .append(cell(r.target())).append(',')
                    .append(cell(r.metadata())).append('\n');
        }
        return csv.toString();
    }

    // Quoted, with quotes doubled. A value a spreadsheet would read as a formula (=, +, -, @, tab,
    // carriage return) gets a leading apostrophe, so opening the file can't run it.
    static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
