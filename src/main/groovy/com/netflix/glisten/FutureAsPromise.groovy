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
import groovy.transform.Canonical
import java.util.concurrent.Future

/**
 * Wraps a Future and makes it look like an SWF Promise. This is useful for local implementations sufficient to run unit
 * tests without a real SWF dependency. Calls to get() will block like a Future rather than fail like an SWF Promise.
 */
@Canonical
class FutureAsPromise<T> extends Promise<T> {
    
    final Future<T> future

    @Override
    T get() {
        future.get()
    }

    @Override
    boolean isReady() {
        future.isDone()
    }

    @Override
    protected void addCallback(Runnable callback) {
        // no need to implement for unit tests
    }

    @Override
    protected void removeCallback(Runnable callback) {
        // no need to implement for unit tests
    }

    String toString() {
        "FutureAsPromise [value=${ready ? get() : 'null'}, ready=${ready}]"
    }
}
