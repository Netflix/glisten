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
package com.netflix.glisten.impl

import com.amazonaws.services.simpleworkflow.flow.core.Promise
import com.amazonaws.services.simpleworkflow.flow.core.Settable

/**
 * Usage patterns that are common for Promises.
 *
 * @param < T > The type of object that is promised.
 */
class PromisingResult<T> extends Promise<T> {

    private Settable<T> result

    PromisingResult(String description = 'PromisingResult') {
        result = new Settable()
        result.description = description
    }

    void setDescription(String description) {
        result.description = description
    }

    @Override
    T get() {
        result.get()
    }

    @Override
    boolean isReady() {
        result.isReady()
    }

    @Override
    protected void addCallback(Runnable callback) {
        result.addCallback(callback)
    }

    @Override
    protected void removeCallback(Runnable callback) {
        result.removeCallback(callback)
    }

    @Override
    String toString() {
        result.toString()
    }


    /**
     * Chain a value to this promised result.
     *
     * @param valueToChain will be wrapped in a promise and chained to the result
     */
    void chain(T valueToChain) {
        chain(wrapWithPromise(valueToChain))
    }

    /**
     * Chain a promise to this promised result. When the promiseToChain is ready so will this promised result.
     *
     * @param promiseToChain will be chained to the result
     */
    void chain(Promise<T> promiseToChain) {
        if (promiseToChain == this) {
            return
        }
        // This is the safest way to handle chaining that we are aware of. We still brute force a chain no matter what.
        // We are really just trying to avoid the potential for errors.

        // You can't unchain if the result is ready.
        if (result.isReady()) {
            // But this chain is happening anyway, so we are throwing out the result that was already ready.
            // This had been known to happen in some odd cases with retries and distributed try/catch blocks.
            // If you end up in a catch block or a subsequent retry, why was that original result ready?
            Settable oldResult = result
            result = new Settable()
            result.description = oldResult.description
            oldResult.callbacks.each { result.addCallback(it) }
        }

        // There is no harm in unchaining whether or not anything was already chained so we always do it.
        result.unchain()

        // Now this should work without a problem.
        result.chain(promiseToChain)
    }

    /**
     * Ensures that a value is wrapped by a Promise. It will not layer wrapping promises. Null values result in Promises
     * that are not ready.
     *
     * @param value that should be wrapped as a Promise
     * @return a promise that wraps a value
     */
    static <U> Promise<U> wrapWithPromise(U value) {
        if (value == null) { return new Settable() }
        (Promise<U>) (Promise.isAssignableFrom(value.getClass()) ? value : asPromise(value))
    }

}
