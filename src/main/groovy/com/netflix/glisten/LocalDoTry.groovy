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
@SuppressWarnings('CatchException')
class LocalDoTry implements DoTry {

    private Exception error

    private Promise result = new Settable()

    LocalDoTry(Closure<? extends Promise> tryBlock) {
        try {
            result = tryBlock()
        } catch (Exception e) {
            error = e
        }
    }

    @Override
    DoTry withCatch(Closure doCatchBlock) {
        if (error) {
            try {
                result = doCatchBlock(error)
                error = null
            } catch (Exception e) {
                result = Promise.asPromise(null)
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

    @Override
    void cancel(Throwable cause) {
        if (result instanceof FutureAsPromise) {
            result.future.cancel(true)
        }
    }

    @Override
    Promise getResult() {
        if (error) {
            throw error
        }
        result
    }
}
