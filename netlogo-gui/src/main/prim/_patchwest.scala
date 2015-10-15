// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim

import org.nlogo.agent.{ Patch, Turtle }
import org.nlogo.api.{ Syntax }
import org.nlogo.core.Nobody
import org.nlogo.nvm.{ Context, Reporter }

class _patchwest extends Reporter {
  override def syntax =
    Syntax.reporterSyntax(Syntax.PatchType, "-TP-")

  override def report(context: Context) = report_1(context)

  def report_1(context: Context): AnyRef = {
    val patch = context.agent match {
      case patch: Patch => patch.getPatchWest
      case turtle: Turtle => turtle.getPatchHere.getPatchWest
      case _ => world.fastGetPatchAt(-1, 0)
    }
    if (patch != null) patch else Nobody
  }
}
