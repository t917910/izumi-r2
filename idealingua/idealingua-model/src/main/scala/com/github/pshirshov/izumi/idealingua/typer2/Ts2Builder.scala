package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.typer2.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.IzTypeId.{IzDomainPath, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.ProcessedOp.Exported
import com.github.pshirshov.izumi.idealingua.typer2.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.TsMember.UserType
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Identified, UnresolvedName}

import scala.collection.mutable

sealed trait TsMember

object TsMember {

  //final case class Namespace(prefix: TypePrefix.UserT, types: List[TsMember]) extends TsMember

  final case class UserType(tpe: IzType) extends TsMember

}

sealed trait ProcessedOp {
  def member: TsMember
}

object ProcessedOp {

  final case class Exported(member: TsMember) extends ProcessedOp

  final case class Imported(member: TsMember) extends ProcessedOp

}

case class Typespace2(

                       warnings: List[T2Warn],
                       imports: Set[IzTypeId],
                       types: List[TsMember],
                     )

class Ts2Builder(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]) {
  private val failed = mutable.HashSet.empty[UnresolvedName]
  private val failures = mutable.ArrayBuffer.empty[InterpretationFail]
  private val existing = mutable.HashSet.empty[UnresolvedName]
  private val types = mutable.HashMap[IzTypeId, ProcessedOp]()
  private val thisPrefix = TypePrefix.UserTLT(IzPackage(index.defn.id.toPackage.map(IzDomainPath)))

  def defined: Set[UnresolvedName] = {
    existing.toSet
  }

  def add(ops: Identified): Unit = {
    ops.defns match {
      case defns if defns.isEmpty =>
        // type requires no ops => builtin
        //existing.add(ops.id).discard()
        register(ops, Right(index.builtins(ops.id)))

      case single :: Nil =>
        val dindex = if (single.source == index.defn.id) {
          index
        } else {
          importedIndexes(single.source)
        }
        val interpreter = new Interpreter(dindex, types.toMap)

        val product = single.defn match {
          case RawTopLevelDefn.TLDBaseType(v) =>
            v match {
              case i: RawTypeDef.Interface =>
                interpreter.makeInterface(i)

              case d: RawTypeDef.DTO =>
                interpreter.makeDto(d)

              case a: RawTypeDef.Alias =>
                interpreter.makeAlias(a)

              case e: RawTypeDef.Enumeration =>
                interpreter.makeEnum(e)

              case i: RawTypeDef.Identifier =>
                interpreter.makeIdentifier(i)
              case a: RawTypeDef.Adt =>
                interpreter.makeAdt(a)
            }

          case c: RawTopLevelDefn.TLDNewtype =>
            interpreter.cloneType(c.v)

          case RawTopLevelDefn.TLDForeignType(v) =>
            interpreter.makeForeign(v)

          case RawTopLevelDefn.TLDDeclared(v) =>
            Left(List(SingleDeclaredType(v)))
        }
        register(ops, product)

      case o =>
        println(s"Unhandled case: ${o.size} defs")
    }
  }

  def fail(ops: Identified, failures: List[InterpretationFail]): Unit = {
    if (ops.depends.exists(failed.contains)) {
      // dependency has failed already, fine to skip
    } else {
      failed.add(ops.id)
      this.failures ++= failures
    }
  }


  def finish(): Either[List[InterpretationFail], Typespace2] = {
    for {
      _ <- if (failures.nonEmpty) {
        Left(failures.toList)
      } else {
        Right(())
      }
      allTypes = Ts2Builder.this.types.values.collect({ case Exported(member) => member }).toList
      allTypes1 <- validateAll(allTypes)
    } yield {
      Typespace2(
        List.empty,
        Set.empty,
        allTypes1,
      )
    }
  }

  private def register(ops: Identified, product: Either[List[InterpretationFail], IzType]): Unit = {
    (for {
      p <- product
      v <- preValidate(p)
    } yield {
      v
    }) match {
      case Left(value) =>
        fail(ops, value)

      case Right(value) =>
        val member = TsMember.UserType(value)
        types.put(value.id, makeMember(member))
        existing.add(ops.id).discard()
    }
  }

  private def validateAll(allTypes: List[TsMember]): Either[List[InterpretationFail], List[TsMember]] = {
    val v = allTypes
      .map {
        case UserType(tpe) =>
          tpe
      }
      .map(postValidate)
    val bad = v.collect({ case Left(l) => l }).flatten
    if (bad.nonEmpty) {
      Left(bad)
    } else {
      Right(allTypes)
    }
  }


  private def postValidate(tpe: IzType): Either[List[InterpretationFail], IzType] = {
    tpe match {
      case structure: IzStructure =>
        structure.fields.foreach {
          f =>
            import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
            f.defined.map(_.as).foreach(parent => assert(isSubtype(f.tpe, parent), s"${f.tpe} ?? $parent\n\n${f.defined.niceList()}\n\n"))
        }
        Right(structure)
      case o =>
        Right(o)
//      case generic: IzType.Generic =>
//      case builtinType: IzType.BuiltinType =>
//      case IzType.IzAlias(id, source, meta) =>
//      case IzType.Identifier(id, fields, meta) =>
//      case IzType.Enum(id, members, meta) =>
//      case foreign: IzType.Foreign =>
//      case IzType.Adt(id, members, meta) =>
    }
  }

  private def preValidate(tpe: IzType): Either[List[InterpretationFail], IzType] = {
    // TODO: verify
    // don't forget: we don't have ALL the definitions here yet
    Right(tpe)
  }

  private def isSubtype(child: IzTypeReference, parent: IzTypeReference): Boolean = {
    (child == parent) || {
      (child, parent) match {
        case (IzTypeReference.Scalar(childId), IzTypeReference.Scalar(parentId)) =>
          // TODO: aliases!
          (types(childId).member, types(parentId).member) match {
            case (UserType(c: IzStructure), UserType(p: IzStructure)) =>
              c.allParents.contains(p.id)
            case _ =>
              false
          }

        case _ =>
          false // all generics are non-covariant
      }
    }
  }

  private def isOwn(id: IzTypeId): Boolean = {
    id match {
      case IzTypeId.BuiltinType(_) =>
        false
      case IzTypeId.UserType(prefix, _) =>
        prefix == thisPrefix
    }
  }

  private def makeMember(member: TsMember.UserType): ProcessedOp = {
    if (isOwn(member.tpe.id)) {
      ProcessedOp.Exported(member)
    } else {
      ProcessedOp.Imported(member)
    }
  }
}


