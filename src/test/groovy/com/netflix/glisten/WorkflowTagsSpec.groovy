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

import com.amazonaws.services.simpleworkflow.flow.DynamicWorkflowClientExternal
import com.amazonaws.services.simpleworkflow.flow.StartWorkflowOptions
import com.netflix.glisten.impl.swf.WorkflowClientExternalToWorkflowInterfaceAdapter
import groovy.transform.Canonical
import spock.lang.Specification

class WorkflowTagsSpec extends Specification {

    WorkflowTags workflowTags = new WorkflowTags()

    def 'should construct swf tags'() {
        workflowTags.desc = 'doing a thing'

        expect:
        workflowTags.constructTags() == ['{"desc":"doing a thing"}']
    }

    def 'should be constructed from swf tags'() {
        workflowTags.withTags(['{"desc":"doing a thing"}'])

        expect:
        workflowTags.desc == 'doing a thing'
    }

    def 'should be able to modify tags without changing original'() {
        List<String> originalTags = ['{"desc":"doing a thing"}']
        workflowTags.withTags(originalTags)
        workflowTags.desc = 'doing a different thing'

        expect:
        workflowTags.constructTags() == ['{"desc":"doing a different thing"}']
        originalTags == ['{"desc":"doing a thing"}']
    }

    def 'should handle description in any position'() {
        workflowTags.withTags([null, '', '{"desc":"doing a thing"}', 'not the description'])

        expect:
        workflowTags.desc == 'doing a thing'
        workflowTags.constructTags() == ['{"desc":"doing a thing"}']
    }

    def 'should quietly handle unserializable data'() {
        workflowTags.withTags([''])

        expect:
        workflowTags.constructTags() == []
        workflowTags.desc == null
    }

    def 'should attempt even with wrong number of tags'() {
        workflowTags.withTags([])

        expect:
        workflowTags.constructTags() == []
        workflowTags.desc == null
    }

    def 'should attempt even with null tag'() {
        workflowTags.withTags([null])

        expect:
        workflowTags.constructTags() == []
        workflowTags.desc == null
    }

    def 'should do nothing with null tags'() {
        workflowTags.withTags(null)

        expect:
        workflowTags.constructTags() == []
        workflowTags.desc == null
    }

    def 'should not be affected by other tags'() {
        workflowTags.withTags(['{"desc":"doing a thing"}'])
        workflowTags.withTags(['{"noDesc":"should not affect tags"}'])

        expect:
        workflowTags.desc == 'doing a thing'
    }

    def 'should use last tag'() {
        workflowTags.withTags(['{"desc":"this will be overwritten"}'])
        workflowTags.withTags(['{"desc":"overwritten as well"}', '{"desc":"this one will win"}'])

        expect:
        workflowTags.desc == 'this one will win'
    }

    def 'client should construct specific tags without destroying original ones'() {
        DynamicWorkflowClientExternal client = Mock(DynamicWorkflowClientExternal)
        client.schedulingOptions >> new StartWorkflowOptions(tagList:
                new ExtraWorkflowTags(extraStuff: new WrappingObject('house-targaryen')).constructTags())
        def adapter = new WorkflowClientExternalToWorkflowInterfaceAdapter(client, TestWorkflow,
                new TestWorkflowDescriptionTemplate(), new ExtraWorkflowTags())

        when:
        adapter.go('Rhaegar', new WrappingObject(nestedName: 'Targaryen'))

        then:
        1 * client.startWorkflowExecution(_, _) >> { List<?> args ->
            assert args[1].tagList as Set == [
                    '{"desc":"Describe workflow for \'Rhaegar\' \'Targaryen\'"}',
                    '{"extraStuff":["com.netflix.glisten.WrappingObject",{"nestedName":"house-targaryen"}]}'
            ] as Set
        }
    }
}

@Canonical
class ExtraWorkflowTags extends WorkflowTags {
    WrappingObject extraStuff
}
