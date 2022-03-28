/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.aas.http

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import csw.aas.http.AuthorizationPolicy.{CustomPolicy, CustomPolicyAsync, EmptyPolicy, RealmRolePolicy}
import msocket.security.models.AccessToken
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

//DEOPSCSW-579: Prevent unauthorized access based on akka http route rules
class AuthorizationPolicyTest
    extends AnyFunSuite
    with MockitoSugar
    with Directives
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures {

  private implicit val patience: PatienceConfig = PatienceConfig(1.seconds, 100.millis)

  test("RealmRolePolicy authorize should return true if token has realm role | DEOPSCSW-579") {
    val accessToken     = mock[AccessToken]
    val realmRolePolicy = RealmRolePolicy("some-role")
    when(accessToken.hasRealmRole("some-role")).thenReturn(true)

    realmRolePolicy.authorize(accessToken).futureValue shouldBe true

    verify(accessToken).hasRealmRole("some-role")
  }

  test("RealmRolePolicy authorize should return false if token does not has realm role | DEOPSCSW-579") {
    val accessToken     = mock[AccessToken]
    val realmRolePolicy = RealmRolePolicy("some-role")
    when(accessToken.hasRealmRole("some-role")).thenReturn(false)

    realmRolePolicy.authorize(accessToken).futureValue shouldBe false

    verify(accessToken).hasRealmRole("some-role")
  }

  test("CustomPolicyAsync authorize should return true if token has realm role | DEOPSCSW-579") {
    val accessToken = mock[AccessToken]
    when(accessToken.hasRealmRole("some-role")).thenReturn(true)
    val predicate: AccessToken => Future[Boolean] = token => Future.successful(token.hasRealmRole("some-role"))
    val customPolicyAsync                         = CustomPolicyAsync(predicate)

    customPolicyAsync.authorize(accessToken).futureValue shouldBe true

  }

  test("CustomPolicyAsync authorize should return false if token does not has realm role | DEOPSCSW-579") {
    val accessToken = mock[AccessToken]
    when(accessToken.hasRealmRole("some-role")).thenReturn(false)
    val predicate: AccessToken => Future[Boolean] = token => Future.successful(token.hasRealmRole("some-role"))
    val customPolicyAsync                         = CustomPolicyAsync(predicate)

    customPolicyAsync.authorize(accessToken).futureValue shouldBe false

  }

  test("CustomPolicy authorize should return true if token has realm role | DEOPSCSW-579") {
    val accessToken = mock[AccessToken]
    when(accessToken.hasRealmRole("some-role")).thenReturn(true)
    val predicate: AccessToken => Boolean = token => token.hasRealmRole("some-role")
    val customPolicy                      = CustomPolicy(predicate)

    customPolicy.authorize(accessToken).futureValue shouldBe true

  }

  test("CustomPolicy authorize should return false if token does not has realm role | DEOPSCSW-579") {
    val accessToken = mock[AccessToken]
    when(accessToken.hasRealmRole("some-role")).thenReturn(false)
    val predicate: AccessToken => Boolean = token => token.hasRealmRole("some-role")
    val customPolicy                      = CustomPolicy(predicate)

    customPolicy.authorize(accessToken).futureValue shouldBe false

  }

  test("EmptyPolicy authorize should always return true | DEOPSCSW-579") {
    val accessToken = mock[AccessToken]
    when(accessToken.hasRealmRole("some-role")).thenReturn(false)
    val emptyPolicy = EmptyPolicy

    emptyPolicy.authorize(accessToken).futureValue shouldBe true

  }
}
