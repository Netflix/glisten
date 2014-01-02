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

/**
 * Local implementation sufficient to run unit tests without a real SWF dependency.
 */
class LocalWaitFor {

    private final ScopedTries scopedTries
    private final LocalWorkflowOperations workflowOperations

    boolean isCanceled = false

    LocalWaitFor(LocalWorkflowOperations workflowOperations) {
        this.workflowOperations = workflowOperations
        scopedTries = new ScopedTries(workflowOperations)
    }

    /**
     * Executes the work once the promise is ready.
     *
     * @param tryBlock to execute
     */
    @SuppressWarnings('CatchThrowable')
    <T> Promise<T> waitForIt(Promise<?> promise, Closure<? extends Promise<T>> work) {
        Settable result = new Settable()
        result.description = "waitFor ${promise}"
        promise.addCallback {
            try {
                // Execute work once the promise is ready if this waitFor has not been canceled.
                if (!isCanceled) {
                    Closure<? extends Promise> rescopedWork = scopedTries.interceptMethodCallsInClosure(work)
                    result.chain(rescopedWork(promise.get()))
                }
            } catch (Throwable t) {
                scopedTries.cancel()
                throw t
            } finally {
                workflowOperations.checkThatAllResultsAreAvailable()
            }
        }
        result
    }

    /** Cancels logic nested inside this waitFor. */
    void cancel() {
        isCanceled = true
        scopedTries.cancel()
    }

    /**
     * Indicates if the tries and retries nested in this waitFor are done.
     */
    boolean isDone() {
        scopedTries.allDone()
    }

    String toString() {
        "LocalWaitFor: ${scopedTries}"
    }

}
