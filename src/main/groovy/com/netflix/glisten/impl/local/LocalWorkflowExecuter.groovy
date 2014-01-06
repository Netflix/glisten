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
package com.netflix.glisten.impl.local

import com.netflix.glisten.WorkflowOperations
import com.netflix.glisten.WorkflowOperator
import com.netflix.glisten.impl.ReflectionHelper
import java.lang.reflect.Method
import javax.naming.OperationNotSupportedException

/**
 * Wraps the execution of a workflow.
 */
class LocalWorkflowExecuter implements WorkflowOperator {

    final LocalWorkflowOperations workflowOperations

    private final Object workflow
    private final boolean shouldBlockUntilAllPromisesAreReady

    protected LocalWorkflowExecuter(Object workflow, LocalWorkflowOperations workflowOperations,
            boolean shouldBlockUntilAllPromisesAreReady) {
        this.workflow = workflow
        this.workflowOperations = workflowOperations
        this.shouldBlockUntilAllPromisesAreReady = shouldBlockUntilAllPromisesAreReady
    }

    def methodMissing(String name, args) {
        executeMethodByName(name, args as List)
        if (shouldBlockUntilAllPromisesAreReady) {
            workflowOperations.workflowExecutionComplete()
        }
    }

    private executeMethodByName(String name, List args) {
        ReflectionHelper reflectionHelper = new ReflectionHelper(workflow.getClass())
        Method method = reflectionHelper.findMethodForNameAndArgsOrFail(name, args)
        method.invoke(workflow, args as Object[])
    }

    @Override
    void setWorkflowOperations(WorkflowOperations workflowOperations) {
        throw new OperationNotSupportedException('The workflowOperations is not settable.')
    }
}
