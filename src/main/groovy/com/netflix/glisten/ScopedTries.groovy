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
import com.amazonaws.services.simpleworkflow.flow.interceptors.RetryPolicy
import com.google.common.collect.ImmutableList

/**
 * Used to build a hierarchy of tries and retries that are nested in other tries and retries. It is used to figure out
 * which parts of the hierarchy are done.
 */
class ScopedTries<A> extends WorkflowOperations<A> {

    private final List<LocalRetry> localRetries = []
    private final List<LocalDoTry> localTries = []
    private final LocalWorkflowOperations workflowOperations

    ScopedTries(LocalWorkflowOperations workflowOperations) {
        this.workflowOperations = workflowOperations
    }

    /** Gets retries at this level of the scoping hierarchy. */
    ImmutableList<LocalRetry> getRetries() {
        ImmutableList.copyOf(localRetries)
    }

    /** Gets tries at this level of the scoping hierarchy. */
    ImmutableList<LocalDoTry> getTries() {
        ImmutableList.copyOf(localTries)
    }

    @Override
    <T> DoTry<T> doTry(Closure<? extends Promise<T>> work) {
        LocalDoTry localDoTry = new LocalDoTry(workflowOperations)
        localTries << localDoTry
        localDoTry.tryIt(work)
        localDoTry
    }

    @Override
    @SuppressWarnings('CatchThrowable')
    <T> Promise<T> retry(RetryPolicy retryPolicy, Closure<? extends Promise<T>> work) {
        LocalRetry localRetry = new LocalRetry(workflowOperations)
        localRetries << localRetry
        localRetry.retry(retryPolicy, work)
    }

    @Override
    Promise<Void> timer(long delaySeconds, String name = '') {
        workflowOperations.timer(delaySeconds, name)
    }

    @Override
    Promise<Void> status(String message) {
        workflowOperations.status message
    }

    @Override
    Object getActivities() {
        workflowOperations.activities
    }

    @Override
    <T> Promise<T> waitFor(Promise<?> promise, Closure<? extends Promise<T>> work) {
        workflowOperations.waitFor(promise, work)
    }

    /**
     * Modifies the closure so that it delegates calls to this first. This allows us to build the scope hierarchy.
     *
     * @param closure containing partial workflow logic
     */
    @SuppressWarnings('UnnecessaryPublicModifier')
    public <T> Closure<T> interceptMethodCallsInClosure(Closure<T> closure) {
        closure.rehydrate(this, this, this)
    }

    /**
     * Cancels the tries and retries being done within this scope.
     */
    void cancel() {
        localRetries.each { it.cancel() }
        localTries.each { it.cancel(null) }
    }

    /**
     * Indicates whether all the tries and retries are done.
     */
    boolean allDone() {
        localRetries.every { it.isDone() } && localTries.every { it.isDone() }
    }

    String toString() {
        "ScopedTries retries: ${localRetries} tries: ${localTries}"
    }

    /**
     * This is done so that workflow methods do not ignore the hierarchy of tries and retries. Otherwise they would be
     * at the root scope rather than their appropriate place in the hierarchy.
     */
    def methodMissing(String name, args) {
        // The method must be on the workflow. We are here because it is not on WorkflowOperations.
        // So grab the method on the workflow with the same name and pretend it is a closure.
        Closure workflowMethodAsClosure = workflowOperations.workflow.&"${name}"

        // Make a new instance of the workflow that sees this scoped tries as the workflowOperations.
        Class<WorkflowOperator> workflowType = (Class<WorkflowOperator>) workflowMethodAsClosure.delegate.getClass()
        WorkflowOperator localScopedWorkflowInstance = workflowType.newInstance()
        localScopedWorkflowInstance.workflowOperations = this

        // Create a new closure that delegates everything to the new workflow with properly scoped workflow operations.
        // This is how you do it when you are serious about not delegating to anything else.
        Closure rescopedWorkflowMethodAsClosure = workflowMethodAsClosure.rehydrate(localScopedWorkflowInstance,
                localScopedWorkflowInstance, localScopedWorkflowInstance)

        // Now that everything is configured, call the modified method with the original args.
        rescopedWorkflowMethodAsClosure.call(*args)
    }
}
