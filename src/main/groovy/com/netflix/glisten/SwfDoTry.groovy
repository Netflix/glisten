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

import com.amazonaws.services.simpleworkflow.flow.core.Functor
import com.amazonaws.services.simpleworkflow.flow.core.Promise
import com.amazonaws.services.simpleworkflow.flow.core.TryCatchFinally
import com.google.common.collect.ImmutableSet
import java.util.concurrent.CancellationException

/**
 * SWF specific implementation.
 */
class SwfDoTry<T> extends TryCatchFinally implements DoTry<T> {

    /** Canceling a try is intentional, it is not an error that needs to be handled. */
    private static final Closure SWALLOW_CANCELLATION_EXCEPTION = { Closure handleError, Throwable e ->
        if (e instanceof CancellationException) { return }
        handleError(e)
    }
    private static final Closure SWALLOW_CANCELLATION_EXCEPTION_THROW_EVERYTHING_ELSE = SWALLOW_CANCELLATION_EXCEPTION.
            curry { Throwable e -> throw e }

    private final ImmutableSet<Promise<?>> promises
    private final Closure tryBlock

    private Closure catchBlock
    private Closure finallyBlock
    private final PromisingResult promisingResult = new PromisingResult()

    private SwfDoTry(Collection<Promise<?>> promises, Closure tryBlock, Closure catchBlock, Closure finallyBlock) {
        super(promises as Promise[])
        this.promises = ImmutableSet.copyOf(promises)
        this.tryBlock = tryBlock
        this.catchBlock = catchBlock
        this.finallyBlock = finallyBlock
    }

    /**
     * Construct a DoTry for the try logic.
     *
     * @param promises that must be ready before the try logic will execute
     * @param tryBlock logic to be preformed
     * @return constructed DoTry
     */
    static DoTry<T> execute(Collection<Promise<?>> promises, Closure<? extends Promise<T>> tryBlock) {
        new SwfDoTry(promises, tryBlock, SWALLOW_CANCELLATION_EXCEPTION_THROW_EVERYTHING_ELSE, { })
    }

    /**
     * Construct a DoTry for the try logic.
     *
     * @param tryBlock logic to be preformed
     * @return constructed DoTry
     */
    static DoTry<T> execute(Closure<? extends Promise<T>> tryBlock) {
        execute([], tryBlock)
    }

    @Override
    DoTry<T> withCatch(Closure block) {
        this.catchBlock = SWALLOW_CANCELLATION_EXCEPTION.curry(block)
        this
    }

    @Override
    DoTry<T> withFinally(Closure block) {
        this.finallyBlock = block
        this
    }

    @Override
    Promise<T> getResult() {
        promisingResult
    }

    @Override
    protected void doTry() throws Throwable {
        promisingResult.chain(tryBlock())
    }

    @Override
    protected void doCatch(Throwable e) throws Throwable {
        promisingResult.chain(catchBlock(e))
    }

    @Override
    protected void doFinally() throws Throwable {
        if (promisingResult?.ready) {
            new Functor([promisingResult] as Promise[]) {
                @Override
                protected Promise doExecute() {
                    finallyBlock(promisingResult.get())
                }
            }
        } else {
            finallyBlock(null)
        }
    }
}
