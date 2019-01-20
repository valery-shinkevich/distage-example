package com.github.ratoshniuk.izumi.distage.sample.plugins

import java.util.concurrent._

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.functional.bio.BIORunner.{DefaultHandler, ZIORunnerBase}
import com.github.pshirshov.izumi.functional.bio.{BIO, BIOExit, BIORunner}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import scalaz.zio.IO


class BIOPlugin extends PluginDef {
  make[BIO[IO]].from(BIO.BIOZio)

  make[ExecutorService].named("zio-es").from {
    val cores = Runtime.getRuntime.availableProcessors.max(2)
    Executors.newFixedThreadPool(cores)
  }

  make[BIORunner[IO]].from {
    (logger: IzLogger, es : ExecutorService @Id("zio-es")) => {
      LoggingZioRunner.apply(es, logger)
    }
  }
}

object LoggingZioRunner {

  def apply(es: ExecutorService, log: IzLogger): BIORunner[IO] =
    new ZIORunnerBase(
      threadPool = es
      , handler = DefaultHandler.Custom {
        case BIOExit.Error(error: Throwable) =>
          val stackTrace = error.getStackTrace
          IO.sync(log.warn(s"Fiber terminated with unhandled Throwable $error $stackTrace"))
        case BIOExit.Error(error) =>
          IO.sync(log.warn(s"Fiber terminated with unhandled $error"))
        case BIOExit.Termination(_, (_: InterruptedException) :: _) =>
          IO.unit // don't log interrupts
        case BIOExit.Termination(exception, _) =>
          IO.sync(log.warn(s"Fiber terminated erroneously with unhandled defect $exception"))
      }) {
      // Keep default auto-fork threshold (every 1k flatMaps)
      override final val YieldMaxOpCount: Int = 1024
    }
}