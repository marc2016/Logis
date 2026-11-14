package app.logis.modules.vacationrental;

/**
 * Where a value was found in a source: in its subject or its text, from {@code start} (inclusive) to {@code end}
 * (exclusive), in UTF-16 code units like Java and JavaScript strings.
 */
record Location(Part part, int start, int end) {

    enum Part {
        SUBJECT, TEXT
    }

    Location {
        if (part == null || start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid location " + part + " " + start + ".." + end);
        }
    }
}
