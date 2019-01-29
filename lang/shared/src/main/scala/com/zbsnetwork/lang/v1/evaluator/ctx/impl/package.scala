package com.zbsnetwork.lang.v1.evaluator.ctx

import com.zbsnetwork.lang.v1.compiler.Terms.CaseObj
import com.zbsnetwork.lang.v1.compiler.Types.CASETYPEREF

package object impl {
  def notImplemented(funcName: String, args: List[Any]): Nothing = throw new Exception(
    s"Can't apply (${args.map(_.getClass.getSimpleName).mkString(", ")}) to '$funcName'"
  )

  lazy val UNIT: CASETYPEREF = CASETYPEREF("Unit", List.empty)
  lazy val unit: CaseObj   = CaseObj(UNIT, Map.empty)
}
