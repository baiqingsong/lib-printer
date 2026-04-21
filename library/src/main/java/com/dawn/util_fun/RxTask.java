package com.dawn.util_fun;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * RxJava3 线程调度封装工具。
 * <p>
 * 各方法每次调用产生独立的 {@link Disposable}，互不共享任务状态；请各自 {@code dispose} 以免泄漏。
 * </p>
 */
public class RxTask {
    private static final String TAG = "RxTask";
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private RxTask() {
        // no instance
    }

    private static void safeMain(Runnable action) {
        if (action == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            handler.post(action);
        }
    }

    /**
     * 执行有返回值的异步任务
     *
     * @param backgroundTask 后台任务（在IO线程执行）
     * @param uiTask         结果处理（在主线程执行）
     * @param <T>            返回值类型
     */
    public static <T> Disposable runAsync(Callable<T> backgroundTask, UiTask<T> uiTask) {
        if (backgroundTask == null) {
            IllegalArgumentException error = new IllegalArgumentException("backgroundTask == null");
            if (uiTask != null) {
                safeMain(() -> uiTask.onError(error));
            } else {
                Log.e(TAG, "runAsync backgroundTask null", error);
            }
            return Disposable.disposed();
        }
        return Single.fromCallable(backgroundTask)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            if (uiTask != null) uiTask.onSuccess(result);
                        },
                        error -> {
                            if (uiTask != null) uiTask.onError(error);
                        }
                );
    }

    /**
     * 执行无返回值的异步任务
     *
     * @param runnable   后台任务（在IO线程执行）
     * @param onComplete 完成回调（在主线程执行）
     */
    public static Disposable runAsync(Runnable runnable, Runnable onComplete) {
        if (runnable == null) {
            Log.e(TAG, "runAsync runnable null");
            return Disposable.disposed();
        }
        return Completable.fromRunnable(runnable)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            if (onComplete != null) onComplete.run();
                        },
                        error -> Log.e(TAG, "runAsync onComplete branch", error)
                );
    }

    /**
     * 执行无返回值的异步任务
     *
     * @param runnable   后台任务（在IO线程执行）
     * @param onComplete 完成回调（在主线程执行）
     */
    public static Disposable runAsync(Runnable runnable, Runnable onComplete, ErrorTask errorTask) {
        if (runnable == null) {
            IllegalArgumentException error = new IllegalArgumentException("runnable == null");
            Log.e(TAG, "runAsync runnable null", error);
            if (errorTask != null) {
                safeMain(() -> errorTask.onError(error));
            }
            return Disposable.disposed();
        }
        return Completable.fromRunnable(runnable)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            if (onComplete != null) onComplete.run();
                        },
                        error -> {
                            Log.e(TAG, "runAsync", error);
                            if (errorTask != null) {
                                errorTask.onError(error);
                            }
                        }
                );
    }

    /**
     * 执行无返回值的异步任务（无 UI 收尾时不在主线程做一次空切换，减少无意义 post）
     *
     * @param runnable 后台任务（在IO线程执行）
     */
    public static Disposable runAsync(Runnable runnable) {
        if (runnable == null) {
            Log.e(TAG, "runAsync runnable null");
            return Disposable.disposed();
        }
        return Completable.fromRunnable(runnable)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> {},
                        error -> Log.e(TAG, "runAsync", error)
                );
    }

    /**
     * 倒计时：主线程 {@link Handler} + 绝对时刻对齐 {@link SystemClock#elapsedRealtime()}。
     * <p>
     * 避免原 {@code Observable.interval + observeOn(mainThread)} 在主线程繁忙时积压、恢复后连续派发导致的<strong>跳秒</strong>；
     * 若已落后，会在一次回调内按 tick 补发中间剩余值，保证秒数不丢（仅可能同帧连续多次 onNext）。
     * </p>
     *
     * @param totalSeconds  总秒数（必须 >= 0）
     * @param interval      间隔时间（秒，必须 > 0）
     * @param countdownTask 倒计时回调接口
     * @return 可取消的Disposable
     */
    public static Disposable countdown(int totalSeconds, int interval, CountdownTask countdownTask) {
        if (totalSeconds < 0) {
            if (countdownTask != null) {
                safeMain(() -> countdownTask.onError(new IllegalArgumentException("totalSeconds must be >= 0")));
            }
            return Disposable.disposed();
        }

        if (interval <= 0) {
            if (countdownTask != null) {
                safeMain(() -> countdownTask.onError(new IllegalArgumentException("interval must be > 0")));
            }
            return Disposable.disposed();
        }

        if (totalSeconds == 0) {
            final AtomicBoolean disposed = new AtomicBoolean(false);
            final Runnable immediate = () -> {
                if (disposed.get()) {
                    return;
                }
                try {
                    if (countdownTask != null) {
                        countdownTask.onNext(0);
                        countdownTask.onComplete();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "countdown immediate callback", t);
                    if (countdownTask != null) {
                        countdownTask.onError(t);
                    }
                } finally {
                    disposed.set(true);
                }
            };
            safeMain(immediate);
            return new Disposable() {
                @Override
                public void dispose() {
                    if (disposed.compareAndSet(false, true)) {
                        handler.removeCallbacks(immediate);
                        if (countdownTask != null) {
                            countdownTask.onDisposed();
                        }
                    }
                }

                @Override
                public boolean isDisposed() {
                    return disposed.get();
                }
            };
        }

        // 与旧实现一致：发射次数 = ceil(totalSeconds/interval) + 1（含起始一刻）
        final long maxTick = (long) Math.ceil((double) totalSeconds / interval);
        final long intervalMs = interval * 1000L;
        final long startMs = SystemClock.elapsedRealtime();
        final AtomicBoolean disposed = new AtomicBoolean(false);
        final int[] lastEmittedTick = new int[]{-1};

        final Runnable tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (disposed.get()) {
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                long targetTick = Math.min((now - startMs) / intervalMs, maxTick);

                try {
                    while (!disposed.get() && lastEmittedTick[0] < targetTick) {
                        lastEmittedTick[0]++;
                        int remaining = (int) Math.max(0L, (long) totalSeconds - (long) lastEmittedTick[0] * interval);
                        if (countdownTask != null) {
                            countdownTask.onNext(remaining);
                        }
                        if (remaining <= 0) {
                            if (countdownTask != null) {
                                countdownTask.onComplete();
                            }
                            disposed.set(true);
                            return;
                        }
                    }

                    if (disposed.get()) {
                        return;
                    }

                    if (lastEmittedTick[0] >= maxTick) {
                        if (countdownTask != null) {
                            countdownTask.onComplete();
                        }
                        disposed.set(true);
                        return;
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "countdown callback", t);
                    if (countdownTask != null) {
                        countdownTask.onError(t);
                    }
                    disposed.set(true);
                    return;
                }

                if (disposed.get()) {
                    return;
                }

                long nextAt = startMs + (lastEmittedTick[0] + 1) * intervalMs;
                long delay = nextAt - SystemClock.elapsedRealtime();
                if (delay <= 0) {
                    handler.post(this);
                } else {
                    handler.postDelayed(this, delay);
                }
            }
        };

        Disposable disposable = new Disposable() {
            @Override
            public void dispose() {
                if (disposed.compareAndSet(false, true)) {
                    handler.removeCallbacks(tickRunnable);
                    if (countdownTask != null) {
                        countdownTask.onDisposed();
                    }
                }
            }

            @Override
            public boolean isDisposed() {
                return disposed.get();
            }
        };

        handler.post(tickRunnable);
        return disposable;
    }

    /**
     * 在主线程执行任务；返回的 {@link Disposable#dispose()} 会尽可能取消尚未执行的回调。
     */
    public static Disposable post(Runnable runnable) {
        if (runnable == null) {
            return Disposable.disposed();
        }
        final AtomicBoolean disposed = new AtomicBoolean(false);
        final Runnable wrapped = () -> {
            if (disposed.get()) {
                return;
            }
            try {
                runnable.run();
            } catch (Throwable t) {
                Log.e(TAG, "post run", t);
            } finally {
                disposed.set(true);
            }
        };
        handler.post(wrapped);
        return new Disposable() {
            @Override
            public void dispose() {
                disposed.set(true);
                handler.removeCallbacks(wrapped);
            }

            @Override
            public boolean isDisposed() {
                return disposed.get();
            }
        };
    }

    /**
     * 延迟在主线程执行任务；{@code dispose} 会同时取消 Rx 定时与 Handler 兜底队列中的同一任务。
     */
    public static Disposable postDelayed(Runnable runnable, long delayMillis) {
        if (runnable == null) {
            return Disposable.disposed();
        }
        if (delayMillis <= 0) {
            return post(runnable);
        }
        final AtomicBoolean disposed = new AtomicBoolean(false);
        final Runnable wrapped = () -> {
            if (disposed.get()) {
                return;
            }
            try {
                runnable.run();
            } catch (Throwable t) {
                Log.e(TAG, "postDelayed run", t);
            } finally {
                disposed.set(true);
            }
        };

        final Disposable[] rx = new Disposable[1];
        rx[0] = Completable.timer(delayMillis, TimeUnit.MILLISECONDS, Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .andThen(Completable.fromRunnable(wrapped))
                .subscribe(
                        () -> {},
                        error -> {
                            Log.e(TAG, "postDelayed, fallback to Handler", error);
                            if (disposed.get()) {
                                return;
                            }
                            try {
                                handler.post(wrapped);
                            } catch (Throwable t) {
                                Log.e(TAG, "postDelayed fallback failed", t);
                            }
                        }
                );

        return new Disposable() {
            @Override
            public void dispose() {
                disposed.set(true);
                Disposable s = rx[0];
                if (s != null && !s.isDisposed()) {
                    s.dispose();
                }
                handler.removeCallbacks(wrapped);
            }

            @Override
            public boolean isDisposed() {
                return disposed.get();
            }
        };
    }

    /**
     * 增强版倒计时回调接口
     */
    public interface CountdownTask {
        void onNext(int remainingSeconds); // 每次间隔回调剩余秒数
        void onComplete();                 // 倒计时完成回调
        void onError(Throwable throwable); // 错误回调
        default void onDisposed() {}       // 新增：资源释放回调（可选实现）
    }

    /**
     * UI线程回调接口
     */
    public interface UiTask<T> {
        void onSuccess(T result);

        void onError(Throwable throwable);
    }

    public interface ErrorTask {
        void onError(Throwable throwable);
    }
}
