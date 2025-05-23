/*
 * Copyright (c) 2022 Thirty Meter Telescope International Observatory
 * SPDX-License-Identifier: Apache-2.0
 */

package csw.aas.core

import csw.aas.core.TokenVerificationFailure.{InvalidToken, TokenExpired}
import csw.aas.core.deployment.AuthConfig
import msocket.security.models.{Access, AccessToken, Audience, Authorization}
import org.keycloak.adapters.KeycloakDeployment
import org.keycloak.exceptions.{TokenNotActiveException, TokenSignatureInvalidException}
import org.keycloak.representations.{AccessToken => KeycloakAccessToken}
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

//DEOPSCSW-558: SPIKE: Token containing user info and roles
//DEOPSCSW-579: Prevent unauthorized access based on pekko http route rules
class TokenVerifierTest extends AnyFunSuite with MockitoSugar with Matchers with EitherValues {
  private val keycloakTokenVerifier: KeycloakTokenVerifier = mock[KeycloakTokenVerifier]
  private val authConfig: AuthConfig                       = mock[AuthConfig]
  private val deployment: KeycloakDeployment               = authConfig.getDeployment

  when(authConfig.getDeployment).thenReturn(deployment)

  private val keycloakAccessToken: KeycloakAccessToken = new KeycloakAccessToken()
  private val tmtTokenVerifier: TokenVerifier          = new TokenVerifier(keycloakTokenVerifier, authConfig)

  test("should throw exception while verifyAndDecode token | DEOPSCSW-558, DEOPSCSW-579") {
    val token                  = "test-token"
    val validationExceptionMsg = "invalid token"
    val validationException    = new TokenSignatureInvalidException(keycloakAccessToken, validationExceptionMsg)

    when(keycloakTokenVerifier.verifyToken(token, deployment)).thenReturn(Future.failed(validationException))

    Await.result(tmtTokenVerifier.verifyAndDecode(token), 5.seconds).left.value shouldBe InvalidToken(validationExceptionMsg)
  }

  test("should throw TokenExpired exception while verifying token | DEOPSCSW-558, DEOPSCSW-579") {
    val token                    = "test-token"
    val tokenExpiredExceptionMsg = "Token expired"
    val tokenExpiredException    = new TokenNotActiveException(keycloakAccessToken, tokenExpiredExceptionMsg)

    when(keycloakTokenVerifier.verifyToken(token, deployment)).thenReturn(Future.failed(tokenExpiredException))

    Await.result(tmtTokenVerifier.verifyAndDecode(token), 5.seconds).left.value shouldBe TokenExpired
  }

  // DEOPSCSW-578: Programming Interface for accessing userinfo
  test("should verifyAndDecode token | DEOPSCSW-558, DEOPSCSW-579, DEOPSCSW-578") {
    val token =
      "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJneWlMbndmWWR3SmNENUdnUnJXMUFCNmNzeVBLZWFPb2lMZHp4dnFBeUZjIn0.eyJqdGkiOiJlN2IzNzAwYi0wNjgxLTQ4MzQtOWE0Zi0yMjU5YmUzOWRiZTUiLCJleHAiOjE1NDI4MjI2MTcsIm5iZiI6MCwiaWF0IjoxNTQyODIyNTU3LCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvYXV0aC9yZWFsbXMvbWFzdGVyIiwiYXVkIjoibmV3LXRlc3QiLCJzdWIiOiI3ZGQ0NmU2ZS01YTk4LTRlZTYtOTFiZS02OWJmYjYwN2ZlYjQiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJuZXctdGVzdCIsIm5vbmNlIjoiYWNjODJmYWMtYzRkNS00NDhmLTg0NDQtNmJmZGQ0MzQ4YWMzIiwiYXV0aF90aW1lIjoxNTQyODIyNTU2LCJzZXNzaW9uX3N0YXRlIjoiMmU4OTdlNDctZTlhMi00YTQ4LWI4MjAtNWJlMTE4YmY5YjVmIiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyIqIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoiZW1tYSIsImdpdmVuX25hbWUiOiIiLCJmYW1pbHlfbmFtZSI6IiJ9.DFT0YnDoOmm9fp0xrR7ObzB9th_WVzDi3cNPEWURgHjtJUqWhA_gyl3fs6LKDt00rL82jVLhA8T7L8BeclgubcCDD8iytb_Nr_xtBdCTZ47YSjXfHsUD6x4s8ltHVMflaPEqMoL2JrBio552Z-6denhKzcdvUXePBDcZrdkxnLIIwA5pbc6ZM4LWJFpDBqdzH0r2a-DBMCEqdRK-o_aqx43c8H0KPXJtftR74YF4WV5X8uASsEIYL6P31inhK1r0fv0ifdTNpkoHaAG_qST4ppD14pvWCKylpT5jSM5hfc-H78m3PPwb93nv3JVC5kMO-yBmStEe9fyTSPCOd97eoQ"
    val expectedToken = AccessToken(
      value = token,
      sub = Option("7dd46e6e-5a98-4ee6-91be-69bfb607feb4"),
      iat = Option(1542822557),
      exp = Option(1542822617),
      iss = Option("http://localhost:8080/auth/realms/master"),
      aud = Audience("new-test"),
      jti = Option("e7b3700b-0681-4834-9a4f-2259be39dbe5"),
      given_name = Option(""),
      family_name = Option(""),
      name = None,
      preferred_username = Option("emma"),
      email = None,
      scope = Option("openid email profile"),
      realm_access = Access(Set("offline_access", "uma_authorization")),
      resource_access = Map("account" -> Access(Set("manage-account", "manage-account-links", "view-profile"))),
      authorization = Authorization.empty
    )

    when(keycloakTokenVerifier.verifyToken(token, deployment)).thenReturn(Future.successful(keycloakAccessToken))
    Await.result(tmtTokenVerifier.verifyAndDecode(token), 5.seconds).toOption.get shouldBe expectedToken
  }

  test("should throw exception while decoding token | DEOPSCSW-558, DEOPSCSW-579") {
    val invalidJsonToken     = "ey.wer.erwe.werw"
    val expectedExceptionMsg = "Expected token [ey.wer.erwe.werw] to be composed of 2 or 3 parts separated by dots."

    when(keycloakTokenVerifier.verifyToken(invalidJsonToken, deployment)).thenReturn(Future.successful(keycloakAccessToken))
    Await.result(tmtTokenVerifier.verifyAndDecode(invalidJsonToken), 5.seconds).left.value shouldBe InvalidToken(
      expectedExceptionMsg
    )
  }
}
