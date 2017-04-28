package com.eurobcreative.monroe.exceptions;

/**
 * Error raised when a measurement fails.
 */
public class MeasurementError extends Exception {
    public MeasurementError(String reason) {
        super(reason);
    }

    public MeasurementError(String reason, Throwable e) {
        super(reason, e);
    }
}