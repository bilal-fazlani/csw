import com.typesafe.sbt.site.SitePlugin.autoImport.siteDirectory
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import org.tmt.sbt.docs.DocKeys.{docsParentDir, docsRepo, gitCurrentRepo}
import sbt.Keys._
import sbt._

object Common {
  private val enableFatalWarnings: Boolean = sys.props.get("enableFatalWarnings").contains("true")
  private val enableCoverage: Boolean      = sys.props.get("enableCoverage").contains("true")
  val storyReport: Boolean                 = sys.props.get("generateStoryReport").contains("true")

  private val reporterOptions: Seq[Tests.Argument] =
    // "-oDF" - show full stack traces and test case durations
    // -C - to give fully qualified name of the custom reporter
    if (storyReport)
      Seq(
        Tests.Argument(TestFrameworks.ScalaTest, "-oDF", "-C", "tmt.test.reporter.TestReporter"),
        Tests.Argument(TestFrameworks.JUnit, "-v", "--run-listener=tmt.test.reporter.JUnit4TestReporter")
      )
    else Seq(Tests.Argument(TestFrameworks.JUnit, "-v"), Tests.Argument("-oDF"))

  val MaybeCoverage: Plugins = if (enableCoverage) Coverage else Plugins.empty
  val jsTestArg              = Test / testOptions := Seq(Tests.Argument("-oDF"))
  lazy val CommonSettings: Seq[Setting[_]] = Seq(
    
    docsRepo                                        := "https://github.com/tmtsoftware/tmtsoftware.github.io.git",
    docsParentDir                                   := "csw",
    gitCurrentRepo                                  := "https://github.com/tmtsoftware/csw",
    libraryDependencies += (Libs.`tmt-test-reporter` % Test),
    organization                                    := "com.github.tmtsoftware.csw",
    organizationName                                := "Thirty Meter Telescope International Observatory",
    scalaVersion                                    := Libs.ScalaVersion,
    homepage                                        := Some(url("https://github.com/tmtsoftware/csw")),
    resolvers += "Apache Pekko Staging".at("https://repository.apache.org/content/groups/staging"),
//    resolvers += "Apache Pekko Snapshots".at("https://repository.apache.org/content/groups/snapshots"),
    resolvers += "jitpack" at "https://jitpack.io",
    scmInfo := Some(
      ScmInfo(url("https://github.com/tmtsoftware/csw"), "git@github.com:tmtsoftware/csw.git")
    ),
    licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
//            "-rewrite",
//            "-source",
//            "3.4-migration"
    ),
    javacOptions ++= Seq(
      "-Xlint:unchecked"
    ),
    javaOptions += "-Xmx2G",
    Compile / doc / javacOptions ++= Seq("-Xdoclint:none"),
    doc / javacOptions ++= Seq("--ignore-source-errors"),
    Test / testOptions ++= reporterOptions,
    Test / packageBin / publishArtifact := true,
    // jitpack provides the env variable VERSION=<version being built> # A tag or commit. We have aliased VERSION to JITPACK_VERSION
    // we make use of it so that the version in class metadata (e.g. classOf[LocationService].getPackage.getSpecificationVersion)
    // and the maven repo match
    version := sys.env.getOrElse("JITPACK_VERSION", "0.1.0-SNAPSHOT"),
    fork    := true,
    Test / javaOptions ++= Seq("-Dpekko.actor.serialize-messages=on", s"-DRTM_PATH=${file("./target/RTM").getAbsolutePath}"),
    autoCompilerPlugins     := true,
    Global / cancelable     := true, // allow ongoing test(or any task) to cancel with ctrl + c and still remain inside sbt
    scalafmtOnCompile       := true,
    commands += Command.command("openSite") { state =>
      val uri = s"file://${Project.extract(state).get(siteDirectory)}/${docsParentDir.value}/${version.value}/index.html"
      state.log.info(s"Opening browser at $uri ...")
      java.awt.Desktop.getDesktop.browse(new java.net.URI(uri))
      state
    },
    Global / excludeLintKeys := Set(
      SettingKey[Boolean]("ide-skip-project"),
      aggregate // verify if this needs to be here or our configuration is wrong
    )
  )
}
