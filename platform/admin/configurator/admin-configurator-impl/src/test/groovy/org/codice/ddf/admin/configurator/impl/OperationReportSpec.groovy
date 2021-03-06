/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.admin.configurator.impl

import org.codice.ddf.admin.configurator.ConfiguratorException
import org.codice.ddf.admin.configurator.OperationReport
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import spock.lang.Specification

@RunWith(JUnitPlatform.class)
class OperationReportSpec extends Specification {
    OperationReport report

    def setup() {
        report = new OperationReportImpl()
    }

    def 'check report with no failures'() {
        setup:
        def pass1 = ResultImpl.pass()
        def pass2 = ResultImpl.pass()
        def a1 = UUID.randomUUID()
        def b2 = UUID.randomUUID()

        when:
        report.putResult(a1, pass1)
        report.putResult(b2, pass2)

        then:
        report.hasTransactionSucceeded()
        !report.containsFailedResults()
        report.getFailedResults() == []
        report.getResult(a1) == pass1
        report.getResult(b2) == pass2
    }

    def 'report with failures'() {
        setup:
        def pass1 = ResultImpl.pass()
        def reportThrowable = Mock(ConfiguratorException)
        def fail1 = ResultImpl.fail(reportThrowable)
        def a1 = UUID.randomUUID()
        def b2 = UUID.randomUUID()

        when:
        report.putResult(a1, pass1)
        report.putResult(b2, fail1)

        then:
        !report.hasTransactionSucceeded()
        report.containsFailedResults()
        report.getFailedResults() == [fail1]
        report.getResult(a1) == pass1
        report.getResult(b2) == fail1
        report.getResult(b2).error.get() == reportThrowable
    }

    def 'pass with a managed service'() {
        setup:
        def pass1 = ResultImpl.pass()
        def pass2 = ResultImpl.passWithData('serviceId1')
        def a1 = UUID.randomUUID()
        def b2 = UUID.randomUUID()

        when:
        report.putResult(a1, pass1)
        report.putResult(b2, pass2)

        then:
        report.hasTransactionSucceeded()
        !report.containsFailedResults()
        report.getFailedResults() == []
        report.getResult(a1) == pass1
        report.getResult(b2) == pass2
        report.getResult(b2).operationData.isPresent()
        report.getResult(b2).operationData.get() == 'serviceId1'
    }

    def 'rollback success'() {
        setup:
        def pass1 = ResultImpl.pass()
        def roll1 = ResultImpl.rollback()
        def a1 = UUID.randomUUID()
        def b2 = UUID.randomUUID()

        when:
        report.putResult(a1, pass1)
        report.putResult(b2, roll1)

        then:
        !report.hasTransactionSucceeded()
        report.containsFailedResults()
        report.getFailedResults() == [roll1]
        report.getResult(a1) == pass1
        report.getResult(b2) == roll1
    }

    def 'rollback failed'() {
        setup:
        def pass1 = ResultImpl.pass()
        def rollbackThrowable = Mock(ConfiguratorException)
        def roll1 = ResultImpl.rollbackFail(rollbackThrowable)
        def a1 = UUID.randomUUID()
        def b2 = UUID.randomUUID()

        when:
        report.putResult(a1, pass1)
        report.putResult(b2, roll1)

        then:
        !report.hasTransactionSucceeded()
        report.containsFailedResults()
        report.getFailedResults() == [roll1]
        report.getResult(a1) == pass1
        report.getResult(b2) == roll1
        report.getResult(b2).error.get() == rollbackThrowable
    }

    def 'rollback failed on a managed service'() {
        setup:
        def pass1 = ResultImpl.pass()
        def rollbackDatathrowable = Mock(ConfiguratorException)
        def roll1 = ResultImpl.rollbackFailWithData(rollbackDatathrowable, 'serviceId1')
        def a1 = UUID.randomUUID()
        def b2 = UUID.randomUUID()

        when:
        report.putResult(a1, pass1)
        report.putResult(b2, roll1)

        then:
        !report.hasTransactionSucceeded()
        report.containsFailedResults()
        report.getFailedResults() == [roll1]
        report.getResult(a1) == pass1
        report.getResult(b2) == roll1
        report.getResult(b2).error.get() == rollbackDatathrowable
        report.getResult(b2).operationData.isPresent()
        report.getResult(b2).operationData.get() == 'serviceId1'
    }

    def 'test skipped rollback step'() {
        setup:
        def roll1 = ResultImpl.rollback()
        def skip1 = ResultImpl.skip()
        def b2 = UUID.randomUUID()
        def c3 = UUID.randomUUID()

        when:
        report.putResult(b2, roll1)
        report.putResult(c3, skip1)

        then:
        !report.hasTransactionSucceeded()
        report.containsFailedResults()
        report.getFailedResults() == [roll1, skip1]
        report.getResult(b2) == roll1
        report.getResult(c3) == skip1
    }
}
