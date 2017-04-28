package com.eurobcreative.monroe;

/**
 * Interface for preemptable tasks.
 *
 * @author Ashkan Nikravesh (ashnik@umich.edu)
 */
public interface PreemptibleMeasurementTask {
    boolean pause();

    //returns the task's total running time before it get paused
    long getTotalRunningTime();

    void updateTotalRunningTime(long duration);
}