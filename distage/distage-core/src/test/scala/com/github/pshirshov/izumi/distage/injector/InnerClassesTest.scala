package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.InnerClassCases._
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import org.scalatest.WordSpec

import scala.util.Try

class InnerClassesTest extends WordSpec with MkInjector {
  "can instantiate inner classes from stable objects where the classes are inherited from a trait" in {
    import InnerClassStablePathsCase._
    import StableObjectInheritingTrait._

    val definition  = PlannerInput(new ModuleDef {
      make[TestDependency]
      make[StableObjectInheritingTrait1.TestDependency]
    })

    val context = mkInjector().produce(definition)

    assert(context.get[TestDependency] == TestDependency())
    assert(context.get[StableObjectInheritingTrait1.TestDependency] == StableObjectInheritingTrait1.TestDependency())
    assert(context.get[StableObjectInheritingTrait1.TestDependency] != context.get[TestDependency])
  }

  "can instantiate inner classes from stable objects where the classes are inherited from a trait and depend on types defined inside trait" in {
    import InnerClassStablePathsCase._
    import StableObjectInheritingTrait._

    val definition  = PlannerInput(new ModuleDef {
      make[TestDependency]
      make[TestClass]
    })

    val context = mkInjector().produce(definition)

    assert(context.get[TestClass] == TestClass(TestDependency()))
  }

  "can support path-dependant injections with injector lookup" in {
    import InnerClassUnstablePathsCase._

    val testProviderModule = new TestModule

    val definition  = PlannerInput(new ModuleDef {
      make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
      make[testProviderModule.TestDependency]
      make[testProviderModule.TestClass]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[testProviderModule.TestClass].a.isInstanceOf[testProviderModule.TestDependency])
  }

  "can handle function local path-dependent injections (macros can't)" in {
    def someFunction(): Unit = {
      import InnerClassUnstablePathsCase._

      val testProviderModule = new TestModule

      val definition  = PlannerInput(new ModuleDef {
        make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
        make[testProviderModule.TestClass]
        make[testProviderModule.TestDependency]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[testProviderModule.TestClass].a.isInstanceOf[testProviderModule.TestDependency])
      ()
    }

    someFunction()
  }


  "support path-dependant by-name injections" in {
    import InnerClassByNameCase._

    val testProviderModule = new TestModule

    val definition  = PlannerInput(new ModuleDef {
      make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
      make[testProviderModule.TestDependency]
      make[testProviderModule.TestClass]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[testProviderModule.TestClass].aValue.isInstanceOf[testProviderModule.TestDependency])
  }

  "can handle inner path-dependent injections (macros can)" in {
    new InnerPathDepTest().testCase
  }

  "can handle class local path-dependent injections (macros can)" in {
    val definition  = PlannerInput(new ModuleDef {
      make[TopLevelPathDepTest.type].from[TopLevelPathDepTest.type](TopLevelPathDepTest: TopLevelPathDepTest.type)
      make[TopLevelPathDepTest.TestClass]
      make[TopLevelPathDepTest.TestDependency]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)

    assert(context.get[TopLevelPathDepTest.TestClass].a != null)
  }

  "progression test: can't handle factories inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait (macros can't)" in {
    val fail = Try {
      import InnerClassStablePathsCase._
      import StableObjectInheritingTrait._

      val definition  = PlannerInput(new ModuleDef {
        make[TestFactory]
      })

      val context = mkInjector().produce(definition)

      assert(context.get[TestFactory].mk(TestDependency()) == TestClass(TestDependency()))
    }
    assert(fail.isFailure)
  }

  "progression test: can't handle circular dependencies inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait (macros can't?)" in {
    val fail = Try {
      import InnerClassStablePathsCase._
      import StableObjectInheritingTrait._

      val definition  = PlannerInput(new ModuleDef {
        make[Circular1]
        make[Circular2]
      })

      val context = mkInjector().produce(definition)

      assert(context.get[TestFactory].mk(TestDependency()) == TestClass(TestDependency()))
    }
    assert(fail.isFailure)
  }

  "progression test: runtime cogen can't handle path-dependant factories (macros can't?)" in {
    val fail = Try {
      import InnerClassUnstablePathsCase._
      val testProviderModule = new TestModule

      val definition  = PlannerInput(new ModuleDef {
        make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
        make[testProviderModule.TestFactory]
      })

      val context = mkInjector().produce(definition)

      assert(context.get[testProviderModule.TestFactory].mk(testProviderModule.TestDependency()) == testProviderModule.TestClass(testProviderModule.TestDependency()))
    }
    assert(fail.isFailure)
  }

  "progression test: runtime cogen can't circular path-dependant dependencies (macros can't?)" in {
    val fail = Try {
      import InnerClassUnstablePathsCase._
      val testProviderModule = new TestModule

      val definition  = PlannerInput(new ModuleDef {
        make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
        make[testProviderModule.Circular1]
        make[testProviderModule.Circular2]
      })

      val context = mkInjector().produce(definition)

      assert(context.get[testProviderModule.TestFactory].mk(testProviderModule.TestDependency()) == testProviderModule.TestClass(testProviderModule.TestDependency()))
    }
    assert(fail.isFailure)
  }


  class InnerPathDepTest extends InnerClassUnstablePathsCase.TestModule {
    private val definition  = PlannerInput(new ModuleDef {
      make[InnerPathDepTest.this.type].from[InnerPathDepTest.this.type](InnerPathDepTest.this: InnerPathDepTest.this.type)
      make[TestClass]
      make[TestDependency]
    })

    def testCase = {
      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestClass].a != null)
    }
  }

  object TopLevelPathDepTest extends InnerClassUnstablePathsCase.TestModule

}
