package com.eurobcreative.monroe;

/**
 * Interface for preemptable tasks.
 *
 * @author Ashkan Nikravesh (ashnik@umich.edu)
 */
public interface PreemptibleMeasurementTask {
    boolean pause();

    void updateTotalRunningTime(long duration);
}