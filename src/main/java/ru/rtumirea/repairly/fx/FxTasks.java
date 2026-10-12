package ru.rtumirea.repairly.fx;

import javafx.concurrent.Task;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Blocking JDBC / filesystem operations run off the JavaFX Application Thread. */
public final class FxTasks implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "repairly-worker");
        thread.setDaemon(true);
        return thread;
    });

    public <T> void run(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> failure.accept(task.getException()));
        executor.execute(task);
    }

    @Override public void close() { executor.shutdownNow(); }
}
