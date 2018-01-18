package com.eurobcreative.monroe.exceptions;

/**
 * Subclass of MeasurementError that indicates that
 * a measurement was skipped - a non-error result.
 */
public class MeasurementSkippedException extends MeasurementError {
    public MeasurementSkippedException(String reason) {
        super(reason);
    }
}