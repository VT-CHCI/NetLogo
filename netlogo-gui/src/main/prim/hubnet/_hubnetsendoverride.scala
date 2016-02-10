// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim.hubnet

import org.nlogo.agent.{ ArrayAgentSet, Agent, AgentSet }
import org.nlogo.api.{ CommandRunnable, Dump, Syntax }
import org.nlogo.nvm.{ Command, Context, EngineException }

class _hubnetsendoverride extends Command {

  override def syntax = Syntax.commandSyntax(
    Array(Syntax.StringType, Syntax.AgentsetType | Syntax.AgentType,
          Syntax.StringType,
          Syntax.ReporterBlockType),
    "OTPL", "?")

  override def perform(context: Context) {
    val client = argEvalString(context, 0)
    val target = args(1).report(context)
    val varName = argEvalString(context, 2)

    val set = target match {
      case a: Agent =>
        val aas = new ArrayAgentSet(a.getAgentClass(), 1, false, world)
        aas.add(a)
        aas
      case as: AgentSet => as
      case _ => throw new IllegalStateException("cant happen...")
    }

    if(!workspace.getHubNetManager.isOverridable(set.`type`, varName))
      throw new EngineException(context, this,
        "you cannot override " + varName)

    val freshContext = new Context(context, set)
    args(3).checkAgentSetClass(set, context)

    // ugh..set.iterator is not a real iterator.
    val overrides = new collection.mutable.HashMap[java.lang.Long,AnyRef]()
    val it = set.iterator
    while(it.hasNext) {
      val agent = it.next
      overrides(agent.id) = {
        val value = freshContext.evaluateReporter(agent, args(3))
        // gross to special case this, and not even clear where to put the special-case
        // code, but I guess it'll have to do until this all gets redone someday - ST 2/7/12
        if(varName.equalsIgnoreCase("LABEL") || varName.equalsIgnoreCase("PLABEL"))
          Dump.logoObject(value)
        else
          value
      }
    }

    workspace.waitFor(new CommandRunnable() {
      def run() { workspace.getHubNetManager.sendOverrideList(client, set.`type`(), varName, overrides.toMap) }
    })
    context.ip = next
  }

}
