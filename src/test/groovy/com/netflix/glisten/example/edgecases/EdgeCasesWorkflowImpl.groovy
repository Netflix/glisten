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
import com.netflix.glisten.DoTry
import com.netflix.glisten.SwfWorkflowOperations
import com.netflix.glisten.WorkflowOperations
import com.netflix.glisten.WorkflowOperator

class EdgeCasesWorkflowImpl implements EdgeCasesWorkflow, WorkflowOperator<EdgeCasesActivities> {

    @Delegate
    WorkflowOperations<EdgeCasesActivities> workflowOperations = SwfWorkflowOperations.of(EdgeCasesActivities)

    @Override
    void start(EdgeCase edgeCase) {

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

        if (edgeCase == EdgeCase.WorkflowMethodCalls) {
            // This ensures that we can call local private methods.
            doTry {
                doTry { Promise.Void() } result
            }
            waitFor(Promise.Void()) {
                localMethodCall('local method call inside wait')
            }
            localMethodCall('local method call')
            nestingLocalMethodCalls('nested local method call')
        }

    }


    private Promise<String> nestingLocalMethodCalls(String text) {
        localMethodCall(text)
    }

    private Promise<String> localMethodCall(String text) {
        doTry {
            promiseFor(activities.doActivity(text))
        } result
    }
}
