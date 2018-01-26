package com.borunovv.wsserver.nio;

import com.borunovv.contract.Precondition;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Многопоточный обработчик сообщений с очередью.
 */
public class ConcurrentMessageProcessor<T> implements Consumer<T> {

    private int workerThreadsCount = 10;
    private int taskQueueLimit = 10000;
    private long queueAddTaskWaitTimeoutMs = 30 * 1000; // 30 seconds

    private volatile ExecutorService executor;
    private final ConcurrentLinkedQueue<T> taskQueue = new ConcurrentLinkedQueue<>();
    private final IMessageHandler<T> messageHandler;

    // Для статистики
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final AtomicLong rejectedMessageCount = new AtomicLong(0);
    private final AtomicLong totalMessagesCome = new AtomicLong(0);
    private final AtomicLong errorsCount = new AtomicLong(0);
    private final AtomicInteger maxQueueSize = new AtomicInteger(0);

    private volatile boolean stopRequested = false;


    public ConcurrentMessageProcessor(int workerThreadsCount,
                                      int taskQueueLimit,
                                      long queueAddTaskWaitTimeoutMs,
                                      IMessageHandler<T> messageHandler) {
        Precondition.expected(workerThreadsCount > 0, "workerThreadsCount must be > 0");
        Precondition.expected(taskQueueLimit > 0, "taskQueueLimit must be > 0");
        Precondition.expected(queueAddTaskWaitTimeoutMs > 0, "queueAddTaskWaitTimeoutMs must be > 0");
        Precondition.expected(messageHandler != null, "messageHandler is null");

        this.workerThreadsCount = workerThreadsCount;
        this.taskQueueLimit = taskQueueLimit;
        this.queueAddTaskWaitTimeoutMs = queueAddTaskWaitTimeoutMs;
        this.messageHandler = messageHandler;
    }


    @Override
    public void accept(T task) {
        queue(task);
    }

    private boolean queue(T task) {
        Precondition.expected(task != null, "task is null");

        long startWait = System.currentTimeMillis();
        while (taskQueue.size() >= taskQueueLimit && !stopRequested) {
            stoppableWaitTimeout(10);
            if (System.currentTimeMillis() - startWait >= queueAddTaskWaitTimeoutMs) {
                messageHandler.onReject(task);
                rejectedMessageCount.incrementAndGet();
                return false;
            }
        }
        // Тут слегка потоконебезопасно в том плане, что размер очереди может быть превышен максимум
        // на кол-во конкурирующих потоков (их не много, поэтому пофиг).
        // Короче, тут мы не заморачиваемся на точность инварианта "taskQueue.size <= taskQueueLimit"
        // в угоду производительности (не приходится лочить).
        taskQueue.add(task);


        // Для статистики.
        totalMessagesCome.incrementAndGet();
        int queueSize = taskQueue.size();
        if (maxQueueSize.get() < queueSize) {
            // Тут есть race condition, но нас это устраивает.
            // Запись/чтение атомарны, а пропустить пару максимумов не страшно.
            maxQueueSize.set(queueSize);
        }

        return true;
    }

    public void stop() {
        if (executor != null) {
            stopRequested = true;
            executor.shutdown();
            while (!executor.isTerminated()) {
                sleep(10);
            }
            stopRequested = false;
            executor = null;

            activeWorkers.set(0);
            rejectedMessageCount.set(0);
        }
    }

    public void start() {
        stop();

        executor = Executors.newFixedThreadPool(workerThreadsCount);
        for (int i = 0; i < workerThreadsCount; ++i) {
            executor.submit(new Worker());
        }
    }

    public long getTotalMessageCome() {
        return totalMessagesCome.get();
    }

    public int getQueueSize() {
        return taskQueue.size();
    }

    public long getRejectedMessageCount() {
        return rejectedMessageCount.get();
    }

    public int getMaxQueueSize() {
        return maxQueueSize.get();
    }

    public long getErrorsCount() {
        return errorsCount.get();
    }

    private class Worker implements Runnable {
        private int timeoutToWait;

        @Override
        public void run() {
            while (!stopRequested) {
                T task = taskQueue.poll();
                if (task != null) {
                    // В очереди что-то есть, обработаем..
                    timeoutToWait = 0; // Сброс таймаута ожидания.
                    processTask(task);
                } else {
                    // Очередь пуста. Прогрессивный таймаут ожидания.
                    progressiveSleep();
                }
            }
        }

        private void progressiveSleep() {
            stoppableWaitTimeout(timeoutToWait);
            timeoutToWait = Math.min(100, timeoutToWait + 1);
        }

        private void processTask(T task) {
            try {
                activeWorkers.incrementAndGet();
                messageHandler.handle(task);
            } catch (Exception e) {
                try {
                    errorsCount.incrementAndGet();
                    messageHandler.onError(task, e);
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            } finally {
                activeWorkers.decrementAndGet();
            }
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void stoppableWaitTimeout(int ms) {
        try {
            StoppableSleep.sleep(ms, () -> stopRequested);
        } catch (InterruptedException e) {
            errorsCount.incrementAndGet();
            messageHandler.onError(e);
            Thread.currentThread().interrupt();
        }
    }
}