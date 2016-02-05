// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim.etc

import org.nlogo.api, api.{ Syntax, LogoListBuilder }
import org.nlogo.nvm.{ Context, Pure, Reporter }

class _approximatehsb extends Reporter with Pure {
  override def syntax =
    Syntax.reporterSyntax(Array(Syntax.NumberType, Syntax.NumberType, Syntax.NumberType),
                          Syntax.NumberType)
  override def report(context: Context): java.lang.Double =
    report_1(context,
        argEvalDoubleValue(context, 0),
        argEvalDoubleValue(context, 1),
        argEvalDoubleValue(context, 2));

  def report_1(context: Context, h: Double, s: Double, b: Double): java.lang.Double =
    api.Color.getClosestColorNumberByHSB(h.toFloat, s.toFloat, b.toFloat, 360.0f, 100.0f, 100.0f);
}

class _approximatehsbold extends Reporter with Pure {
  override def syntax =
    Syntax.reporterSyntax(Array(Syntax.NumberType, Syntax.NumberType, Syntax.NumberType),
                          Syntax.NumberType)
  override def report(context: Context): java.lang.Double =
    report_1(context,
        argEvalDoubleValue(context, 0),
        argEvalDoubleValue(context, 1),
        argEvalDoubleValue(context, 2));

  def report_1(context: Context, h: Double, s: Double, b: Double): java.lang.Double =
    api.Color.getClosestColorNumberByHSB(h.toFloat, s.toFloat, b.toFloat, 255.0f, 255.0f, 255.0f);
}
