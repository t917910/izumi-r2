package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.references.WithDIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

trait WithDIWiring {
  this: DIUniverseBase
    with WithDISafeType
    with WithDICallable
    with WithDIKey
    with WithDIAssociation
    with WithDIDependencyContext
    with WithDISymbolInfo
  =>

  sealed trait Wiring {
    def associations: Seq[Association]

    def requiredKeys: Set[DIKey] = associations.map(_.wireWith).toSet

    def replaceKeys(f: Association => DIKey.BasicKey): Wiring
  }

  object Wiring {

    sealed trait UnaryWiring extends Wiring {
      def instanceType: SafeType

      override def replaceKeys(f: Association => DIKey.BasicKey): UnaryWiring
    }


    object UnaryWiring {

      sealed trait ProductWiring extends UnaryWiring {
        override def replaceKeys(f: Association => DIKey.BasicKey): ProductWiring
      }

      sealed trait InstantiationWiring extends ProductWiring {
        def prefix: Option[DIKey]

        override final def requiredKeys: Set[DIKey] = super.requiredKeys ++ prefix.toSet

        override def replaceKeys(f: Association => DIKey.BasicKey): ProductWiring
      }

      case class Constructor(instanceType: SafeType, associations: Seq[Association.Parameter], prefix: Option[DIKey]) extends InstantiationWiring {
        override final def replaceKeys(f: Association => DIKey.BasicKey): Constructor =
          this.copy(associations = this.associations.map(a => a.withWireWith(f(a))))
      }

      case class AbstractSymbol(instanceType: SafeType, associations: Seq[Association.AbstractMethod], prefix: Option[DIKey]) extends InstantiationWiring {
        override final def replaceKeys(f: Association => DIKey.BasicKey): AbstractSymbol =
          this.copy(associations = this.associations.map(a => a.withWireWith(f(a))))
      }

      case class Function(provider: Provider, associations: Seq[Association.Parameter]) extends UnaryWiring {
        override def instanceType: SafeType = provider.ret

        override final def replaceKeys(f: Association => DIKey.BasicKey): Function =
          this.copy(associations = this.associations.map(a => a.withWireWith(f(a))))
      }

      case class Instance(instanceType: SafeType, instance: Any) extends UnaryWiring {
        override def associations: Seq[Association] = Seq.empty

        override def replaceKeys(f: Association => DIKey.BasicKey): this.type = { f.discard(); this }
      }

      case class Reference(instanceType: SafeType, key: DIKey, weak: Boolean) extends UnaryWiring {
        override def associations: Seq[Association] = Seq.empty

        override def requiredKeys: Set[DIKey] = super.requiredKeys ++ Set(key)

        override def replaceKeys(f: Association => DIKey.BasicKey): this.type = { f.discard(); this }
      }
    }

    case class FactoryMethod(factoryType: SafeType, factoryMethods: Seq[FactoryMethod.WithContext], fieldDependencies: Seq[Association.AbstractMethod]) extends Wiring {
      /**
        * this method returns product dependencies which aren't present in any signature of factory methods.
        * Though it's a kind of a heuristic that can be spoiled at the time of plan initialization
        *
        * Complete check can only be performed at runtime.
        */
      override def associations: Seq[Association] = {
        val factoryMethodsArgs = factoryMethods.flatMap(_.methodArguments).toSet

        val factorySuppliedProductDeps = factoryMethods.flatMap(_.wireWith.associations).filterNot(v => factoryMethodsArgs.contains(v.wireWith))

        factorySuppliedProductDeps ++ fieldDependencies
      }

      override final def replaceKeys(f: Association => DIKey.BasicKey): FactoryMethod =
        this.copy(
          fieldDependencies = this.fieldDependencies.map(a => a.withWireWith(f(a)))
          , factoryMethods = this.factoryMethods.map(m => m.copy(wireWith = m.wireWith.replaceKeys(f)))
        )
    }

    object FactoryMethod {
      case class WithContext(factoryMethod: SymbolInfo.Runtime, wireWith: UnaryWiring.ProductWiring, methodArguments: Seq[DIKey]) {
        def associationsFromContext: Seq[Association] = wireWith.associations.filterNot(methodArguments contains _.wireWith)
      }
    }

    case class FactoryFunction(
                                provider: Provider
                                , factoryIndex: Map[Int, FactoryFunction.WithContext]
                                , providerArguments: Seq[Association.Parameter]
                              ) extends Wiring {
      val factoryMethods: Seq[FactoryFunction.WithContext] = factoryIndex.values.toSeq

      override def associations: Seq[Association] = {
        val factoryMethodsArgs = factoryMethods.flatMap(_.methodArguments).toSet

        val factorySuppliedProductDeps = factoryMethods.flatMap(_.wireWith.associations).filterNot(v => factoryMethodsArgs.contains(v.wireWith))

        factorySuppliedProductDeps ++ providerArguments
      }

      override final def replaceKeys(f: Association => DIKey.BasicKey): FactoryFunction =
        this.copy(
          providerArguments = this.providerArguments.map(a => a.withWireWith(f(a)))
          , factoryIndex = this.factoryIndex.mapValues(m => m.copy(wireWith = m.wireWith.replaceKeys(f))).toMap // 2.13 compat
        )
    }

    object FactoryFunction {
      // TODO: wireWith should be Wiring.UnaryWiring.Function - generate providers for concrete classes in distage-static, instead of using reflection
      case class WithContext(factoryMethod: SymbolInfo, wireWith: Wiring.UnaryWiring, methodArguments: Seq[DIKey]) {
        def associationsFromContext: Seq[Association] = wireWith.associations.filterNot(methodArguments contains _.wireWith)
      }
    }

  }

}
