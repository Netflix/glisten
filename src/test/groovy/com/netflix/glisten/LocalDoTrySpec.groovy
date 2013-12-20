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
import spock.lang.Specification

@SuppressWarnings('AbcMetric')
class LocalDoTrySpec extends Specification {

    LocalWorkflowOperations workflowOperations = LocalWorkflowOperations.of(null)

    def 'should keep references to nested tries and retries'() {
        when:
        LocalDoTry topLevelDoTry = (LocalDoTry) workflowOperations.doTry {
            retry {
                new Settable()
            }
            doTry {
                new Settable()
            }
            retry {
                doTry {
                    new Settable()
                }
                retry {
                    new Settable()
                }
            }
            doTry {
                doTry {
                    new Settable()
                }
                retry {
                    new Settable()
                }
            }
            cancelableTimer(10, 'test').result
        }

        then: 'Make sure all nested tries and retries exist and are not canceled'
        workflowOperations.scopedTries.tries.size() == 1
        workflowOperations.scopedTries.retries.size() == 0
        !topLevelDoTry.canceled
        topLevelDoTry.scopedTries.tries.size() == 3
        topLevelDoTry.scopedTries.retries.size() == 2

        LocalDoTry emptyDoTry = topLevelDoTry.scopedTries.tries[0]
        !emptyDoTry.canceled
        emptyDoTry.scopedTries.tries.size() == 0
        emptyDoTry.scopedTries.retries.size() == 0
        LocalDoTry doTryWithNesting = topLevelDoTry.scopedTries.tries[1]
        !doTryWithNesting.canceled
        doTryWithNesting.scopedTries.tries.size() == 1
        !doTryWithNesting.scopedTries.tries[0].canceled
        doTryWithNesting.scopedTries.retries.size() == 1
        !doTryWithNesting.scopedTries.retries[0].interrupted
        LocalDoTry cancelableTimer = topLevelDoTry.scopedTries.tries[2]
        !cancelableTimer.canceled
        cancelableTimer.scopedTries.tries.size() == 0
        cancelableTimer.scopedTries.retries.size() == 0

        LocalRetry emptyRetry = topLevelDoTry.scopedTries.retries[0]
        !emptyRetry.interrupted
        emptyRetry.scopedTries.tries.size() == 0
        emptyRetry.scopedTries.retries.size() == 0
        LocalRetry retryWithNesting = topLevelDoTry.scopedTries.retries[1]
        !retryWithNesting.interrupted
        retryWithNesting.scopedTries.tries.size() == 1
        !retryWithNesting.scopedTries.tries[0].canceled
        retryWithNesting.scopedTries.retries.size() == 1
        !retryWithNesting.scopedTries.retries[0].interrupted

        when:
        topLevelDoTry.cancel(null)

        then:
        topLevelDoTry.canceled
        emptyDoTry.canceled
        doTryWithNesting.canceled
        doTryWithNesting.scopedTries.tries[0].canceled
        doTryWithNesting.scopedTries.retries[0].interrupted
        cancelableTimer.canceled
        emptyRetry.interrupted
        retryWithNesting.interrupted
        retryWithNesting.scopedTries.tries[0].canceled
        retryWithNesting.scopedTries.retries[0].interrupted
    }

    def 'should not be done until all nested tries and retries are done'() {
        Settable doTryResult = new Settable()

        when:
        LocalDoTry topLevelDoTry = (LocalDoTry) workflowOperations.doTry {
            doTry {
                doTryResult
            }
            retry {
                new Settable()
            }
            doTry {
                Promise.Void()
            } result
        }

        then:
        !topLevelDoTry.isDone()
        LocalDoTry unreadyDoTry = topLevelDoTry.scopedTries.tries[0]
        !unreadyDoTry.isDone()
        LocalDoTry readyDoTry = topLevelDoTry.scopedTries.tries[1]
        readyDoTry.isDone()
        LocalRetry unreadyRetry = topLevelDoTry.scopedTries.retries[0]
        !unreadyRetry.isDone()

        when:
        unreadyRetry.cancel()

        then:
        !topLevelDoTry.isDone()

        when:
        doTryResult.chain(Promise.Void())

        then:
        topLevelDoTry.isDone()
    }
}
