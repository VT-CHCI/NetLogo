// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.api

/**
 * Partial implementation of Reporter provides common implementations of some methods.  Implements
 * every method except <code>report(...)</code>.
 */
abstract class DefaultReporter extends Reporter with TokenHolder {

  /**
   * Indicates that this primitive can be used by any agent.
   *
   * @return <code>"OTPL"</code>
   */
  def getAgentClassString = getSyntax.agentClassString

  /**
   * Indicates that this reporter takes no arguments and returns a number.
   *
   * @return <code>Syntax.reporterSyntax(Syntax.NumberType)</code>
   */
  override def getSyntax =
    org.nlogo.core.Syntax.reporterSyntax(ret = Syntax.NumberType)

}
