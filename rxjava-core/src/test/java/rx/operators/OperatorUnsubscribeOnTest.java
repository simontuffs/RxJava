package rx.operators;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.observers.TestObserver;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public class OperatorUnsubscribeOnTest {

    @Test
    public void testUnsubscribeWhenSubscribeOnAndUnsubscribeOnAreOnSameThread() throws InterruptedException {
        UIEventLoopScheduler UI_EVENT_LOOP = new UIEventLoopScheduler();
        try {
            final ThreadSubscription subscription = new ThreadSubscription();
            final AtomicReference<Thread> subscribeThread = new AtomicReference<Thread>();
            Observable<Integer> w = Observable.create(new OnSubscribe<Integer>() {

                @Override
                public void call(Subscriber<? super Integer> t1) {
                    subscribeThread.set(Thread.currentThread());
                    t1.add(subscription);
                    t1.onNext(1);
                    t1.onNext(2);
                    t1.onCompleted();
                }
            });

            TestObserver<Integer> observer = new TestObserver<Integer>();
            w.subscribeOn(UI_EVENT_LOOP).observeOn(Schedulers.computation()).unsubscribeOn(UI_EVENT_LOOP).subscribe(observer);

            Thread unsubscribeThread = subscription.getThread();

            assertNotNull(unsubscribeThread);
            assertNotSame(Thread.currentThread(), unsubscribeThread);

            assertNotNull(subscribeThread.get());
            assertNotSame(Thread.currentThread(), subscribeThread.get());
            // True for Schedulers.newThread()

            System.out.println("unsubscribeThread: " + unsubscribeThread);
            System.out.println("subscribeThread.get(): " + subscribeThread.get());
            assertTrue(unsubscribeThread == UI_EVENT_LOOP.getThread());

            observer.assertReceivedOnNext(Arrays.asList(1, 2));
            observer.assertTerminalEvent();
        } finally {
            UI_EVENT_LOOP.shutdown();
        }
    }

    @Test
    public void testUnsubscribeWhenSubscribeOnAndUnsubscribeOnAreOnDifferentThreads() throws InterruptedException {
        UIEventLoopScheduler UI_EVENT_LOOP = new UIEventLoopScheduler();
        try {
            final ThreadSubscription subscription = new ThreadSubscription();
            final AtomicReference<Thread> subscribeThread = new AtomicReference<Thread>();
            Observable<Integer> w = Observable.create(new OnSubscribe<Integer>() {

                @Override
                public void call(Subscriber<? super Integer> t1) {
                    subscribeThread.set(Thread.currentThread());
                    t1.add(subscription);
                    t1.onNext(1);
                    t1.onNext(2);
                    t1.onCompleted();
                }
            });

            TestObserver<Integer> observer = new TestObserver<Integer>();
            w.subscribeOn(Schedulers.newThread()).observeOn(Schedulers.computation()).unsubscribeOn(UI_EVENT_LOOP).subscribe(observer);

            Thread unsubscribeThread = subscription.getThread();

            assertNotNull(unsubscribeThread);
            assertNotSame(Thread.currentThread(), unsubscribeThread);

            assertNotNull(subscribeThread.get());
            assertNotSame(Thread.currentThread(), subscribeThread.get());
            // True for Schedulers.newThread()

            System.out.println("unsubscribeThread: " + unsubscribeThread);
            System.out.println("subscribeThread.get(): " + subscribeThread.get());
            assertTrue(unsubscribeThread == UI_EVENT_LOOP.getThread());

            observer.assertReceivedOnNext(Arrays.asList(1, 2));
            observer.assertTerminalEvent();
        } finally {
            UI_EVENT_LOOP.shutdown();
        }
    }

    private static class ThreadSubscription implements Subscription {
        private volatile Thread thread;

        private final CountDownLatch latch = new CountDownLatch(1);

        private final Subscription s = Subscriptions.create(new Action0() {

            @Override
            public void call() {
                System.out.println("unsubscribe invoked: " + Thread.currentThread());
                thread = Thread.currentThread();
                latch.countDown();
            }

        });

        @Override
        public void unsubscribe() {
            s.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return s.isUnsubscribed();
        }

        public Thread getThread() throws InterruptedException {
            latch.await();
            return thread;
        }
    }

    public static class UIEventLoopScheduler extends Scheduler {

        private final Scheduler.Inner eventLoop;
        private final Subscription s;
        private volatile Thread t;

        public UIEventLoopScheduler() {
            /*
             * DON'T DO THIS IN PRODUCTION CODE
             */
            final AtomicReference<Scheduler.Inner> innerScheduler = new AtomicReference<Scheduler.Inner>();
            final CountDownLatch latch = new CountDownLatch(1);
            s = Schedulers.newThread().schedule(new Action1<Inner>() {

                @Override
                public void call(Inner inner) {
                    t = Thread.currentThread();
                    innerScheduler.set(inner);
                    latch.countDown();
                }

            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("failed to initialize and get inner scheduler");
            }
            eventLoop = innerScheduler.get();
        }

        @Override
        public Subscription schedule(Action1<Inner> action) {
            eventLoop.schedule(action);
            return Subscriptions.empty();
        }

        @Override
        public Subscription schedule(Action1<Inner> action, long delayTime, TimeUnit unit) {
            eventLoop.schedule(action);
            return Subscriptions.empty();
        }

        public void shutdown() {
            s.unsubscribe();
        }

        public Thread getThread() {
            return t;
        }

    }
}
