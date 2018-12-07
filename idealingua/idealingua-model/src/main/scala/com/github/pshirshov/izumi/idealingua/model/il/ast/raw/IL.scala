package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef.{ForeignType, NewType}

object IL {

  final case class ImportedId(name: String, as: Option[String]) {
    def importedName: String = as.getOrElse(name)
  }

  final case class Import(id: DomainId, identifiers: Set[ImportedId])

  sealed trait Val

  sealed trait TypeVal extends Val

  final case class ILImport(domain: DomainId, id: ImportedId) extends Val

  final case class ILInclude(i: String) extends Val

  final case class ILDef(v: IdentifiedRawTypeDef) extends TypeVal

  final case class ILService(v: Service) extends Val

  final case class ILBuzzer(v: Buzzer) extends Val

  // not supported by cogen yet
  final case class ILStreams(v: Streams) extends Val

  final case class ILConst(v: Constants) extends Val

  final case class ILNewtype(v: NewType) extends TypeVal

  final case class ILForeignType(v: ForeignType) extends TypeVal
}
