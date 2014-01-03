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
package com.netflix.glisten.example.edgecases
import com.amazonaws.services.simpleworkflow.flow.core.Promise
import com.amazonaws.services.simpleworkflow.flow.core.Settable
import com.amazonaws.services.simpleworkflow.flow.interceptors.ExponentialRetryPolicy
import com.amazonaws.services.simpleworkflow.flow.interceptors.RetryPolicy
import com.netflix.glisten.DoTry
import com.netflix.glisten.impl.swf.SwfWorkflowOperations
import com.netflix.glisten.WorkflowOperations
import com.netflix.glisten.WorkflowOperator

class EdgeCasesWorkflowImpl implements EdgeCasesWorkflow, WorkflowOperator<EdgeCasesActivities> {

    @Delegate
    WorkflowOperations<EdgeCasesActivities> workflowOperations = SwfWorkflowOperations.of(EdgeCasesActivities)

    @Override
    @SuppressWarnings('AbcMetric')
    void start(EdgeCase edgeCase) {
        status("Starting workflow for ${edgeCase}.")

        if (edgeCase == EdgeCase.WaitForDeadlocks) {
            // These are all attempts to deadlock the workflow with different permutations of waitFor.
            DoTry<Void> uselessTimer = cancelableTimer(42, 'useless')
            waitFor(uselessTimer.result) { Promise.Void() }
            waitFor(Promise.Void()) { Promise.Void() }
            waitFor(new Settable()) { Promise.Void() }
            waitFor(allPromises(uselessTimer.result)) { Promise.Void() }
            waitFor(anyPromises(uselessTimer.result)) { Promise.Void() }
            waitFor(allPromises(uselessTimer.result, Promise.Void())) { Promise.Void() }
            waitFor(anyPromises(uselessTimer.result, new Settable())) { Promise.Void() }

            waitFor(activities.doActivity('Any deadlocks?')) {
                activities.doActivity(it)
                uselessTimer.cancel(null)
                status it
            }
        }

        if (edgeCase == EdgeCase.WaitForScoping) {
            // This ensures that we can use tries and retries nested in a waitFor.
            doTry {
                waitFor(Promise.Void()) {
                    doTry { Promise.Void() }
                    doTry { promiseFor(activities.doActivity('in waitFor')) }
                    Promise.Void()
                }
            } result
        }

        if (edgeCase == EdgeCase.FailedTry) {
            // This try should be done on failure
            doTry {
                retry { promiseFor(activities.doActivity('fails indefinitely')) }
                doTry { promiseFor(activities.doActivity('will never be ready')) }
                doTry { promiseFor(activities.doActivity('this should fail the whole try block')) } result
            } withCatch { Throwable t ->
                Promise.Void()
            } result
        }

        if (edgeCase == EdgeCase.Retry) {
            status 'Test started.'
            RetryPolicy retryPolicy = new ExponentialRetryPolicy(1).withExceptionsToRetry([IllegalStateException])
            Promise<String> resultPromise = doTry {
                retry(retryPolicy) {
                    waitFor(activities.doActivity('retry')) { String message ->
                        if (message) {
                            throw new IllegalStateException(message)
                        }
                        promiseFor(message)
                    }
                }
            } result
            waitFor(resultPromise) {
                status 'Test completed.'
            }
            workflowOperations.countDownLatchesByName['long running activity'].countDown()
        }

        if (edgeCase == EdgeCase.WorkflowMethodCalls) {
            // This ensures that we can call local private methods.
            doTry {
                status 'In top level doTry.'
                doTry { Promise.Void() }
                localMethodCall('local method call inside doTry')
                nestingLocalMethodCalls('nested method call inside doTry')
            }
            waitFor(Promise.Void()) {
                localMethodCall('local method call inside wait')
            }
            localMethodCall('local method call')
            nestingLocalMethodCalls('nested local method call')
            doTry { Promise.Void() }
        }

    }

    private Promise<String> nestingLocalMethodCalls(String text) {
        status "In nestingLocalMethodCalls: ${text}"
        localMethodCall(text)
    }

    private Promise<String> localMethodCall(String text) {
        status "In localMethodCall: ${text}"
        doTry {
            promiseFor(activities.doActivity(text))
        } result
    }
}
