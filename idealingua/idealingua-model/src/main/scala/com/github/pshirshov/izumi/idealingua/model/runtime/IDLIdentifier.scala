package com.github.pshirshov.izumi.idealingua.model.runtime

import scala.util.Try

trait IDLGenerated {

}

trait IDLIdentifier {
  this: IDLGenerated =>
}

object IDLIdentifier {
  // TODO: here we should escape colons
  def escape(s: String): String = s

  def unescape(s: String): String = s
}

trait IDLService {

}


