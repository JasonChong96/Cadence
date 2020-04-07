package com.cs4347.cadence.util;

// Adapted from https://stackoverflow.com/questions/39437411/java-priority-in-semaphore
public class PrioLock {
    private boolean _locked;
    private boolean _priorityWaiting;

    public synchronized void lock() throws InterruptedException {
        while(_locked || _priorityWaiting) {
            wait();
        }
        _locked = true;
    }

    public synchronized void lockPriority() throws InterruptedException {
        _priorityWaiting = true;
        try {
            while(_locked) {
                wait();
            }
            _locked = true;
        } finally {
            _priorityWaiting = false;
        }
    }

    public synchronized void unlock() {
        _locked = false;
        notifyAll();
    }
}
