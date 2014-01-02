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

/**
 * Local implementation sufficient to run unit tests without a real SWF dependency.
 */
class LocalDoTry implements DoTry {

    private final ScopedTries scopedTries
    private final PromisingResult result = new PromisingResult('LocalDoTry')

    private boolean canceled = false
    private Exception error

    LocalDoTry(LocalWorkflowOperations workflowOperations) {
        scopedTries = new ScopedTries(workflowOperations)
    }

    /**
     * Tries to execute the closure passed in.
     *
     * @param tryBlock to execute
     */
    @SuppressWarnings('CatchException')
    LocalDoTry tryIt(Closure<? extends Promise> tryBlock) {
        Closure<? extends Promise>  rescopedTryBlock = scopedTries.interceptMethodCallsInClosure(tryBlock)
        try {
            result.chain(rescopedTryBlock())
        } catch (Exception e) {
            scopedTries.cancel()
            result.description = "Error in LocalDoTry try: ${e}"
            error = e
        }
        this
    }

    @Override
    @SuppressWarnings('CatchException')
    DoTry withCatch(Closure doCatchBlock) {
        if (error) {
            try {
                result.chain(doCatchBlock(error))
                error = null
            } catch (Exception e) {
                result.description = "Error in LocalDoTry catch: ${e}"
                result.chain(Promise.asPromise(null))
                error = e
            }
        }
        this
    }

    @Override
    DoTry withFinally(Closure doFinallyBlock) {
        if (result?.ready) {
            doFinallyBlock(result?.get())
        } else {
            doFinallyBlock(null)
        }
        if (error) {
            throw error
        }
        this
    }

    /** Cancels this try logic and everything nested inside it. */
    @Override
    void cancel(Throwable cause) {
        canceled = true
        result.description = 'Canceled LocalDoTry'
        scopedTries.cancel()
    }

    /**
     * Indicates if this try is done. Done means that all nested operations are done and it is complete due to being
     * ready, canceled or failed.
     */
    boolean isDone() {
        if (!scopedTries.allDone()) { return false }
        if (canceled || error) { return true }
        result.ready
    }

    /** Gets a promise for the result of the try logic. */
    @Override
    Promise getResult() {
        if (error) {
            throw error
        }
        scopedTries.retries.each {
            if (it.unretriableError) {
                throw it.unretriableError
            }
        }
        result
    }

    String toString() {
        "LocalDoTry [canceled=${canceled}, result=${result}, isDone=${isDone()}]"
    }
}
