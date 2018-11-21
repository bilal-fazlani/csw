package csw.auth
import csw.auth.TokenVerificationFailure.{InvalidToken, TokenExpired}
import csw.auth.token.AccessToken
import csw.auth.token.claims.{Access, Audience, Authorization, Permission}
import org.keycloak.adapters.KeycloakDeployment
import org.keycloak.adapters.rotation.AdapterTokenVerifier
import org.keycloak.common.VerificationException
import org.keycloak.exceptions.TokenNotActiveException
import org.keycloak.representations.idm.authorization
import org.keycloak.representations.{AccessToken => KeycloakAccessToken}

import scala.collection.JavaConverters._

class KeycloakTokenVerifier {
  def verifyToken(token: String, keycloakDeployment: KeycloakDeployment): KeycloakAccessToken =
    AdapterTokenVerifier.verifyToken(token, Keycloak.deployment)
}

class TMTTokenVerifier private[auth] (keycloakTokenVerifier: KeycloakTokenVerifier) {

  def verifyAndDecode(token: String): Either[TokenVerificationFailure, AccessToken] = {

    val keycloakToken: Either[TokenVerificationFailure, KeycloakAccessToken] = try {
      Right(keycloakTokenVerifier.verifyToken(token, Keycloak.deployment))
    } catch {
      case ex: TokenNotActiveException =>
        Left(TokenExpired)
      case ex: VerificationException =>
        Left(InvalidToken(ex.getMessage))
    }

    keycloakToken
      .map(convert)

  }

  private def convert(keycloakAccessToken: KeycloakAccessToken): AccessToken = {

    val keycloakPermissions: Option[Set[authorization.Permission]] =
      Option(keycloakAccessToken.getAuthorization)
        .flatMap(
          authorization => {
            Option(authorization.getPermissions).map(_.asScala).map(_.toSet)
          }
        )

    //todo: Remove var
    var resourceAccess: Map[String, Access] = Map.empty

    Option(keycloakAccessToken.getResourceAccess).foreach(
      ra =>
        ra.forEach(
          (key, access) => resourceAccess += key -> Access(Option(access.getRoles).map(_.asScala).map(_.toSet))
      )
    )

    val realmRoles = Option(keycloakAccessToken.getRealmAccess).map(_.getRoles).map(_.asScala).map(_.toSet)

    val accessToken = AccessToken(
      sub = Option(keycloakAccessToken.getSubject),
      iat = Option(keycloakAccessToken.getIssuedAt).map(_.toLong),
      exp = Option(keycloakAccessToken.getExpiration).map(_.toLong),
      iss = Option(keycloakAccessToken.getIssuer),
      aud = Option(keycloakAccessToken.getAudience).map(a ⇒ Audience(a.toSeq)),
      jti = Option(keycloakAccessToken.getId),
      given_name = Option(keycloakAccessToken.getGivenName),
      family_name = Option(keycloakAccessToken.getFamilyName),
      name = Option(keycloakAccessToken.getFamilyName),
      preferred_username = Option(keycloakAccessToken.getPreferredUsername),
      email = Option(keycloakAccessToken.getEmail),
      scope = Option(keycloakAccessToken.getScope),
      realm_access = Option(Access(realmRoles)),
      resource_access = Option(resourceAccess),
      authorization = Option(Authorization(keycloakPermissions.map(getPermissions)))
    )

    accessToken
  }

  private def getPermissions(kpermissions: Set[authorization.Permission]): Set[Permission] = {
    kpermissions.map(
      permission =>
        Permission(
          permission.getResourceId,
          permission.getResourceName,
          Option(permission.getScopes).map(_.asScala).map(_.toSet)
      )
    )
  }

}

object TMTTokenVerifier {
  def apply(): TMTTokenVerifier = new TMTTokenVerifier(new KeycloakTokenVerifier)
}