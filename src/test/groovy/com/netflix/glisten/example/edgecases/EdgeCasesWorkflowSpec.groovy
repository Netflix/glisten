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

import com.netflix.glisten.LocalDoTry
import com.netflix.glisten.LocalWorkflowExecuter
import com.netflix.glisten.LocalWorkflowOperations
import spock.lang.Specification

class EdgeCasesWorkflowSpec extends Specification {

    EdgeCasesActivities mockActivities = Mock(EdgeCasesActivities)
    LocalWorkflowOperations workflowOperations = LocalWorkflowOperations.of(mockActivities)
    EdgeCasesWorkflow workflow = new EdgeCasesWorkflowImpl(workflowOperations: workflowOperations)
    def workflowExecuter = LocalWorkflowExecuter.makeLocalWorkflowExecuter(workflow, workflowOperations)

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
        workflowOperations.scopedTries.tries.size() == 3 // two of these should be nested inside the first one
        workflowOperations.scopedTries.retries.size() == 0
        LocalDoTry topLevelDoTry = workflowOperations.scopedTries.tries[0]
        topLevelDoTry.scopedTries.tries.size() == 0
        topLevelDoTry.scopedTries.retries.size() == 0
    }

    def 'should call local workflow methods'() {
        when:
        workflowExecuter.start(EdgeCase.WorkflowMethodCalls)

        then:
        1 * mockActivities.doActivity('local method call') >> 'check'
        1 * mockActivities.doActivity('nested local method call') >> 'this works too'
        1 * mockActivities.doActivity('local method call inside wait') >> 'yep'
        workflowOperations.scopedTries.tries.size() == 4
        workflowOperations.scopedTries.retries.size() == 0
        LocalDoTry containerDoTry = workflowOperations.scopedTries.tries[0]
        containerDoTry.scopedTries.tries.size() == 1
        containerDoTry.scopedTries.retries.size() == 0
    }
}
