package com.github.pshirshov.izumi.idealingua.typer2.conversions

import com.github.pshirshov.izumi.fundamentals.collections.ImmutableMultiMap
import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.typer2.WarnLogger
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.BasicField
import com.github.pshirshov.izumi.idealingua.typer2.model.{Conversion, IzTypeId, T2Warn, Typespace2}

import scala.collection.immutable.ListSet

class ConversionCalculator(warnLogger: WarnLogger, ts2: Typespace2) {

  import Conversion._
  import Conversion.model._

  Quirks.discard(ts2)

  def conversion(from: IzStructure, to: IzStructure): List[Conversion] = {
    val fromFields = from.fields.map(_.basic).to[ListSet]
    val toFields = to.fields.map(_.basic).to[ListSet]
    val copyable = fromFields.intersect(toFields)
    val toCopy = copyable.map(f => ConstructionOp.Transfer(from.id, f, f)).toSeq
    val missing = toFields.diff(fromFields)

    val directMissingCopy = missing.map(m => ConstructionOp.Emerge(m.ref, m)).toSeq

    val conversion1 = List(make(from.id, to.id, toCopy ++ directMissingCopy))

    val conversion2 = if (missing.nonEmpty) {
      val solutions = findSolutions(to, missing)
      solutions.map(s => make(from.id, to.id, toCopy ++ s))
    } else {
      List.empty
    }

    val result = conversion1
    filterValid(from, to, result)
  }

  private def findSolutions(to: IzStructure, missing: ListSet[BasicField]): Seq[Seq[ConstructionOp]] = {
    val findex = to.fields.map(f => f.basic -> f).toMap
    val sources = missing.map {
      f =>
        val full = findex(f)
        // we may check if f.ref is a parent of d, but it's excessive
        val defined = full.defined.filter(d => d.as == f.ref && d.in != to.id).map(_.in)
        f -> defined
    }

    val sourcesMap = sources.toMap
    val definingTypes = sources.flatMap {
      case (f, defs) =>
        defs.map {
          d =>
            d -> f
        }
    }.toMultimap

    // now we need to find non-intersecting types which defines all the missing fields
    // though it's an NP problem

    val covers = findBestCoveringParents(definingTypes, Set.empty)

    covers.map {
      cover =>
        assert(cover.toSet.size == cover.size)
        val covered = cover.flatMap(c => definingTypes(c))
        // it's guaranteed that field sets provided by "cover" are non-contradictive
        assert(covered.toSet.size == covered.size)

        val toCopy = cover.flatMap {
          c =>
            val provided = definingTypes(c)
            provided.map {
              f =>
                ConstructionOp.Transfer(c, f, f)
            }

        }

        val stillMissing = missing.diff(covered.toSet)
        val directMissingCopy = stillMissing.map(m => ConstructionOp.Emerge(m.ref, m)).toSeq

        toCopy ++ directMissingCopy
    }
  }

  // a heuristic
  private def findBestCoveringParents(definingTypes: ImmutableMultiMap[IzTypeId, BasicField], covered: Set[BasicField]): Seq[Seq[IzTypeId]] = {
    val sortedCandidates = definingTypes.mapValues(_.size).toSeq.sortBy(_._2)(implicitly[Ordering[Int]].reverse).map(_._1)

    sortedCandidates
      .view
      .flatMap {
        b =>
          val withoutBest = definingTypes - b
          if (withoutBest.isEmpty) {
            Seq(Seq.empty)
          } else {
            val moreCover = definingTypes(b)
            if (moreCover.intersect(covered).isEmpty) {
              findBestCoveringParents(withoutBest, moreCover).map {
                b1 =>
                  Seq(b) ++ b1
              }
            } else {
              Seq(Seq.empty)
            }
          }
      }
      .take(5)
  }


  private def make(from: IzTypeId, to: IzTypeId, ops: Seq[ConstructionOp]): Conversion = {
    val transfers = ops.collect({ case f: ConstructionOp.Transfer => f })
    val emerges = ops.collect({ case f: ConstructionOp.Emerge => f })

    if (emerges.isEmpty) {
      Copy(from, to, transfers)
    } else {
      Expand(from, to, ops)
    }
  }

  private def filterValid(from: IzStructure, to: IzStructure, result: List[Conversion]): List[Conversion] = {
    val toFields = to.fields.map(_.basic).to[ListSet]
    val (good, bad) = result.partition {
      c =>
        val targetFields = c match {
          case Copy(_, _, ops) =>
            ops.map(_.target)
          case Expand(_, _, ops) =>
            ops.map(_.target)
        }
        targetFields.size == to.fields.size && targetFields.toSet == toFields.toSeq.toSet // ListSet considers order in equals :/
    }

    bad.foreach {
      b =>
        warnLogger.log(T2Warn.FailedConversion(b))
    }

    good
  }

}
