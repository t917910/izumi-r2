package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.{WithDISafeType, DIUniverseBase}

trait WithDITypedRef {
  this: DIUniverseBase
    with WithDISafeType =>

  case class TypedRef[+T](value: T, symbol: TypeFull)

  object TypedRef {
    def apply[T: Tag](value: T): TypedRef[T] =
      TypedRef(value, SafeType.get[T])
  }

}