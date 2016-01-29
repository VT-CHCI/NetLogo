import java.io.File
import sbt._
import Keys._

import Def.Initialize
import sbt.complete.{ Parser, DefaultParsers }, Parser.success, DefaultParsers._

object Extensions {

  private val extensionDeps = TaskKey[(File, File)]("extension dependencies")
  val extensions = TaskKey[Seq[File]]("extensions", "builds extensions")
  val extension = InputKey[Seq[File]]("extension", "build a single extension")
  val submoduleUpdate = TaskKey[Unit]("checks out all submodules")

  val isDirectory = new java.io.FileFilter {
    override def accept(f: File) = f.isDirectory
  }

  def extensionDirs(base: File) = IO.listFiles(isDirectory)(base / "extensions").toSeq

  val extensionParser: Initialize[Parser[File]] = {
    import Parser._
    Def.setting {
      (Space ~> extensionDirs(baseDirectory.value).map(d => (d.getName ^^^ d)).reduce(_ | _))
    }
  }

  lazy val extensionsTask = Seq(
    extensionDeps := {
      val packagedNetLogoJar     = (packageBin in Compile).value
      val packagedNetLogoTestJar = (packageBin in Test).value
      (packagedNetLogoJar, packagedNetLogoTestJar)
    },
    extension  := {
      val extensionDir = extensionParser.parsed
      val (packagedNetLogoJar, packagedNetLogoTestJar) = extensionDeps.value
      val s = streams.value
      val scala = scalaInstance.value

      Seq(buildExtension(extensionDir, scala.libraryJar, packagedNetLogoJar, s.log, state.value))
    },
    extensions <<= Def.taskDyn {
      val (packagedNetLogoJar, packagedNetLogoTestJar) = extensionDeps.value
      new Scoped.RichTaskSeq(extensionDirs.value.map(buildExtensionTask(packagedNetLogoJar))).join dependsOn(submoduleUpdate)
    },
    submoduleUpdate := {
      ("git -C " + baseDirectory.value + " submodule --quiet update --init") ! streams.value.log
    }
  )

  lazy val extensionDirs: Def.Initialize[Task[Seq[File]]] =
    Def.task {
      val isDirectory = new java.io.FileFilter {
        override def accept(f: File) = f.isDirectory
      }
      IO.listFiles(isDirectory)(baseDirectory.value / "extensions").toSeq
    }

  lazy val fileDependencies: Def.Initialize[Set[File]] =
    Def.setting {
      Set(
        baseDirectory.value / "NetLogo.jar",
        baseDirectory.value / "NetLogoLite.jar"
      )
    }

  class NestedConfiguration(val config: xsbti.AppConfiguration, baseDir: File, args: Array[String]) extends xsbti.AppConfiguration {
    override val arguments = args
    override val provider = config.provider
    override val baseDirectory = baseDir
  }

  val runner = new xMain()

  def config(state: State, dir: File, command: String) =
    new NestedConfiguration(state.configuration, dir, Array(command))

  private def buildExtensionTask(packagedNetLogoJar: File)(dir: File): Def.Initialize[Task[File]] =
    Def.task {
      FileFunction.cached(streams.value.cacheDirectory / "extensions" / dir.getName,
        inStyle = FilesInfo.hash, outStyle = FilesInfo.hash)(
        { files =>
          Set(buildExtension(dir, scalaInstance.value.libraryJar, packagedNetLogoJar, streams.value.log, state.value))
        })(fileDependencies.value).head
    }

  private def buildExtension(dir: File, scalaLibrary: File, netLogoJar: File, log: Logger, state: State): File = {
    log.info("building extension: " + dir.getName)
    System.setProperty("netlogo.jar.url", netLogoJar.toURI.toString)
    val buildConfig  = config(state, dir, "package")
    val jar = dir / (dir.getName + ".jar")
    runner.run(buildConfig) match {
      case e: xsbti.Exit   => assert(e.code == 0, "extension build " + dir.getName + " failed, exitCode = " + e.code)
      case r: xsbti.Reboot => assert(true == false, "expected extension " + dir.getName + " to build, rebooted instead")
    }
    jar
  }

}
