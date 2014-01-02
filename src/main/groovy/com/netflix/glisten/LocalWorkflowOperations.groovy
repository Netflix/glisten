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

    /** Named CountDownLatches can be put here to be shared between workflow and test to recreate race conditions.*/
    final Map<String, CountDownLatch> countDownLatchesByName = [:]

    private final Set<String> firedTimerNames = []
    private final List<String> timerHistory = []
    private final CountDownLatch waitForAllPromises = new CountDownLatch(1)

    private ScopedTries scopedTries
    private WorkflowOperator workflow
    private boolean isWorkflowExecutionComplete = false

    static <T> LocalWorkflowOperations<T> of(T activities) {
        new LocalWorkflowOperations<T>(activities).with {
            scopedTries = new ScopedTries(it)
            it
        }
    }

    private LocalWorkflowOperations(A activities) {
        this.activities = activities
    }

    /** The top of a hierarchy of tries and retries for this workflow execution. */
    ScopedTries getScopedTries() {
        scopedTries
    }

    protected WorkflowOperator getWorkflow() {
        workflow
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

    /** Checks the completion of this workflow and all nested work. Unblocks the workflow once complete. */
    void checkThatAllResultsAreAvailable() {
        if (isWorkflowExecutionComplete && scopedTries.allDone()) {
            waitForAllPromises.countDown()
        }
    }

    /** Hooks into the end of a workflow execution and allows it to block until done. */
    protected void workflowExecutionComplete() {
        isWorkflowExecutionComplete = true
        checkThatAllResultsAreAvailable()
        waitForAllPromises.await()
    }

    @Override
    <T> Promise<T> waitFor(Promise<?> promise, Closure<? extends Promise<T>> work) {
        scopedTries.waitFor(promise, work)
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

    /**
     * Creates an executer that runs the workflow. The workflow will wait for all try and retry results. This ensures
     * that code works with the assumption that the workflow is done. Otherwise things like asserts in a unit test
     * may have race conditions.
     *
     * @param workflowImplType the implementation class for the workflow
     * @return an executer for the workflow seen as an instance of the workflow implementation itself
     */
    @SuppressWarnings('UnnecessaryPublicModifier')
    public <T extends WorkflowOperator> T getExecuter(Class<T> workflowImplType) {
        (T) makeExecuter(workflowImplType, true)
    }

    /**
     * Creates an executer that runs the workflow. The workflow will not block for all try and retry results.
     *
     * @param workflowImplType the implementation class for the workflow
     * @return an executer for the workflow seen as an instance of the workflow implementation itself
     */
    @SuppressWarnings('UnnecessaryPublicModifier')
    public <T extends WorkflowOperator> T getNonblockingExecuter(Class<T> workflowImplType) {
        (T) makeExecuter(workflowImplType, false)
    }

    private LocalWorkflowExecuter makeExecuter(Class<WorkflowOperator> workflowImplType,
            boolean shouldBlockUntilAllPromisesAreReady) {
        workflow = workflowImplType.newInstance()
        workflow.workflowOperations = this
        new LocalWorkflowExecuter(workflow, this, shouldBlockUntilAllPromisesAreReady)
    }
}
