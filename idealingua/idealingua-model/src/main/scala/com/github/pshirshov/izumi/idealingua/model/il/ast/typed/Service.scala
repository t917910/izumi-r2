package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId

case class Service(id: ServiceId, methods: List[Service.DefMethod])

object Service {

  trait DefMethod

  object DefMethod {

    sealed trait Output

    object Output {

      case class Struct(struct: SimpleStructure) extends Output

      case class Algebraic(alternatives: List[AdtMember]) extends Output

      case class Singular(typeId: TypeId) extends Output
    }

    case class Signature(input: SimpleStructure, output: Output)

    case class RPCMethod(name: String, signature: Signature) extends DefMethod
  }

}