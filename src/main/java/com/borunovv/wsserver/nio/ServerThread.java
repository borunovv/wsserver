package com.borunovv.wsserver.nio;

import com.borunovv.log.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ServerThread {

    private AtomicBoolean running = new AtomicBoolean();
    private AtomicBoolean stopRequested = new AtomicBoolean();

    protected abstract void onThreadStart();

    protected abstract void doThreadIteration();

    protected abstract void onThreadStop();

    protected abstract void onThreadError(Exception e);


    void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Thread already running");
        }
        try {
            new Thread(this::doInThread).start();
        } catch (IllegalThreadStateException e) {
            running.set(false);
            throw e;
        }
    }

    void stop() {
        if (stopRequested.compareAndSet(false, true)) {
            while (running.get()) {
                sensitiveSleep(10);
            }
            stopRequested.set(false);
        }
    }

    boolean isRunning() {
        return running.get();
    }

    boolean isStopRequested() {
        return stopRequested.get();
    }

    private void doInThread() {
        try {
            onThreadStart();
            while (!isStopRequested()) {
                doThreadIteration();
            }
        } catch (Exception e) {
            handleThreadError(e);
        } finally {
            try {
                onThreadStop();
            } catch (Exception e) {
                handleThreadError(e);
            } finally {
                running.set(false);
            }
        }
    }

    private void handleThreadError(Exception e) {
        try {
            onThreadError(e);
        } catch (Exception e2) {
            Log.error("FATAL: Failed to handle thread error", e2);
            Log.error("\n--> Cause error:", e);
        }
    }

    private void sensitiveSleep(int millis) {
        long end = System.currentTimeMillis() + millis;
        while (!stopRequested.get()) {
            long elapsed = end - System.currentTimeMillis();
            if (elapsed <= 0) {
                break;
            }

            try {
                Thread.sleep(Math.min(elapsed, 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
