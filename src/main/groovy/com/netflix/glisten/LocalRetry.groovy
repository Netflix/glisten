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
import com.amazonaws.services.simpleworkflow.flow.interceptors.RetryPolicy
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import java.util.concurrent.Callable

/**
 * Local implementation of retry logic sufficient to run unit tests without a real SWF dependency.
 */
class LocalRetry<T> extends Promise<T> {

    private final ListeningExecutorService executor
    private final ScopedTries scopedTries
    private final PromisingResult result = new PromisingResult('LocalRetry')

    private ListenableFuture<Promise<T>> future
    private boolean interrupted = false

    protected Throwable unretriableError

    LocalRetry(LocalWorkflowOperations workflowOperations) {
        this.executor = workflowOperations.executor
        scopedTries = new ScopedTries(workflowOperations)
    }

    @Override
    T get() {
        if (unretriableError) {
            throw unretriableError
        }
        result.get()
    }

    @Override
    boolean isReady() {
        if (interrupted || unretriableError) { return false }
        result.isReady()
    }

    /** Cancels this retry and everything nested inside it. */
    void cancel() {
        interrupted = true
        result.description = 'Canceled LocalRetry'
        scopedTries.cancel()
        future?.cancel(true)
    }

    /** Indicates if this retry is done. Done means that all nested operations are done and it is complete due to being
     * ready, canceled or failed. */
    boolean isDone() {
        if (!scopedTries.allDone()) { return false }
        if (interrupted || unretriableError) { return true }
        isReady()
    }

    /**
     * Retries work based on the retryPolicy.
     *
     * @param retryPolicy determines how the work will be retried
     * @param work to be retried
     * @return a Promise of the result from the retry logic
     */
    @SuppressWarnings('CatchThrowable')
    Promise<T> retry(RetryPolicy retryPolicy, Closure<? extends Promise<T>> work) {
        int maximumAttempts = FlowDefaults.EXPONENTIAL_RETRY_MAXIMUM_ATTEMPTS
        if (retryPolicy.respondsTo('getMaximumAttempts')) {
            maximumAttempts = retryPolicy.maximumAttempts
        }
        scopedTries.interceptMethodCallsInClosure(work)
        try {
            result.chain(work())
            return result
        } catch (Throwable t) {
            // Retry after first attempt fails.
            Closure<Promise<T>> retryAttempt = {
                recursingRetry(retryPolicy, work, maximumAttempts, 2, t)
            }
            if (maximumAttempts > 0) {
                return retryAttempt()
            }
            // Use a future for retries if there is no maximum for retry attempts. This allows the retry to be canceled.
            future = executor.submit(new Callable<Promise<T>>() {
                @Override
                Promise<T> call() throws Exception {
                    retryAttempt()
                }
            })
            // Ensure that the result is updated when the future computation has completed.
            future.addListener({
                def fResult = future.get()
                result.chain(fResult)
            }, executor)
        }
        this
    }

    @SuppressWarnings('CatchThrowable')
    private Promise<T> recursingRetry(RetryPolicy retryPolicy, Closure<? extends Promise<T>> work,
                                          int maximumAttempts, int attemptCount, Throwable lastError) {
        if (!retryPolicy.isRetryable(lastError)) {
            unretriableError = lastError
        }
        if (Thread.currentThread().interrupted) {
            throw new InterruptedException("Retry was interrupted on attempt ${attemptCount}.")
        }
        boolean tooManyAttempts = maximumAttempts > 0 && attemptCount > maximumAttempts
        boolean abortRetry = lastError instanceof InterruptedException || unretriableError || tooManyAttempts
        if (abortRetry) {
            interrupted = true
            result.description = "LocalRetry aborted: ${lastError}"
            throw lastError
        }
        try {
            result.chain(work())
            return result
        } catch (Throwable currentError) {
            recursingRetry(retryPolicy, work, maximumAttempts, attemptCount + 1, currentError)
        }
    }

    @Override
    protected void addCallback(Runnable callback) {
        future.addListener(callback, executor)
    }

    @Override
    protected void removeCallback(Runnable callback) {
        // no need to implement for unit tests
    }

    String toString() {
        String result
        if (unretriableError) {
            result = "unretriableError=${unretriableError.toString()}"
        } else {
            result = "value=${isReady() ? get() : 'null'}"
        }
        "LocalRetry [ready=${isReady()} ${result}]"
    }
}
