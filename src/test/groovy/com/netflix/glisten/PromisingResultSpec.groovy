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

class PromisingResultSpec extends Specification {

    PromisingResult<String> promiseString = new PromisingResult()

    String quote = "I loved her against reason, against promise, against peace, against hope, against happiness, " +
            "against all discouragement that could be. ― Charles Dickens, Great Expectations"

    def 'should wrap value in promise'() {
        when:
        Promise<String> promise = PromisingResult.wrapWithPromise(quote)

        then:
        promise.ready
        promise.get() == quote
    }

    def 'should not wrap promise again'() {
        when:
        Promise<String> promise = PromisingResult.wrapWithPromise(Promise.asPromise(quote))

        then:
        promise.ready
        promise.get() == quote
    }

    def 'should chain to promise'() {
        when:
        promiseString.chain(Promise.asPromise(quote))

        then:
        promiseString.ready
        promiseString.get() == quote
    }

    def 'should chain to unready chained promise'() {
        when:
        promiseString.chain(new Settable())

        then:
        !promiseString.ready

        when:
        promiseString.chain(Promise.asPromise(quote))

        then:
        promiseString.ready
        promiseString.get() == quote
    }

    def 'should chain to ready chained promise'() {
        when:
        promiseString.chain(Promise.asPromise(quote))

        then:
        promiseString.ready
        promiseString.get() == quote

        when:
        promiseString.chain(Promise.asPromise(quote))

        then:
        promiseString.ready
        promiseString.get() == quote
    }

}
