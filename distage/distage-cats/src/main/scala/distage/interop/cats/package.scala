package distage.interop

import _root_.cats.effect.Sync
import _root_.cats.Applicative
import _root_.cats.instances.vector._
import _root_.cats.syntax.functor._
import _root_.cats.syntax.traverse._
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring.Instance
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan, SemiPlan}
import distage._

package object cats
  extends DistageInteropCats {

  implicit final class ProducerIOExts(private val producer: Producer) extends AnyVal {
    def produceIO[F[_]: Sync](plan: OrderedPlan): F[Locator] =
      Sync[F].delay(producer.produceUnsafe(plan))
  }

  implicit final class InjectorIOExts(private val injector: Injector) extends AnyVal {
    def produceIO[F[_]: Sync](plan: OrderedPlan): F[Locator] =
      Sync[F].delay(injector.produceUnsafe(plan))

    def produceIO[F[_]: Sync](definition: PlannerInput): F[Locator] =
      Sync[F].delay(injector.produce(definition))
  }

  implicit final class SemiPlanExts(private val plan: SemiPlan) extends AnyVal {
    def traverse[F[_]: Applicative](f: ExecutableOp => F[ExecutableOp]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => plan.copy(steps = s))

    def flatMapF[F[_]: Applicative](f: ExecutableOp => F[Seq[ExecutableOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => plan.copy(steps = s.flatten))

    def resolveImportF[T]: ResolveImportFSemiPlanPartiallyApplied[T] = new ResolveImportFSemiPlanPartiallyApplied(plan)

    def resolveImportF[F[_]: Applicative, T: Tag](f: F[T]): F[SemiPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[SemiPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

  implicit final class OrderedPlanExts(private val plan: OrderedPlan) extends AnyVal {
    def traverse[F[_]: Applicative](f: ExecutableOp => F[ExecutableOp]): F[SemiPlan] =
      plan.steps.traverse(f).map(SemiPlan(plan.definition, _, plan.roots))

    def flatMapF[F[_]: Applicative](f: ExecutableOp => F[Seq[ExecutableOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => SemiPlan(plan.definition, s.flatten, plan.roots))

    def resolveImportF[T]: ResolveImportFOrderedPlanPartiallyApplied[T] = new ResolveImportFOrderedPlanPartiallyApplied(plan)

    def resolveImportF[F[_]: Applicative, T: Tag](f: F[T]): F[OrderedPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[OrderedPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

  @inline
  private[cats] def resolveImportsImpl[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]], steps: Vector[ExecutableOp]): F[Vector[ExecutableOp]] =
    steps.traverse {
      case i: ImportDependency =>
        f.lift(i).map {
          _.map[ExecutableOp](instance => ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))
        } getOrElse Applicative[F].pure(i)
      case op =>
        Applicative[F].pure(op)
    }

  private[cats] final class ResolveImportFSemiPlanPartiallyApplied[T](private val plan: SemiPlan) extends AnyVal {
    def apply[F[_]: Applicative](f: F[T])(implicit ev: Tag[T]): F[SemiPlan] =
      plan.resolveImportsF[F] {
        case i if i.target == DIKey.get[T] => f.asInstanceOf[F[Any]]
      }
  }

  private[cats] final class ResolveImportFOrderedPlanPartiallyApplied[T](private val plan: OrderedPlan) extends AnyVal {
    def apply[F[_]: Applicative](f: F[T])(implicit ev: Tag[T]): F[OrderedPlan] =
      plan.resolveImportsF[F] {
        case i if i.target == DIKey.get[T] => f.asInstanceOf[F[Any]]
      }
  }
}
