package com.shutdownhook.sharetoroku.util;

import android.app.Activity;

import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ActivityWorker implements Closeable {

    public interface Worker {
        public boolean doBackground();
        public void doUx();
    }

    public ActivityWorker(Activity activity) {
        this.activity = activity;
        this.executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void close() {

        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                    log.e("Worker failed to terminate cleanly");

                }
            }
        }
        catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void run(final Worker worker) {
        executor.execute(new Runnable() {
            public void run() {
                if (worker.doBackground()) {
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            worker.doUx();
                        }
                    });
                }
            }
        });
    }

    private Activity activity;
    private ExecutorService executor;

    private static final int SHUTDOWN_SECONDS = 1;

    private final static Loggy log = new Loggy(ActivityWorker.class.getName());
}
