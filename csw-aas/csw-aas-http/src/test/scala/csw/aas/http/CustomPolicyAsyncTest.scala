package csw.aas.http

import akka.http.javadsl.server.{AuthenticationFailedRejection, AuthorizationFailedRejection}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import csw.aas.core.token.AccessToken
import csw.aas.http.AuthorizationPolicy.CustomPolicyAsync
import org.mockito.MockitoSugar

import scala.concurrent.Future
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

//DEOPSCSW-579: Prevent unauthorized access based on akka http route rules
class CustomPolicyAsyncTest extends AnyFunSuite with MockitoSugar with Directives with ScalatestRouteTest with Matchers {

  test("custom policy async should return AuthenticationFailedRejection when token is invalid | DEOPSCSW-579") {
    val authentication: Authentication = mock[Authentication]
    val securityDirectives             = new SecurityDirectives(authentication, "TMT", "test", false)

    val invalidTokenStr    = "invalid"
    val invalidTokenHeader = Authorization(OAuth2BearerToken(invalidTokenStr))

    val authenticator: AsyncAuthenticator[AccessToken] = {
      case Provided(`invalidTokenStr`) => Future.successful(None)
      case _                           => Future.successful(None)
    }

    when(authentication.authenticator).thenReturn(authenticator)

    val route: Route =
      securityDirectives.authenticate { implicit at =>
        get {
          securityDirectives.authorize(CustomPolicyAsync(token => Future.successful(token.given_name.contains("John"))), at) {
            complete("OK")
          }
        }
      }

    Get("/test").addHeader(invalidTokenHeader) ~> route ~> check {
      rejection shouldBe a[AuthenticationFailedRejection]
    }
  }

  test("custom policy async should return AuthenticationFailedRejection when token is not present | DEOPSCSW-579") {
    val authentication: Authentication = mock[Authentication]
    val securityDirectives             = new SecurityDirectives(authentication, "TMT", "test", false)

    val authenticator: AsyncAuthenticator[AccessToken] = _ => Future.successful(None)

    when(authentication.authenticator).thenReturn(authenticator)

    val route: Route = securityDirectives.authenticate { implicit at =>
      get {
        securityDirectives.authorize(CustomPolicyAsync(_ => Future.successful(false)), at) {
          complete("OK")
        }
      }
    }

    Get("/") ~> route ~> check {
      rejection shouldBe a[AuthenticationFailedRejection]
    }
  }

  test("custom policy async should return AuthorizationFailedRejection when policy does not match | DEOPSCSW-579") {
    val authentication: Authentication = mock[Authentication]
    val securityDirectives             = new SecurityDirectives(authentication, "TMT", "test", false)

    val validTokenWithPolicyViolationStr    = "validTokenWithPolicyViolation"
    val validTokenWithPolicyViolationHeader = Authorization(OAuth2BearerToken(validTokenWithPolicyViolationStr))

    val validTokenWithPolicyViolation = mock[AccessToken]

    val authenticator: AsyncAuthenticator[AccessToken] = {
      case Provided(`validTokenWithPolicyViolationStr`) => Future.successful(Some(validTokenWithPolicyViolation))
      case _                                            => Future.successful(None)
    }

    when(authentication.authenticator).thenReturn(authenticator)

    val route: Route = securityDirectives.authenticate { implicit at =>
      get {
        securityDirectives.authorize(CustomPolicyAsync(_ => Future.successful(false)), at) {
          complete("OK")
        }
      }
    }

    Get("/").addHeader(validTokenWithPolicyViolationHeader) ~> route ~> check {
      rejection shouldBe a[AuthorizationFailedRejection]
    }
  }

  test("custom policy async should return 200 OK when policy matches | DEOPSCSW-579") {
    val authentication: Authentication = mock[Authentication]
    val securityDirectives             = new SecurityDirectives(authentication, "TMT", "test", false)

    val validTokenWithPolicyMatchStr    = "validTokenWithPolicyMatch"
    val validTokenWithPolicyMatchHeader = Authorization(OAuth2BearerToken(validTokenWithPolicyMatchStr))

    val validTokenWithPolicyMatch = mock[AccessToken]

    val authenticator: AsyncAuthenticator[AccessToken] = {
      case Provided(`validTokenWithPolicyMatchStr`) => Future.successful(Some(validTokenWithPolicyMatch))
      case _                                        => Future.successful(None)
    }

    when(authentication.authenticator).thenReturn(authenticator)

    val route: Route = securityDirectives.authenticate { implicit at =>
      get {
        securityDirectives.authorize(CustomPolicyAsync(_ => Future.successful(true)), at) {
          complete("OK")
        }
      }
    }

    Get("/").addHeader(validTokenWithPolicyMatchHeader) ~> route ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("custom policy async should return AuthorizationFailedRejection when async execution fails | DEOPSCSW-579") {
    val authentication: Authentication = mock[Authentication]
    val securityDirectives             = new SecurityDirectives(authentication, "TMT", "test", false)

    val validTokenWithPolicyMatchStr    = "validTokenWithPolicyMatch"
    val validTokenWithPolicyMatchHeader = Authorization(OAuth2BearerToken(validTokenWithPolicyMatchStr))

    val validTokenWithPolicyMatch = mock[AccessToken]

    val authenticator: AsyncAuthenticator[AccessToken] = {
      case Provided(`validTokenWithPolicyMatchStr`) => Future.successful(Some(validTokenWithPolicyMatch))
      case _                                        => Future.successful(None)
    }

    when(authentication.authenticator).thenReturn(authenticator)

    val route: Route = securityDirectives.authenticate { implicit at =>
      get {
        securityDirectives.authorize(CustomPolicyAsync(_ => Future.failed(new RuntimeException("failure has failed"))), at) {
          complete("OK")
        }
      }
    }

    Get("/").addHeader(validTokenWithPolicyMatchHeader) ~> route ~> check {
      rejection shouldBe a[AuthorizationFailedRejection]
    }
  }
}
