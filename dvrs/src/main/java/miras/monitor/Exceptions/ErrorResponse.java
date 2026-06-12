package miras.monitor.Exceptions;

public record ErrorResponse(
    int status,
    String error,
    String message
) {}
