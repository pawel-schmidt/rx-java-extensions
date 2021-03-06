/*
 * Copyright 2015 Jacek Marchwicki <jacek.marchwicki@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appunite.rx.operators;

import com.appunite.rx.ResponseOrError;
import com.appunite.rx.observables.NetworkObservableProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import rx.Notification;
import rx.Observable;
import rx.Observer;
import rx.Producer;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.Exceptions;
import rx.exceptions.OnErrorThrowable;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.functions.Func3;
import rx.functions.FuncN;
import rx.internal.util.RxRingBuffer;
import rx.subscriptions.Subscriptions;

import static com.appunite.rx.ObservableExtensions.behavior;

public class MoreOperators {

    @Nonnull
    public static <T> Observable.Transformer<T, T> refresh(
            @Nonnull final Observable<Object> refreshSubject) {
        return new Observable.Transformer<T, T>() {
            @Override
            public Observable<T> call(final Observable<T> observable) {
                return refresh(refreshSubject, observable);
            }
        };
    }

    @Nonnull
    private static <T> Observable<T> refresh(@Nonnull Observable<Object> refreshSubject,
                                             @Nonnull final Observable<T> toRefresh) {
        return refreshSubject.startWith((Object)null).switchMap(new Func1<Object, Observable<T>>() {
            @Override
            public Observable<T> call(final Object o) {
                return toRefresh;
            }
        });
    }

    @Nonnull
    public static <T> Observable.Transformer<T, T> cacheWithTimeout(
            @Nonnull final Scheduler scheduler) {
        return new Observable.Transformer<T, T>() {
            @Override
            public Observable<T> call(final Observable<T> observable) {
                return cacheWithTimeout(observable, scheduler);
            }
        };
    }

    @Nonnull
    private static <T> Observable<T> cacheWithTimeout(
            @Nonnull Observable<T> observable,
            @Nonnull Scheduler scheduler) {
        return OnSubscribeRefCountDelayed.create(
                behavior(observable), 5, TimeUnit.SECONDS, scheduler);
    }

    @Nonnull
    public static <T> Observable.Transformer<ResponseOrError<T>, ResponseOrError<T>> repeatOnError(
            @Nonnull final Scheduler scheduler) {
        return
                new Observable.Transformer<ResponseOrError<T>, ResponseOrError<T>>() {
            @Override
            public Observable<ResponseOrError<T>> call(final Observable<ResponseOrError<T>> responseOrErrorObservable) {
                return repeatOnError(responseOrErrorObservable, scheduler);
            }
        };
    }

    @Nonnull
    public static <T> Observable.Transformer<ResponseOrError<T>, ResponseOrError<T>> repeatOnErrorOrNetwork(
            @Nonnull final NetworkObservableProvider networkObservableProvider,
            @Nonnull final Scheduler scheduler) {
        return
                new Observable.Transformer<ResponseOrError<T>, ResponseOrError<T>>() {
                    @Override
                    public Observable<ResponseOrError<T>> call(final Observable<ResponseOrError<T>> responseOrErrorObservable) {
                        return repeatOnErrorOrNetwork(responseOrErrorObservable, networkObservableProvider, scheduler);
                    }
                };
    }

    @Nonnull
    public static <T1, T2, R> Observable.Transformer<T1, R> combineWith(@Nonnull final Observable<T2> observable,
                                                                        @Nonnull final Func2<T1, T2, R> func) {
        return new Observable.Transformer<T1, R>() {
            @Override
            public Observable<R> call(Observable<T1> t1Observable) {
                return Observable.combineLatest(t1Observable, observable, func);
            }
        };
    }

    @Nonnull
    public static <T1, T2, T3, R> Observable.Transformer<T1, R> combineWith(@Nonnull final Observable<T2> observable2,
                                                                            @Nonnull final Observable<T3> observable3,
                                                                        @Nonnull final Func3<T1, T2, T3, R> func) {
        return new Observable.Transformer<T1, R>() {
            @Override
            public Observable<R> call(Observable<T1> t1Observable) {
                return Observable.combineLatest(t1Observable, observable2, observable3, func);
            }
        };
    }

    @Nonnull
    private static <T> Observable<ResponseOrError<T>> repeatOnError(
            @Nonnull final Observable<ResponseOrError<T>> from,
            @Nonnull final Scheduler scheduler) {
        return OnSubscribeRedoWithNext.repeat(from, new Func1<Observable<? extends Notification<ResponseOrError<T>>>, Observable<?>>() {
            @Override
            public Observable<?> call(Observable<? extends Notification<ResponseOrError<T>>> observable) {
                return observable
                        .filter(new Func1<Notification<ResponseOrError<T>>, Boolean>() {
                            @Override
                            public Boolean call(Notification<ResponseOrError<T>> responseOrErrorNotification) {
                                return responseOrErrorNotification.isOnNext();
                            }
                        })
                        .map(new Func1<Notification<ResponseOrError<T>>, ResponseOrError<T>>() {
                            @Override
                            public ResponseOrError<T> call(Notification<ResponseOrError<T>> responseOrErrorNotification) {
                                return responseOrErrorNotification.getValue();
                            }
                        })
                        .scan(0, new Func2<Integer, ResponseOrError<T>, Integer>() {
                            @Override
                            public Integer call(Integer integer, ResponseOrError<T> tResponseOrError) {
                                if (tResponseOrError.isData()) {
                                    return 0;
                                } else {
                                    if (integer == 0) {
                                        return 1;
                                    } else {
                                        return integer * 2;
                                    }
                                }
                            }
                        })
                        .switchMap(new Func1<Integer, Observable<?>>() {
                            @Override
                            public Observable<?> call(Integer integer) {
                                if (integer == 0) {
                                    return Observable.never();
                                }
                                return Observable.timer(integer, TimeUnit.SECONDS, scheduler);
                            }
                        });
            }
        });
    }



    @Nonnull
    private static <T> Observable<ResponseOrError<T>> repeatOnErrorOrNetwork(
            @Nonnull final Observable<ResponseOrError<T>> from,
            @Nonnull final NetworkObservableProvider networkObservableProvider,
            @Nonnull final Scheduler scheduler) {
        return OnSubscribeRedoWithNext.repeat(from, new Func1<Observable<? extends Notification<ResponseOrError<T>>>, Observable<?>>() {
            @Override
            public Observable<?> call(Observable<? extends Notification<ResponseOrError<T>>> observable) {
                return observable
                        .filter(new Func1<Notification<ResponseOrError<T>>, Boolean>() {
                            @Override
                            public Boolean call(Notification<ResponseOrError<T>> responseOrErrorNotification) {
                                return responseOrErrorNotification.isOnNext();
                            }
                        })
                        .map(new Func1<Notification<ResponseOrError<T>>, ResponseOrError<T>>() {
                            @Override
                            public ResponseOrError<T> call(Notification<ResponseOrError<T>> responseOrErrorNotification) {
                                return responseOrErrorNotification.getValue();
                            }
                        })
                        .scan(0, new Func2<Integer, ResponseOrError<T>, Integer>() {
                            @Override
                            public Integer call(Integer integer, ResponseOrError<T> tResponseOrError) {
                                if (tResponseOrError.isData()) {
                                    return 0;
                                } else {
                                    if (integer == 0) {
                                        return 1;
                                    } else {
                                        return integer * 2;
                                    }
                                }
                            }
                        })
                        .switchMap(new Func1<Integer, Observable<?>>() {
                            @Override
                            public Observable<?> call(Integer integer) {
                                if (integer == 0) {
                                    return Observable.never();
                                }
                                final Observable<NetworkObservableProvider.NetworkStatus> networkBecomeActive = networkObservableProvider
                                        .networkObservable()
                                        .skip(1)
                                        .filter(new Func1<NetworkObservableProvider.NetworkStatus, Boolean>() {
                                            @Override
                                            public Boolean call(NetworkObservableProvider.NetworkStatus networkStatus) {
                                                return networkStatus.isNetwork();
                                            }
                                        });
                                final Observable<Long> timeout = Observable.timer(integer, TimeUnit.SECONDS, scheduler);
                                return Observable.amb(networkBecomeActive, timeout);
                            }
                        });
            }
        });
    }

    @Nonnull
    public static <T> Observable.Operator<T, T> callOnNext(@Nonnull Observer<? super T> events) {
        return new OperatorCallOnNext<>(events);
    }

    /**
     * @deprecated use {@link Observable#ignoreElements()} instead.
     */
    @Deprecated
    @Nonnull
    public static <T> Observable.Operator<T, T> ignoreNext() {
        return new Observable.Operator<T, T>() {
            @Override
            public Subscriber<? super T> call(final Subscriber<? super T> subscriber) {
                return new Subscriber<T>(subscriber) {
                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(T obj) {}
                };
            }
        };
    }

    @Nonnull
    public static <T> Observable.Transformer<Object, T> filterAndMap(@Nonnull final Class<T> clazz) {
        return new Observable.Transformer<Object, T>() {
            @Override
            public Observable<T> call(Observable<Object> observable) {
                return observable
                        .filter(new Func1<Object, Boolean>() {
                            @Override
                            public Boolean call(Object o) {
                                return o != null && clazz.isInstance(o);
                            }
                        })
                        .map(new Func1<Object, T>() {
                            @Override
                            public T call(Object o) {
                                //noinspection unchecked
                                return (T) o;
                            }
                        });
            }
        };
    }

    @Nonnull
    public static Func1<Throwable, Object> throwableToIgnoreError() {
        return new Func1<Throwable, Object>() {
            @Override
            public Object call(Throwable throwable) {
                return new Object();
            }
        };
    }

    @Nonnull
    public static <T> Observable.OnSubscribe<T> fromAction(@Nonnull final Func0<T> call) {
        return new Observable.OnSubscribe<T>() {
            @Override
            public void call(final Subscriber<? super T> child) {
                final AtomicBoolean produce = new AtomicBoolean(true);
                child.setProducer(new Producer() {
                    @Override
                    public void request(long n) {
                        if (n <= 0) {
                            return;
                        }
                        if (produce.getAndSet(false)) {
                            produceValue(child);
                        }
                    }
                });
            }

            private void produceValue(Subscriber<? super T> child) {
                try {
                    if (child.isUnsubscribed()) {
                        return;
                    }
                    final T result = call.call();
                    if (child.isUnsubscribed()) {
                        return;
                    }
                    child.onNext(result);
                } catch (Throwable e) {
                    Exceptions.throwIfFatal(e);
                    child.onError(OnErrorThrowable.addValueAsLastCause(e, call));
                }
                if (child.isUnsubscribed()) {
                    return;
                }
                child.onCompleted();
            }
        };
    }

    @Nonnull
    public static <T> Observable.Transformer<List<Observable<T>>, List<T>> newCombineAll() {
        return new Observable.Transformer<List<Observable<T>>, List<T>>() {
            @Override
            public Observable<List<T>> call(Observable<List<Observable<T>>> listObservable) {
                return listObservable.switchMap(new Func1<List<Observable<T>>, Observable<List<T>>>() {
                    @Override
                    public Observable<List<T>> call(List<Observable<T>> observables) {
                        return newCombineAll(observables);
                    }
                });
            }
        };
    }

    @Nonnull
    public static <T> Observable<List<T>> newCombineAll(@Nonnull List<Observable<T>> observables) {
        if (observables.isEmpty()) {
            return Observable.just(Collections.<T>emptyList());
        }
        if (observables.size() > RxRingBuffer.SIZE) {
            // RxJava has some limitation that can only handle up to 128 arguments in combineLast
            // Additionally on android there is a bug so this limit is cut to 16 arguments - on
            // android we will not get any throw so rxjava fail sailent
            final int size = observables.size();
            final int left = size / 2;
            return Observable.combineLatest(
                    newCombineAll(observables.subList(0, left)),
                    newCombineAll(observables.subList(left, size)),
                    new Func2<List<T>, List<T>, List<T>>() {
                        @Override
                        public List<T> call(final List<T> ts, final List<T> ts2) {
                            final ArrayList<T> ret = new ArrayList<>(ts.size() + ts2.size());
                            ret.addAll(ts);
                            ret.addAll(ts2);
                            return Collections.unmodifiableList(ret);
                        }
                    });
        }
        return Observable.combineLatest(observables, new FuncN<List<T>>() {
            @Override
            public List<T> call(final Object... args) {
                final ArrayList<T> ret = new ArrayList<>(args.length);
                for (Object arg : args) {
                    //noinspection unchecked
                    ret.add((T) arg);
                }
                return Collections.unmodifiableList(ret);
            }
        });
    }

    public static final int FRAME_PERIOD = 16;

    @Nonnull
    public static <T> Observable.Transformer<T, T> animatorCompose(
            @Nonnull final Scheduler scheduler,
            final long period, final TimeUnit timeUnit,
            @Nonnull final TypeEvaluator<T> evaluator) {
        return new Observable.Transformer<T, T>() {
            @Override
            public Observable<T> call(final Observable<T> integerObservable) {
                final long periodInMillis = timeUnit.toMillis(period);
                return Observable.create(new Observable.OnSubscribe<T>() {
                    T prevValue;
                    @Override
                    public void call(final Subscriber<? super T> child) {
                        final Scheduler.Worker worker = scheduler.createWorker();
                        child.add(worker);
                        final Subscription sub = integerObservable.subscribe(new Observer<T>() {

                            private Subscription subscription;

                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(final Throwable e) {
                                child.onError(e);
                            }

                            @Override
                            public void onNext(final T endValue) {
                                if (subscription != null) {
                                    subscription.unsubscribe();
                                    subscription = null;
                                }
                                if (prevValue == null) {
                                    prevValue = endValue;
                                    child.onNext(endValue);
                                } else if (!prevValue.equals(endValue)) {
                                    subscription = worker.schedulePeriodically(new Action0() {
                                        long startTime = scheduler.now();
                                        final T startValue = prevValue;

                                        @Override
                                        public void call() {
                                            float percent = (scheduler.now() - startTime) / (float)periodInMillis;
                                            prevValue = evaluator.evaluate(Math.min(percent, 1.0f), startValue, endValue);
                                            child.onNext(prevValue);
                                            if (percent >= 1.0f) {
                                                if (subscription != null) {
                                                    subscription.unsubscribe();
                                                    subscription = null;
                                                }
                                            }
                                        }
                                    }, FRAME_PERIOD, FRAME_PERIOD, TimeUnit.MILLISECONDS);
                                }
                            }
                        });

                        child.add(Subscriptions.create(new Action0() {
                            @Override
                            public void call() {
                                sub.unsubscribe();
                            }
                        }));
                    }
                });
            }
        };
    }
}
