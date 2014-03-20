package com.netflix.glisten.example.trip

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.flow.ActivityWorker
import com.amazonaws.services.simpleworkflow.flow.WorkflowWorker
import com.netflix.glisten.InterfaceBasedWorkflowClient
import com.netflix.glisten.WorkflowClientFactory
import com.netflix.glisten.WorkflowDescriptionTemplate
import com.netflix.glisten.WorkflowTags

/**
 * Tests the BayAreaTrip workflow
 * Config required to make this test work:
 * 1. Create a AwsCredentials.properties file in your user home folder. Put this in it:
 * accessKey = [Replace with your AWS access key]
 * secretKey = [Replace with your AWS secret key]
 * 2. In the AWS SWF console, add a domain called "BayAreaTripIntegrationTestDomain"
 */
class BayAreaTripIntegrationTest extends GroovyTestCase {

    private final awsCredentialsPath = System.getProperty("user.home") + "/" + "AwsCredentials.properties"
    private final String domain = "BayAreaTripIntegrationTestDomain"
    private final String taskList = "BayAreaTripIntegrationTestTaskList"

    /**
     * Attempt to execute a workflow, per the information in the Glisten Wiki
     * https://github.com/Netflix/glisten/wiki/Basics#executing-a-workflow-in-your-code
     */
    void testWorkflow() {

        // load the AWS credentials
        final Properties properties = new Properties();
        properties.load(new FileInputStream(awsCredentialsPath));
        final accessKey = properties.getProperty("accessKey")
        final secretKey = properties.getProperty("secretKey")
        final AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey)

        // create the AWS SWF client
        AmazonSimpleWorkflow simpleWorkflow = new AmazonSimpleWorkflowClient(awsCredentials)

        // create the Glisten WorkflowClientFactory
        final workflowClientFactory = new WorkflowClientFactory(simpleWorkflow, domain, taskList)

        // the description template
        WorkflowDescriptionTemplate workflowDescriptionTemplate = new BayAreaTripWorkflowDescriptionTemplate()

        // create tags -- these are required per https://github.com/Netflix/glisten/issues/21
        final workflowTags = new WorkflowTags("BayAreaTripWorkflowTags")

        // create the client for the BayAreaTripWorkflow
        InterfaceBasedWorkflowClient<BayAreaTripWorkflow> client =
                workflowClientFactory.getNewWorkflowClient(BayAreaTripWorkflow, workflowDescriptionTemplate, workflowTags)

        // create and start the workflow worker
        final workflowWorker = new WorkflowWorker(simpleWorkflow, domain, taskList)
        workflowWorker.setWorkflowImplementationTypes([BayAreaTripWorkflowImpl])
        workflowWorker.start()

        // create the activity object
        BayAreaTripActivitiesImpl bayAreaTripActivities = new BayAreaTripActivitiesImpl()

        // create and start the activity worker
        final activityWorker = new ActivityWorker(simpleWorkflow, domain, taskList)
        activityWorker.addActivitiesImplementations([bayAreaTripActivities])
        activityWorker.start()

        // assume I haven't visited anywhere
        final previouslyVisited = []

        // start the workflow
        client.asWorkflow().start("John Doe", previouslyVisited)

        // keeps the test running until the workflow is finished
        // this would be much better if it checked to see if the workflow were completed
        Thread.sleep(30 * 1000)
    }
}
