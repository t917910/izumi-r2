package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.typer2.TypespaceEvalutor.TopLevelIdIndex
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.model.FieldConflict
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference}


class TsVerifier(types: Map[IzTypeId, ProcessedOp], tsc: TypespaceEvalutor) {


  def validateTypespace(allTypes: List[IzType]): Either[List[VerificationFail], Unit] = {
    for {
      _ <- validateTsConsistency(allTypes)
      _ <- validateAll(allTypes, postValidate)
    } yield {

    }
  }

  private def validateTsConsistency(allTypes: List[IzType]): Either[List[VerificationFail], Unit] = {
    val topLevelIdIndex: TopLevelIdIndex = tsc.topLevelIndex(allTypes)

    val duplicatingDefs = allTypes.groupBy(_.id).filter(_._2.size > 1)


    val missingRefs = topLevelIdIndex.present.mapValues(_.diff(topLevelIdIndex.allIds)).filter(_._2.nonEmpty)
    for {
      _ <- if (duplicatingDefs.nonEmpty) {
        Left(List(DuplicatedTypespaceMembers(duplicatingDefs.keySet)))
      } else {
        Right(())
      }
      _ <- if (missingRefs.nonEmpty) {
        Left(List(MissingTypespaceMembers(missingRefs)))
      } else {
        Right(())
      }
      _ <- if (topLevelIdIndex.missingGenerics.nonEmpty) {
        Left(List(UnresolvedGenericsInstancesLeft(topLevelIdIndex.missingGenerics)))
      } else {
        Right(())
      }
    } yield {

    }
  }


  def prevalidateTypes(allTypes: List[IzType]): Either[List[VerificationFail], Unit] = {
    validateAll(allTypes, preValidate)
  }

  private def validateAll(allTypes: List[IzType], validator: IzType => Either[List[VerificationFail], Unit]): Either[List[VerificationFail], Unit] = {
    val bad = allTypes
      .map(validator)
      .collect({ case Left(l) => l })
      .flatten

    for {
      _ <- if (bad.nonEmpty) {
        Left(bad)
      } else {
        Right(())
      }
    } yield {

    }
  }


  private def preValidate(tpe: IzType): Either[List[VerificationFail], Unit] = {
    // TODO: verify
    // don't forget: we don't have ALL the definitions here yet
    Quirks.discard(tpe)
    Right(())
  }

  private def postValidate(tpe: IzType): Either[List[VerificationFail], Unit] = {
    tpe match {
      case structure: IzStructure =>
        merge(List(
          verifyFieldContradictions(structure),
        ))

      case o =>
        // TODO: member conflicts
        Right(())
      //      case generic: IzType.Generic =>
      //      case builtinType: IzType.BuiltinType =>
      //      case IzType.IzAlias(id, source, meta) =>
      //      case IzType.Identifier(id, fields, meta) =>
      //      case IzType.Enum(id, members, meta) =>
      //      case foreign: IzType.Foreign =>
      //      case IzType.Adt(id, members, meta) =>
    }
  }

  private def verifyFieldContradictions(structure: IzStructure): Either[List[VerificationFail], Unit] = {
    val badFields = structure.fields.map {
      f =>
        f -> f.defined.map(_.as)
          .map(parent => FieldConflict(f.tpe, parent))
          .filterNot(c => isSubtype(c.tpe, c.expectedToBeParent))
    }
      .filterNot(_._2.isEmpty)
      .map {
        bad =>
          ContradictiveFieldDefinition(structure.id, bad._1, bad._2, structure.meta)
      }

    if (badFields.isEmpty) {
      Right(())
    } else {
      Left(badFields.toList)
    }
  }


  private def isSubtype(child: IzTypeReference, parent: IzTypeReference): Boolean = {
    (child == parent) || {
      (child, parent) match {
        case (IzTypeReference.Scalar(childId), IzTypeReference.Scalar(parentId)) =>
          (types(childId).member, types(parentId).member) match {
            case (c: IzStructure, p: IzStructure) =>
              c.allParents.contains(p.id)
            case _ =>
              false
          }

        case _ =>
          false // all generics are non-covariant
      }
    }
  }

  private def merge(checks: List[Either[List[VerificationFail], Unit]]): Either[List[VerificationFail], Unit] = {
    val issues = checks
      .collect({ case Left(l) => l })
      .flatten
    if (issues.nonEmpty) {
      Left(issues)
    } else {
      Right(())
    }
  }

}
