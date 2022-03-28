/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.command.client.handlers

import akka.http.scaladsl.testkit.ScalatestRouteTest
import csw.aas.http.SecurityDirectives
import csw.command.api.codecs.CommandServiceCodecs
import csw.command.api.messages.CommandServiceRequest
import csw.command.api.messages.CommandServiceRequest.{Oneway, Query, Submit, Validate}
import csw.command.api.scaladsl.CommandService
import csw.command.client.handlers.TestHelper.Narrower
import csw.commons.RandomUtils
import csw.params.commands.CommandResponse.*
import csw.params.commands.{CommandName, Setup}
import csw.params.core.models.Id
import csw.prefix.models.{Prefix, Subsystem}
import msocket.http.post.{PostRouteFactory, ServerHttpCodecs}
import msocket.jvm.metrics.LabelExtractor
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.concurrent.Future

class CommandServiceHttpHandlerTest
    extends AnyFunSuite
    with ScalatestRouteTest
    with Matchers
    with CommandServiceCodecs
    with ServerHttpCodecs
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  private val securityDirective = mock[SecurityDirectives]
  private val commandService    = mock[CommandService]
  private val handler           = new CommandServiceRequestHandler(commandService, securityDirective, None)

  import LabelExtractor.Implicits.default
  private val route = new PostRouteFactory[CommandServiceRequest]("post-endpoint", handler).make()

  private val subsystem = randomSubsystem
  private val command   = Setup(Prefix(subsystem, RandomUtils.randomString5()), CommandName(RandomUtils.randomString5()), None)
  private val started   = Started(Id())
  private val accepted  = Accepted(Id())

  override protected def beforeEach(): Unit = {
    reset(securityDirective)
    reset(commandService)
    super.beforeEach()
  }

  test("Submit must be delegated to commandService.submit") {
    when(commandService.submit(command)).thenReturn(Future.successful(started))

    Post("/post-endpoint", Submit(command).narrow) ~> route ~> check {
      verify(commandService).submit(command)
      responseAs[SubmitResponse] should ===(started)
    }
  }

  test("Oneway must be delegated to commandService.oneway") {
    when(commandService.oneway(command)).thenReturn(Future.successful(accepted))

    Post("/post-endpoint", Oneway(command).narrow) ~> route ~> check {
      verify(commandService).oneway(command)
      responseAs[OnewayResponse] should ===(accepted)
    }
  }

  test("Validate must be delegated to commandService.validate") {
    when(commandService.validate(command)).thenReturn(Future.successful(accepted))

    Post("/post-endpoint", Validate(command).narrow) ~> route ~> check {
      verify(commandService).validate(command)
      responseAs[ValidateResponse] should ===(accepted)
    }
  }

  test("Query must be delegated to commandService.query") {
    val id             = Id()
    val randomResponse = RandomUtils.randomFrom(List(Cancelled(id), Started(id), Completed(id), Locked(id)))
    when(commandService.query(id)).thenReturn(Future.successful(randomResponse))

    Post("/post-endpoint", Query(id).narrow) ~> route ~> check {
      verify(commandService).query(id)
      responseAs[SubmitResponse] should ===(randomResponse)
    }
  }

  private def randomSubsystem: Subsystem = RandomUtils.randomFrom(Subsystem.values)
}
