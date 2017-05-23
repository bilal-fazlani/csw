package csw.services.logging

import java.net.InetAddress

object LoggingSystemFactory extends GenericLogger.Simple {
  def make(): LoggingSystem =
    LoggingSystem(log, "serviceName1", "serviceVersion1", InetAddress.getLocalHost.getHostName, Seq(StdOutAppender))
}
