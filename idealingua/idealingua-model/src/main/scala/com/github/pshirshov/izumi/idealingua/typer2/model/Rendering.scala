package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArg

object Rendering {

  implicit object IzTypeIdRenderer extends Renderable[IzTypeId] {
    override def render(value: IzTypeId): String = {
      value match {
        case IzTypeId.BuiltinType(name) =>
          s"::${name.name}"
        case IzTypeId.UserType(prefix, name) =>
          s"${Renderable[TypePrefix].render(prefix)}/${name.name}"

      }
    }
  }

  implicit object IzTypePrefixRenderer extends Renderable[TypePrefix] {
    override def render(value: TypePrefix): String = {
      value match {
        case TypePrefix.UserTLT(location) =>
          location.path.map(_.name).mkString(".")
        case TypePrefix.UserT(location, subpath) =>
          location.path.map(_.name).mkString(".") + "/" + subpath.map(_.name).mkString(".")
      }
    }
  }


  implicit object IzTypeArgRenderer extends Renderable[IzTypeArg] {
    override def render(value: IzTypeArg): String = {
      s"#${Renderable[IzTypeReference].render(value.value.ref)}"
    }
  }

  implicit object IzTypeReferenceRenderer extends Renderable[IzTypeReference] {
    override def render(value: IzTypeReference): String = {
      value match {
        case IzTypeReference.Scalar(id) =>
          s"&${Renderable[IzTypeId].render(id)}"
        case IzTypeReference.Generic(id, args, adhocName) =>
          val n = adhocName.getOrElse(s"${Renderable[IzTypeId].render(id)}[${args.map(arg => Renderable[IzTypeArg].render(arg)).mkString(",")}]")
          s"&$n"
      }
    }
  }

}