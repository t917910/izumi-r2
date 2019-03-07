package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawStructure, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawGenericRef, RawRef}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, TypenameRef}
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.{Interpreter, InterpreterContext}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzDomainPath, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.RefToTLTLink
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model._

import scala.collection.{immutable, mutable}

trait RefRecorder {
  def require(ref: RefToTLTLink): Unit
}

class Ts2Builder(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]) extends WarnLogger with RefRecorder {
  private val failed = mutable.HashSet.empty[TypenameRef]
  private val failures = mutable.ArrayBuffer.empty[BuilderFail]
  private val warnings = mutable.ArrayBuffer.empty[T2Warn]
  private val existing = mutable.HashSet.empty[TypenameRef]
  private val types = mutable.HashMap[IzTypeId, ProcessedOp]()
  private val thisPrefix = TypePrefix.UserTLT(IzPackage(index.defn.id.toPackage.map(IzDomainPath)))


  override def log(w: T2Warn): Unit = {
    warnings += w
  }

  def defined: Set[TypenameRef] = {
    existing.toSet
  }

  def add(ops: Operation): Unit = {
    ops match {
      case Typer2.DefineBuiltin(id) =>
        register(ops, Right(List(index.builtins(id))))

      case single: Typer2.DefineType =>
        val dindex = if (single.main.source == index.defn.id) {
          index
        } else {
          importedIndexes(single.main.source)
        }

        val interpreter = makeInterpreter(dindex).interpreter
        //        val refs = requiredTemplates(single.main.defn.defn)
        //        if (refs.nonEmpty) {
        //          println(refs)
        //        }

        val product = interpreter.dispatch(single.main.defn)
        register(ops, product)

      case mult: Typer2.DefineWithDecls =>
        merge(mult) match {
          case Left(value) =>
            fail(ops, value)
          case Right(value) =>
            add(value)
        }
    }
  }


  ///
  def requiredTemplates(v: RawTypeDef): Seq[RawGenericRef] = {
    v match {
      case t: RawTypeDef.Interface =>
        trefs(t.struct)

      case t: RawTypeDef.DTO =>
        trefs(t.struct)

      case t: RawTypeDef.Alias =>
        collectGenericRefs(List(t.target))

      case t: RawTypeDef.Adt =>
        t.alternatives
          .flatMap {
            case a: Member.TypeRef =>
              collectGenericRefs(List(a.typeId))
            case a: Member.NestedDefn =>
              requiredTemplates(a.nested)
          }

      case _: RawTypeDef.NewType =>
        Seq.empty

      case t: RawTypeDef.Template =>
        requiredTemplates(t.decl)

      case _: RawTypeDef.Enumeration =>
        Seq.empty

      case _: RawTypeDef.Identifier =>
        Seq.empty

      case _: RawTypeDef.ForeignType =>
        Seq.empty

      case _: RawTypeDef.Instance =>
        Seq.empty
    }
  }

  private def trefs(struct: RawStructure): Seq[RawGenericRef] = {
    val allRefs = struct.interfaces ++ struct.concepts ++ struct.removedConcepts ++ struct.fields.map(_.typeId)
    collectGenericRefs(allRefs)
  }

  private def collectGenericRefs(allRefs: List[RawRef]): immutable.Seq[RawGenericRef] = {
    allRefs.collect({ case ref: RawGenericRef => ref })
  }

  ///


  def merge(mult: Typer2.DefineWithDecls): Either[List[BuilderFail], Typer2.DefineType] = {
    if (mult.decls.isEmpty) {
      Right(Typer2.DefineType(mult.id, mult.depends, mult.main))
    } else {
      Left(List(FeatureUnsupported(null, "TODO", null)))
    }
  }

  def fail(ops: Operation, failures: List[BuilderFail]): Unit = {
    if (ops.depends.exists(failed.contains)) {
      // dependency has failed already, fine to skip
    } else {
      failed.add(ops.id)
      this.failures ++= failures
    }
  }


  def finish(): Either[List[BuilderFail], Typespace2] = {
    for {
      _ <- if (failures.nonEmpty) {
        Left(failures.toList)
      } else {
        Right(())
      }
      _ <- instantiateMissingGenerics()
      verifier = makeVerifier()
      allTypes = freezeTypes()
      _ <- verifier.validateTypespace(allTypes)
    } yield {
      Typespace2(
        warnings.toList,
        Set.empty,
        this.types.values.toList,
      )
    }
  }

  private val missingRefs = mutable.ArrayBuffer[RefToTLTLink]()

  override def require(ref: RefToTLTLink): Unit = {
    //    println(s"Registered ref: ${ref}")
    missingRefs += ref
  }

  def instantiateMissingGenerics(): Either[List[BuilderFail], Unit] = {
    //val all = freezeTypes() //.map(_.id).toSet
    import results._

    val toCreate = missingRefs //.filterNot(r => all.get() .contains(r.target))
    val ret = toCreate
      .map {
        mg =>
          makeInterpreter(index).templates.makeInstance(RawDeclaredTypeName(mg.target.name.name), mg.ref, RawNodeMeta(None, Seq.empty, InputPosition.Undefined), mutable.HashMap.empty)
        //        instantiated.right.get.foreach {
        //          i =>
        //            println(s"Instantiated missing generic: ${i.id.name}")
        //        }

      }.biFlatAggregate

    registerTypes(ret)
  }

  private def freezeTypes(): List[IzType] = {
    Ts2Builder.this.types.values.map(_.member).toList
  }


  private def makeVerifier(): TsVerifier = {
    new TsVerifier(types.toMap, makeEvaluator())
  }

  private def makeEvaluator(): TypespaceEvalutor = {
    new TypespaceEvalutor(makeInterpreter(index).resolvers)
  }

  private def makeInterpreter(dindex: DomainIndex): InterpreterContext = {
    new InterpreterContext(dindex, this, this, Interpreter.Args(types.toMap, Map.empty))
  }

  private def register(ops: Operation, maybeTypes: Either[List[BuilderFail], List[IzType]]): Unit = {
    registerTypes(maybeTypes) match {
      case Left(value) =>
        fail(ops, value)

      case Right(_) =>
        existing.add(ops.id).discard()

    }
  }

  private def checkSanity(maybeTypes: List[IzType]): Either[List[BuilderFail], Unit] = {
    for {
      typesToRegister <- Right(maybeTypes)
      toRegister = typesToRegister.groupBy(_.id).mapValues(_.toSet)
      badRegistrations = toRegister.filter(_._2.size > 1)
      _ <- if (badRegistrations.nonEmpty) {
        Left(List(TypesAlreadyRegistered(badRegistrations.keySet.map(_.name))))
      } else {
        Right(())
      }
      conflicts = toRegister.mapValues(_.head).filter {
        case (id, tpe) =>
          val maybeOp = types.get(id)
          maybeOp.nonEmpty && !maybeOp.exists(_.member == tpe)
      }
      _ <- if (conflicts .nonEmpty) {
        Left(List(TypesAlreadyRegistered(conflicts.keySet.map(_.name))))
      } else {
        Right(())
      }
    } yield {
    }
  }
  private def registerTypes(maybeTypes: Either[List[BuilderFail], List[IzType]]): Either[List[BuilderFail], Unit] = {
    for {
      typesToRegister <- maybeTypes
      verifier = makeVerifier()
      _ <- verifier.prevalidateTypes(typesToRegister)
      _ <- checkSanity(typesToRegister)
    } yield {
      typesToRegister.foreach {
        product =>
          types.put(product.id, makeMember(product)) match {
            case Some(value) if value.member != product =>
              throw new IllegalStateException(s"Unexpected problem: sanity check failed for product $product != $value")
            case _ =>
          }
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

  private def makeMember(member: IzType): ProcessedOp = {
    if (isOwn(member.id)) {
      ProcessedOp.Exported(member)
    } else {
      ProcessedOp.Imported(member)
    }
  }
}



