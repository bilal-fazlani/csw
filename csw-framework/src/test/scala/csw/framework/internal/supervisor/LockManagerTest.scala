package csw.framework.internal.supervisor

import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.{typed, ActorSystem}
import csw.command.client.messages.CommandMessage.Submit
import csw.command.client.models.framework.LockingResponse
import csw.command.client.models.framework.LockingResponses._
import csw.logging.api.scaladsl.Logger
import csw.logging.client.scaladsl.LoggerFactory
import csw.params.commands.CommandResponse.SubmitResponse
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{KeyType, Parameter}
import csw.params.core.models.{ObsId, Prefix}
import org.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

// DEOPSCSW-222: Locking a component for a specific duration
// DEOPSCSW-301: Support UnLocking
// DEOPSCSW-302: Support Unlocking by Admin
class LockManagerTest extends FunSuite with MockitoSugar with Matchers {

  private val prefix        = Prefix("tcs.mobie.blue.filter")
  private val invalidPrefix = Prefix("tcs.mobie.blue.filter.invalid")

  implicit val system: ActorSystem                     = ActorSystem()
  implicit val typedSystem: typed.ActorSystem[Nothing] = system.toTyped
  implicit val testKitSettings: TestKitSettings        = TestKitSettings(typedSystem)

  private val intParam: Parameter[Int]    = KeyType.IntKey.make("intKey").set(1, 2, 3)
  private val setup: Setup                = Setup(prefix, CommandName("move"), Some(ObsId("obs1001")), Set(intParam))
  private val invalidSetup: Setup         = Setup(invalidPrefix, CommandName("move"), Some(ObsId("obs1001")), Set(intParam))
  private val AdminKey                    = "CSW_ADMIN_PREFIX"
  private def adminPrefix: Option[Prefix] = (sys.env ++ sys.props).get(AdminKey).map(Prefix(_))
  private val mockedLoggerFactory         = mock[LoggerFactory]
  private val mockedLogger                = mock[Logger]
  when(mockedLoggerFactory.getLogger).thenReturn(mockedLogger)

  test("should be locked when prefix is available") {
    val lockManager = new LockManager(Some(prefix), adminPrefix, mockedLoggerFactory)
    lockManager.isLocked shouldBe true
    lockManager.isUnLocked shouldBe false
  }

  test("should be unlocked when prefix is not available") {
    val lockManager = new LockManager(None, adminPrefix, mockedLoggerFactory)
    lockManager.isLocked shouldBe false
    lockManager.isUnLocked shouldBe true
  }

  test("should able to lock") {
    val lockingResponseProbe = TestProbe[LockingResponse]

    val lockManager = new LockManager(None, adminPrefix, mockedLoggerFactory)
    lockManager.isLocked shouldBe false

    val updatedLockManager = lockManager.lockComponent(prefix, lockingResponseProbe.ref)(Unit)
    lockingResponseProbe.expectMessage(LockAcquired)
    updatedLockManager.isLocked shouldBe true
  }

  test("should able to reacquire lock") {
    val lockingResponseProbe = TestProbe[LockingResponse]

    val lockManager = new LockManager(Some(prefix), adminPrefix, mockedLoggerFactory)
    lockManager.isLocked shouldBe true

    val updatedLockManager = lockManager.lockComponent(prefix, lockingResponseProbe.ref)(Unit)
    lockingResponseProbe.expectMessage(LockAcquired)
    updatedLockManager.isLocked shouldBe true
  }

  test("should not acquire lock when invalid prefix is provided") {
    val lockingResponseProbe = TestProbe[LockingResponse]

    val lockManager = new LockManager(Some(prefix), adminPrefix, mockedLoggerFactory)
    lockManager.isLocked shouldBe true

    val updatedLockManager = lockManager.lockComponent(invalidPrefix, lockingResponseProbe.ref)(Unit)
    lockingResponseProbe.expectMessageType[AcquiringLockFailed]
    updatedLockManager.isLocked shouldBe true
    updatedLockManager.lockPrefix.get shouldBe prefix
  }

  test("should able to unlock") {
    val lockingResponseProbe = TestProbe[LockingResponse]

    val lockManager = new LockManager(Some(prefix), adminPrefix, mockedLoggerFactory)
    lockManager.isUnLocked shouldBe false

    val updatedLockManager = lockManager.unlockComponent(prefix, lockingResponseProbe.ref)(Unit)
    lockingResponseProbe.expectMessage(LockReleased)
    updatedLockManager.isUnLocked shouldBe true
  }

  test("should not able to unlock with invalid prefix") {
    val lockingResponseProbe = TestProbe[LockingResponse]

    val lockManager = new LockManager(Some(prefix), adminPrefix, mockedLoggerFactory)
    lockManager.isUnLocked shouldBe false

    val updatedLockManager = lockManager.unlockComponent(invalidPrefix, lockingResponseProbe.ref)(Unit)
    lockingResponseProbe.expectMessageType[ReleasingLockFailed]
    updatedLockManager.isUnLocked shouldBe false
  }

  test("should not result into failure when tried to unlock already unlocked component") {
    val lockingResponseProbe = TestProbe[LockingResponse]

    val lockManager = new LockManager(None, adminPrefix, mockedLoggerFactory)
    lockManager.isUnLocked shouldBe true

    val updatedLockManager = lockManager.unlockComponent(prefix, lockingResponseProbe.ref)(Unit)
    lockingResponseProbe.expectMessage(LockAlreadyReleased)
    updatedLockManager.isUnLocked shouldBe true
  }

  test("should allow commands when component is not locked") {
    val commandResponseProbe = TestProbe[SubmitResponse]

    val lockManager = new LockManager(None, adminPrefix, mockedLoggerFactory)
    lockManager.isUnLocked shouldBe true

    lockManager.allowCommand(Submit(setup, commandResponseProbe.ref)) shouldBe true
  }

  test("should allow commands when component is locked with same prefix") {
    val commandResponseProbe = TestProbe[SubmitResponse]

    val lockManager = new LockManager(Some(prefix), adminPrefix, mockedLoggerFactory)
    lockManager.isLocked shouldBe true

    lockManager.allowCommand(Submit(setup, commandResponseProbe.ref)) shouldBe true
  }

  test("should not allow commands when component is locked with different prefix") {
    val commandResponseProbe = TestProbe[SubmitResponse]

    val lockManager = new LockManager(Some(prefix), adminPrefix, mockedLoggerFactory)
    lockManager.isLocked shouldBe true

    lockManager.allowCommand(Submit(invalidSetup, commandResponseProbe.ref)) shouldBe false

  }

  test("should allow unlocking any locked component by admin") {
    val commandResponseProbe = TestProbe[SubmitResponse]

    System.setProperty(AdminKey, "Admin")
    val lockManager = new LockManager(Some(prefix), adminPrefix, mockedLoggerFactory)
    lockManager.isLocked shouldBe true

    val adminSetup: Setup = Setup(Prefix("admin123"), CommandName("move"), Some(ObsId("obs1001")), Set(intParam))
    lockManager.allowCommand(Submit(adminSetup, commandResponseProbe.ref)) shouldBe false

    val probe               = TestProbe[LockingResponse]
    val unlockedLockManager = lockManager.unlockComponent(Prefix("Admin"), probe.ref) {}

    unlockedLockManager.allowCommand(Submit(adminSetup, commandResponseProbe.ref)) shouldBe true

  }
}
