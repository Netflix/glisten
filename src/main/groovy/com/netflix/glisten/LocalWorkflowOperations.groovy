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

import com.amazonaws.services.simpleworkflow.flow.core.Promise
import com.amazonaws.services.simpleworkflow.flow.core.Settable
import com.amazonaws.services.simpleworkflow.flow.interceptors.RetryPolicy
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Local implementation sufficient to run workflow unit tests without a real SWF dependency.
 */
class LocalWorkflowOperations<A> extends WorkflowOperations<A> {

    final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(15))

    final A activities

    private final Set<String> firedTimerNames = []
    private final List<String> timerHistory = []
    private final boolean shouldBlockUntilAllPromisesAreReady
    private final CountDownLatch waitForAllPromises = new CountDownLatch(1)

    private ScopedTries scopedTries
    private boolean isWorkflowExecutionComplete = false

    static <T> LocalWorkflowOperations<T> of(T activities, boolean shouldBlockUntilAllPromisesAreReady = true) {
        new LocalWorkflowOperations<T>(activities, shouldBlockUntilAllPromisesAreReady).with {
            scopedTries = new ScopedTries(it)
            it
        }
    }

    private LocalWorkflowOperations(A activities, boolean shouldBlockUntilAllPromisesAreReady) {
        this.activities = activities
        this.shouldBlockUntilAllPromisesAreReady = shouldBlockUntilAllPromisesAreReady
    }

    /** The top of a hierarchy of tries and retries for this workflow execution. */
    ScopedTries getScopedTries() {
        scopedTries
    }

    /**
     * Adds names of timers that should have fired during workflow execution.
     *
     * @param newFiredTimerNames to be added
     */
    void addFiredTimerNames(Collection<String> newFiredTimerNames) {
        firedTimerNames.addAll(newFiredTimerNames)
    }

    /** Gets names of timers that should have fired during workflow execution. */
    ImmutableList<String> getFiredTimerNames() {
        ImmutableList.copyOf(firedTimerNames)
    }

    /** Gets a log of timers encountered during workflow execution and whether they fired. */
    ImmutableList<String> getTimerHistory() {
        ImmutableList.copyOf(timerHistory)
    }

    private void checkThatAllResultsAreAvailable() {
        if (isWorkflowExecutionComplete && scopedTries.allDone()) {
            waitForAllPromises.countDown()
        }
    }

    /** Hooks into the end of a workflow execution. */
    protected void workflowExecutionComplete() {
        isWorkflowExecutionComplete = true
        checkThatAllResultsAreAvailable()
        if (shouldBlockUntilAllPromisesAreReady) {
            waitForAllPromises.await()
        }
    }

    @Override
    @SuppressWarnings('CatchThrowable')
    <T> Promise<T> waitFor(Promise<?> promise, Closure<? extends Promise<T>> work) {
        Settable result = new Settable()
        result.description = "waitFor ${promise}"
        promise.addCallback {
            try {
                // Execute work once the promise is ready.
                result.chain(work(promise.get()))
            } catch (Throwable t) {
                // Don't block on workflow completion if there was an error. Just stop.
                waitForAllPromises.countDown()
                throw t
            } finally {
                // Recheck completion of all nested processes.
                checkThatAllResultsAreAvailable()
            }
        }
        result
    }

    @Override
    Promise<Void> timer(long delaySeconds, String name = '') {
        boolean hasTimerFired = hasTimerFired(delaySeconds, name)
        timerHistory << "Timer ${name} ${hasTimerFired ? '' : 'NOT'} fired."
        Settable timerResult = new Settable()
        timerResult.description = "timer: ${name}"
        timerResult.ready = hasTimerFired
        timerResult
    }

    private boolean hasTimerFired(long delaySeconds, String name = '') {
        if (firedTimerNames.contains(name)) { return true }
        delaySeconds == 0
    }

    @Override
    def <T> DoTry<T> doTry(Closure<? extends Promise<T>> work) {
        scopedTries.doTry(work)
    }

    @Override
    def <T> Promise<T> retry(RetryPolicy retryPolicy, Closure<? extends Promise<T>> work) {
        scopedTries.retry(retryPolicy, work)
    }
}
