/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.network.utils.internal

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

class BlockingUtilsTest extends AnyFunSuite with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  test("test that Poll method bottoms out and returns expected result") {
    BlockingUtils.poll(predicate = true) shouldBe true
    BlockingUtils.poll(predicate = false, 1.seconds) shouldBe false
  }

  test("test that Poll method detects predicate fulfillment") {

    import scala.concurrent.ExecutionContext.Implicits.global

    val upMembers                  = 10
    val replicaCountF: Future[Int] = Future { Thread.sleep(200); 10 }
    def replicaCount: Int =
      if (replicaCountF.isCompleted) replicaCountF.value match {
        case Some(Success(v)) => v
        case _                => -1
      }
      else -1

    def predicate = replicaCount == upMembers

    val result = BlockingUtils.poll(predicate, 10.seconds)

    result shouldBe true
  }
}
