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
package com.netflix.glisten.example.trip

import com.amazonaws.services.simpleworkflow.flow.core.Promise
import com.amazonaws.services.simpleworkflow.flow.interceptors.ExponentialRetryPolicy
import com.amazonaws.services.simpleworkflow.flow.interceptors.RetryPolicy
import com.netflix.glisten.DoTry
import com.netflix.glisten.impl.swf.SwfWorkflowOperations
import com.netflix.glisten.WorkflowOperations
import com.netflix.glisten.WorkflowOperator

/**
 * SWF workflow implementation for the BayAreaTripWorkflow example.
 */
class BayAreaTripWorkflowImpl implements BayAreaTripWorkflow, WorkflowOperator<BayAreaTripActivities> {

    @Delegate
    WorkflowOperations<BayAreaTripActivities> workflowOperations = SwfWorkflowOperations.of(BayAreaTripActivities)

    @Override
    void start(String name, Collection<BayAreaLocation> previouslyVisited) {
        Promise<BayAreaLocation> destinationPromise = determineDestination(previouslyVisited)
        waitFor(destinationPromise) { BayAreaLocation destination ->
            waitFor(activities.goTo(name, destination)) {
                status it
                Map<BayAreaLocation, Closure<Promise<Void>>> doAtLocation = [
                        (BayAreaLocation.GoldenGateBridge): this.&doAtBridge,
                        (BayAreaLocation.Redwoods): this.&doAtRedwoods,
                        (BayAreaLocation.Monterey): this.&doAtMonterey,
                        (BayAreaLocation.Boardwalk): this.&doAtBoardwalk
                ]
                doTry {
                    doAtLocation[destination].call()
                } withCatch { Throwable t ->
                    status "Oh Noes! ${t.message}"
                }
                Promise.Void()
            }
        }
    }

    private Promise<BayAreaLocation> determineDestination(previouslyVisited) {
        if (!previouslyVisited.contains(BayAreaLocation.GoldenGateBridge)) {
            return promiseFor(BayAreaLocation.GoldenGateBridge)
        }
        if (!previouslyVisited.contains(BayAreaLocation.Redwoods)) {
            return promiseFor(BayAreaLocation.Redwoods)
        }
        waitFor(activities.askYesNoQuestion('Do you like roller coasters?')) { boolean isThrillSeeker ->
            if (isThrillSeeker) {
                return promiseFor(BayAreaLocation.Boardwalk)
            }
            promiseFor(BayAreaLocation.Monterey)
        }
    }

    private Promise<Void> doAtBridge() {
        waitFor(activities.hike('across the bridge')) {
            status it
        }
    }

    private Promise<Void> doAtRedwoods() {
        // take time to stretch before hiking
        status 'And stretched for 10 seconds before hiking.'
        waitFor(timer(10, 'stretching')) {
            DoTry<String> hiking = doTry {
                retry(new ExponentialRetryPolicy(1).withExceptionsToRetry([NotDoneYetException])) {
                    promiseFor(activities.hike('through redwoods'))
                }
            }
            DoTry<Void> countDown = cancelableTimer(30, 'countDown')

            // hike until done or out of time (which ever comes first)
            Promise<Boolean> doneHiking = waitFor(anyPromises(countDown.result, hiking.result)) {
                if (hiking.result.isReady()) {
                    countDown.cancel(null)
                    status "${hiking.result.get()}"
                } else {
                    hiking.cancel(null)
                    status 'And ran out of time when hiking.'
                }
                Promise.asPromise(true)
            }
            waitFor(doneHiking) {
                status 'Left forest safely (no bigfoot attack today).'
            }
        }
    }

    private Promise<Void> doAtMonterey() {
        // parallel activities (eat while watching)
        Promise<String> eating = promiseFor(activities.enjoy('eating seafood'))
        Promise<String> watching = promiseFor(activities.enjoy('watching sea lions'))
        waitFor(allPromises(eating, watching)) {
            status "${eating.get()} ${watching.get()}"
            doTry {
                promiseFor(activities.enjoy('looking for sea glass on the beach'))
            } withCatch { Throwable t ->
                status t.message
                promiseFor(activities.enjoy('the aquarium'))
            } withFinally { String result ->
                status result
                waitFor(activities.enjoy('the 17-Mile Drive')) { status it }
            }
            Promise.Void()
        }
    }

    @SuppressWarnings('UnnecessaryReturnKeyword')
    private Promise<Void> doAtBoardwalk() {
        int numberOfTokensGiven = 3
        int numberOfTokens = numberOfTokensGiven
        RetryPolicy retryPolicy = new ExponentialRetryPolicy(60).
                withMaximumAttempts(numberOfTokens).withExceptionsToRetry([IllegalStateException])
        DoTry<String> tryToWin = doTry {
            return retry(retryPolicy) {
                numberOfTokens--
                promiseFor(activities.win('a carnival game'))
            }
        } withCatch { Throwable e ->
            return promiseFor("${e.message} ${numberOfTokensGiven} times.")
        }
        waitFor(tryToWin.result) {
            status it
            if (numberOfTokens > 0) {
                waitFor(activities.enjoy('a roller coaster')) { status it }
            }
            Promise.Void()
        }
    }

}
