package com.vikisol.arena.common.exception;

public class BadRequestException extends RuntimeException {

    // MARATHON-BE-2 step 1b item 6: most 400s are one-off messages a human reads and acts on -
    // no code needed. A few (like DOB_REQUIRED) are conditions the frontend itself needs to detect
    // and react to (e.g. routing to the DOB step), so this is null unless a caller opts in.
    private final String code;

    public BadRequestException(String message) {
        this(message, null);
    }

    public BadRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
