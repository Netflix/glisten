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

import com.netflix.glisten.impl.local.LocalWorkflowOperations
import spock.lang.Specification

class BayAreaTripWorkflowSpec extends Specification {

    BayAreaTripActivities mockActivities = Mock(BayAreaTripActivities)
    LocalWorkflowOperations workflowOperations = LocalWorkflowOperations.of(mockActivities)
    def workflowExecuter = workflowOperations.getExecuter(BayAreaTripWorkflowImpl)

    def 'should go to Golden Gate Bridge'() {
        when:
        workflowExecuter.start('Clay', [])

        then:
        workflowOperations.logHistory == [
                'Clay went to the Golden Gate Bridge.',
                'And hiked across the bridge.'
        ]
        0 * _
        then: 1 * mockActivities.goTo('Clay', BayAreaLocation.GoldenGateBridge) >>
                'Clay went to the Golden Gate Bridge.'
        then: 1 * mockActivities.hike('across the bridge') >> 'And hiked across the bridge.'
    }

    def 'should go to Redwoods and hike'() {
        workflowOperations.addFiredTimerNames(['stretching'])

        when:
        workflowExecuter.start('Clay', [BayAreaLocation.GoldenGateBridge])

        then:
        workflowOperations.logHistory == [
                'Clay went to Muir Woods.',
                'And stretched for 10 seconds before hiking.',
                'And hiked through redwoods.',
                'Left forest safely (no bigfoot attack today).'
        ]
        0 * _
        then: 1 * mockActivities.goTo('Clay', BayAreaLocation.Redwoods) >> 'Clay went to Muir Woods.'
        then: 1 * mockActivities.hike('through redwoods') >> 'And hiked through redwoods.'
    }

    def 'should go to Redwoods and hike until out of time'() {
        workflowOperations.addFiredTimerNames(['stretching', 'countDown'])
        mockActivities.hike('through redwoods') >> {
            throw new NotDoneYetException('got to see what is around the next bend')
        }

        when:
        workflowExecuter.start('Clay', [BayAreaLocation.GoldenGateBridge])

        then:
        workflowOperations.logHistory == [
                'Clay went to Muir Woods.',
                'And stretched for 10 seconds before hiking.',
                'And ran out of time when hiking.',
                'Left forest safely (no bigfoot attack today).'
        ]
        then: 1 * mockActivities.goTo('Clay', BayAreaLocation.Redwoods) >> 'Clay went to Muir Woods.'
    }

    def 'should go to Redwoods and hike until getting around the first bend'() {
        workflowOperations.addFiredTimerNames(['stretching'])

        when:
        workflowExecuter.start('Clay', [BayAreaLocation.GoldenGateBridge])

        then:
        workflowOperations.logHistory == [
                'Clay went to Muir Woods.',
                'And stretched for 10 seconds before hiking.',
                'And hiked through redwoods.',
                'Left forest safely (no bigfoot attack today).'
        ]
        0 * _
        then: 1 * mockActivities.goTo('Clay', BayAreaLocation.Redwoods) >> 'Clay went to Muir Woods.'
        then: 1 * mockActivities.hike('through redwoods') >> {
            throw new NotDoneYetException('got to see what is around the next bend')
        }
        then: 1 * mockActivities.hike('through redwoods') >> 'And hiked through redwoods.'
    }

    def 'should go to Redwoods and hike until something goes horribly wrong'() {
        workflowOperations.addFiredTimerNames(['stretching'])

        when:
        workflowExecuter.start('Clay', [BayAreaLocation.GoldenGateBridge])

        then:
        workflowOperations.logHistory == [
                'Clay went to Muir Woods.',
                'And stretched for 10 seconds before hiking.',
                'Oh Noes! Something went horribly wrong!'
        ]
        0 * _
        1 * mockActivities.goTo('Clay', BayAreaLocation.Redwoods) >> 'Clay went to Muir Woods.'
        (1.._) * mockActivities.hike('through redwoods') >> {
            throw new IllegalStateException('Something went horribly wrong!')
        }
    }

    def 'should go to Boardwalk and win game'() {
        when:
        workflowExecuter.start('Clay', BayAreaLocation.with { [GoldenGateBridge, Redwoods] })

        then:
        workflowOperations.logHistory == [
                'Clay went to the Santa Cruz Boardwalk.',
                'And won a carnival game.',
                'And enjoyed a roller coaster'
        ]
        0 * _
        then: 1 * mockActivities.askYesNoQuestion('Do you like roller coasters?') >> true
        then: 1 * mockActivities.goTo('Clay', BayAreaLocation.Boardwalk) >> 'Clay went to the Santa Cruz Boardwalk.'
        then: 1 * mockActivities.win('a carnival game') >> 'And won a carnival game.'
        then: 1 * mockActivities.enjoy('a roller coaster') >> 'And enjoyed a roller coaster'
    }

    def 'should go to boardwalk and lose game'() {
        when:
        workflowExecuter.start('Clay', [BayAreaLocation.GoldenGateBridge, BayAreaLocation.Redwoods])

        then:
        workflowOperations.logHistory == [
                'Clay went to the Santa Cruz Boardwalk.',
                'And lost a carnival game. 3 times.'
        ]
        0 * _
        then: 1 * mockActivities.askYesNoQuestion('Do you like roller coasters?') >> true
        then: 1 * mockActivities.goTo('Clay', BayAreaLocation.Boardwalk) >> 'Clay went to the Santa Cruz Boardwalk.'
        then: 3 * mockActivities.win('a carnival game') >> {
            throw new IllegalStateException('And lost a carnival game.')
        }
    }

    def 'should go to Monterey'() {
        when:
        workflowExecuter.start('Clay', [BayAreaLocation.GoldenGateBridge, BayAreaLocation.Redwoods])

        then:
        workflowOperations.logHistory == [
                'Clay went to Monterey Bay.',
                'And enjoyed eating seafood. And enjoyed watching sea lions.',
                'And enjoyed looking for sea glass on the beach.',
                'And enjoyed the 17-Mile Drive.',
        ]
        0 * _
        then: 1 * mockActivities.askYesNoQuestion('Do you like roller coasters?') >> false
        then: 1 * mockActivities.goTo('Clay', BayAreaLocation.Monterey) >> 'Clay went to Monterey Bay.'
        then: with(mockActivities) {
            1 * enjoy('eating seafood') >> 'And enjoyed eating seafood.'
            1 * enjoy('watching sea lions') >> 'And enjoyed watching sea lions.'
        }
        then: 1 * mockActivities.enjoy('looking for sea glass on the beach') >>
                'And enjoyed looking for sea glass on the beach.'
        then: 1 * mockActivities.enjoy('the 17-Mile Drive') >> 'And enjoyed the 17-Mile Drive.'
    }

    def 'should go to Monterey and get rained on'() {
        when:
        workflowExecuter.start('Clay', [BayAreaLocation.GoldenGateBridge, BayAreaLocation.Redwoods])

        then:
        workflowOperations.logHistory == [
                'Clay went to Monterey Bay.',
                'And enjoyed eating seafood. And enjoyed watching sea lions.',
                'And skipped the beach because it was raining!',
                'And enjoyed the aquarium.',
                'And enjoyed the 17-Mile Drive.'
        ]
        0 * _
        then: 1 * mockActivities.askYesNoQuestion('Do you like roller coasters?') >> false
        then: 1 * mockActivities.goTo('Clay', BayAreaLocation.Monterey) >> 'Clay went to Monterey Bay.'
        then: with(mockActivities) {
            1 * enjoy('eating seafood') >> 'And enjoyed eating seafood.'
            1 * enjoy('watching sea lions') >> 'And enjoyed watching sea lions.'
        }
        then: 1 * mockActivities.enjoy('looking for sea glass on the beach') >> {
            throw new IllegalStateException('And skipped the beach because it was raining!')
        }
        then: 1 * mockActivities.enjoy('the aquarium') >> 'And enjoyed the aquarium.'
        then: 1 * mockActivities.enjoy('the 17-Mile Drive') >> 'And enjoyed the 17-Mile Drive.'
    }
}
