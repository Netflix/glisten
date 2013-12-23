/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.glisten

import com.amazonaws.services.simpleworkflow.flow.common.FlowDefaults
import com.amazonaws.services.simpleworkflow.flow.core.Promise
import com.amazonaws.services.simpleworkflow.flow.core.Settable
import com.amazonaws.services.simpleworkflow.flow.interceptors.RetryPolicy
import groovy.transform.Canonical
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Local implementation sufficient to run unit tests without a real SWF dependency.
 */
@Canonical
class LocalWorkflowOperations<A> extends WorkflowOperations<A> {
    
    private final ExecutorService pool = Executors.newFixedThreadPool(10)

    final A activities

    /** Zero based counter for the number of timers that have been encountered by the workflow */
    int timerCallCounter = 0

    /** Sequence is used in the order that timers are started in your workflow (true indicates a fired timer) */
    List<Boolean> timerHasFiredSequence = []

    static <T> LocalWorkflowOperations<T> of(T activities) {
        new LocalWorkflowOperations<T>(activities)
    }

    @Override
    <T> Promise<T> waitFor(Promise<?> promise, Closure<? extends Promise<T>> work) {
        if (promise.isReady()) {
            return work(promise.get())
        }
        new Settable()
    }

    @Override
    <T> DoTry<T> doTry(Promise promise, Closure<? extends Promise<T>> work) {
        if (promise.isReady()) {
            return new LocalDoTry(work)
        }
        new LocalDoTry({ Promise.Void() })
    }

    @Override
    <T> DoTry<T> doTry(Closure<? extends Promise<T>> work) {
        new LocalDoTry(work)
    }

    @Override
    Promise<Void> timer(long delaySeconds) {
        boolean timerHasFired = delaySeconds == 0
        // check the sequence for firing information
        if (timerHasFiredSequence.size() > timerCallCounter) {
            timerHasFired = timerHasFiredSequence[timerCallCounter]
            timerCallCounter++
        }
        if (timerHasFired) {
            return Promise.Void()
        }
        new Settable() // return a Promise that is not ready
    }

    @Override
    @SuppressWarnings('CatchThrowable')
    <T> Promise<T> retry(RetryPolicy retryPolicy, Closure<? extends Promise<T>> work) {
        int maximumAttempts = FlowDefaults.EXPONENTIAL_RETRY_MAXIMUM_ATTEMPTS
        if (retryPolicy.respondsTo('getMaximumAttempts')) {
            maximumAttempts = retryPolicy.maximumAttempts
        }
        try {
            return work()
        } catch (Throwable t) {
            // Retry after first attempt fails.
            Closure<Promise<T>> retryAttempt = {
                recursingRetry(retryPolicy, work, maximumAttempts, 2, t)
            }
            if (maximumAttempts > 0) {
                return retryAttempt()
            }
            // Use a future for retries if there is no maximum for retry attempts. This allows the retry to be canceled.
            Future<Promise> futurePromiseResult = pool.submit(new Callable<Promise>() {
                @Override
                Promise call() throws Exception {
                    retryAttempt()
                }
            })
            return new FutureAsPromise(futurePromiseResult)
        }
    }

    @SuppressWarnings('CatchThrowable')
    private <T> Promise<T> recursingRetry(RetryPolicy retryPolicy, Closure<? extends Promise<T>> work,
            int maximumAttempts, int attemptCount, Throwable t1) {
        if (Thread.currentThread().interrupted) {
            throw new InterruptedException("Retry was interrupted on attempt ${attemptCount}.")
        }
        boolean abortRetry = t1 instanceof InterruptedException || !retryPolicy.isRetryable(t1) ||
                (maximumAttempts > 0 && attemptCount > maximumAttempts)
        if (abortRetry) {
            throw t1
        }
        try {
            return work()
        } catch (Throwable t2) {
            recursingRetry(retryPolicy, work, maximumAttempts, attemptCount + 1, t2)
        }
    }
}
