package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.Injector
import com.github.pshirshov.izumi.distage.model.definition.{ContextDefinition, TrivialDIDef}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.logstage.api.{IzLogger, LoggingMacroTest}
import com.github.pshirshov.izumi.logstage.model.Log.CustomContext
import com.github.pshirshov.izumi.logstage.model.logger.LogRouter
import org.scalatest.WordSpec

class ExampleService(log: IzLogger) {
  def compute: Int = {
    log.debug("Service")
    265
  }
}

class ExampleApp(log: IzLogger, service: ExampleService) {
  def test: Int = {
    log.debug("App")
    service.compute
  }
}

class PlanningObserverLoggingImpl extends PlanningObserver {

  override def onFinalPlan(finalPlan: FinalPlan): Unit = {
    System.err.println("=" * 60 + " Final Plan " + "=" * 60)
    System.err.println(s"$finalPlan")
    System.err.println("\n")
  }


  override def onResolvingFinished(finalPlan: FinalPlan): Unit = {
    System.err.println("=" * 60 + " Resolved Plan " + "=" * 60)
    System.err.println(s"$finalPlan")
    System.err.println("\n")
  }

  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    System.err.println("-" * 60 + " Next Plan " + "-" * 60)
    System.err.println(next)
  }

  override def onReferencesResolved(plan: DodgyPlan): Unit = {

  }
}



class LoggerInjectionTest extends WordSpec {
  "Logging module for distage" should {
    "inject loggers" in {
      def mkInjector(): Injector = {
        val customizations = TrivialDIDef.binding[PlanningObserver, PlanningObserverLoggingImpl]
        Injector.emerge(Injector.bootstrapCustomized(customizations))
      }

      val router: LogRouter = LoggingMacroTest.mkRouter(LoggingMacroTest.consoleSinkText)

      val definition: ContextDefinition = TrivialDIDef
        .instance(router)
        .instance(CustomContext.empty)
        .binding[IzLogger]
        .binding[ExampleService]
        .binding[ExampleApp]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      assert(context.get[ExampleApp].test == 265)
    }
  }
}