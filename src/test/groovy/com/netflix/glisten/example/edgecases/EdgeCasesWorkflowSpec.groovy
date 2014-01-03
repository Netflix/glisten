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

import com.netflix.glisten.impl.local.LocalDoTry
import com.netflix.glisten.impl.local.LocalWorkflowOperations
import com.netflix.glisten.impl.local.ScopedTries
import java.util.concurrent.CountDownLatch
import spock.lang.Specification

class EdgeCasesWorkflowSpec extends Specification {

    EdgeCasesActivities mockActivities = Mock(EdgeCasesActivities)
    LocalWorkflowOperations workflowOperations = LocalWorkflowOperations.of(mockActivities)
    def workflowExecuter = workflowOperations.getExecuter(EdgeCasesWorkflowImpl)

    def 'should not deadlock'() {
        when:
        workflowExecuter.start(EdgeCase.WaitForDeadlocks)

        then:
        1 * mockActivities.doActivity('Any deadlocks?') >> 'nope'
        workflowOperations.scopedTries.allDone()
    }

    def 'should keep references to tries and retries nested in a waitFor'() {
        when:
        workflowExecuter.start(EdgeCase.WaitForScoping)

        then:
        1 * mockActivities.doActivity('in waitFor') >> 'test'
        workflowOperations.scopedTries.tries.size() == 1
        workflowOperations.scopedTries.retries.size() == 0
        LocalDoTry topLevelDoTry = workflowOperations.scopedTries.tries[0]
        ScopedTries scopedTriesInWaitFor = topLevelDoTry.scopedTries.waitFors[0].scopedTries
        scopedTriesInWaitFor.tries.size() == 2
        scopedTriesInWaitFor.retries.size() == 0
    }

    def 'should be done with try after a failure'() {
        when:
        workflowExecuter.start(EdgeCase.FailedTry)

        then:
        (1.._) * mockActivities.doActivity('fails indefinitely') >> {
            throw new IllegalStateException('This will never work!')
        }
        1 * mockActivities.doActivity('this should fail the whole try block') >> {
            throw new IllegalStateException('Stop it! You should all be ashamed of yourselves!')
        }
        workflowOperations.scopedTries.allDone()
        LocalDoTry topLevelDoTry = workflowOperations.scopedTries.tries[0]
        topLevelDoTry.isDone()
        topLevelDoTry.scopedTries.retries[0].done
        topLevelDoTry.scopedTries.tries[0].done
        topLevelDoTry.scopedTries.tries[1].done
    }

    def 'should call local workflow methods'() {
        when:
        workflowExecuter.start(EdgeCase.WorkflowMethodCalls)

        then:
        1 * mockActivities.doActivity('local method call') >> 'check'
        1 * mockActivities.doActivity('nested local method call') >> 'this works too'
        1 * mockActivities.doActivity('local method call inside wait') >> 'yep'
        1 * mockActivities.doActivity('local method call inside doTry') >> 'all good here'
        1 * mockActivities.doActivity('nested method call inside doTry') >> 'even this worked'
        workflowOperations.scopedTries.tries.size() == 4
        workflowOperations.scopedTries.retries.size() == 0
        workflowOperations.scopedTries.waitFors.size() == 1
        LocalDoTry containerDoTry = workflowOperations.scopedTries.tries[0]
        containerDoTry.scopedTries.tries.size() == 3
        containerDoTry.scopedTries.retries.size() == 0
        containerDoTry.scopedTries.waitFors.size() == 0

        workflowOperations.logHistory == [
                'Starting workflow for WorkflowMethodCalls.',
                'In top level doTry.',
                'In localMethodCall: local method call inside doTry',
                'In nestingLocalMethodCalls: nested method call inside doTry',
                'In localMethodCall: nested method call inside doTry',
                'In localMethodCall: local method call inside wait',
                'In localMethodCall: local method call',
                'In nestingLocalMethodCalls: nested local method call',
                'In localMethodCall: nested local method call',
        ]
    }

    def 'should retry and eventually pass without deadlock'() {
        workflowOperations.countDownLatchesByName['long running activity'] = new CountDownLatch(1)

        when:
        workflowExecuter.start(EdgeCase.Retry)

        then:
        workflowOperations.logHistory == [
                'Starting workflow for Retry.',
                'Test started.',
                'Test completed.',
        ]
        1 * mockActivities.doActivity('retry') >> {
            'no luck this time'
        }
        1 * mockActivities.doActivity('retry') >> {
            // The CountDownLatch makes the activity last longer than the workflow execution. We avoid a sleep call.
            workflowOperations.countDownLatchesByName['long running activity'].await()
            ''
        }
    }
}
