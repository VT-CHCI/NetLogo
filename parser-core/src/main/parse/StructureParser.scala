// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.parse

// For each source file, input is Tokens, output is a StructureResults -- which is mostly just a
// Program and some Procedures.

// Each source file is handled in three stages, each represented as a separate trait.
// 1. StructureCombinators parses input tokens according to a context-free grammar,
//    returning a Seq[Declaration].
// 2. StructureChecker checks the Seq[Declaration] for duplicates.
// 3. StructureConverter converts the Seq[Declaration] to a StructureResults.
// By splitting it this way, we get separation of concerns between the (clean) mechanics of parsing
// and the (messy) mechanics of building the data structures that the rest of the compiler and the
// engine will use.

// Note that when parsing starts, we don't necessarily have all our source files yet.  Some of them
// will be discovered as we parse, through __include declarations.  (Included files might themselves
// include further files.)

import
  org.nlogo.core,
    core.{CompilationOperand, ErrorSource, ExtensionManager, BreedIdentifierHandler, CompilationEnvironment,
    FrontEndInterface, ProcedureSyntax, Program, Token, TokenMapperInterface, StructureDeclarations, StructureResults},
      FrontEndInterface.ProceduresMap,
    core.Fail._

object StructureParser {
  val IncludeFilesEndInNLS = "Included files must end with .nls"

  /// main entry point.  handles gritty extensions stuff and includes stuff.
  def parseSources(tokenizer: core.TokenizerInterface, compilationData: CompilationOperand): StructureResults = {
      import compilationData.{ compilationEnvironment, displayName, oldProcedures, subprogram, sources, containingProgram => program }
      parsingWithExtensions(compilationData) {
        val structureParser = new StructureParser(displayName, subprogram)
        val firstResults =
          sources.foldLeft(StructureResults(program, oldProcedures)) {
            case (results, (filename, source)) =>
              parseOne(tokenizer, structureParser, source, filename, results)
          }
        if (subprogram)
          firstResults
        else {
          Iterator.iterate(firstResults) { results =>
            val suppliedPath = results.includes.head.value.asInstanceOf[String]
            cAssert(suppliedPath.endsWith(".nls"), IncludeFilesEndInNLS, results.includes.head)
            IncludeFile(compilationEnvironment, suppliedPath) match {
              case Some((path, fileContents)) =>
                parseOne(tokenizer, structureParser, fileContents, path, results.copy(includes = results.includes.tail))
              case None =>
                exception(s"Could not find $suppliedPath", results.includes.head)
            }
          }.dropWhile(_.includes.nonEmpty).next
        }
      }
  }

  private def parsingWithExtensions(compilationData: CompilationOperand)(results: => StructureResults): StructureResults = {
    if (compilationData.subprogram)
      results
    else {
      compilationData.extensionManager.startFullCompilation()

      val r = results

      for (token <- r.extensions)
        compilationData.extensionManager.importExtension(
          token.text.toLowerCase, new ErrorSource(token))

      compilationData.extensionManager.finishFullCompilation()

      r
    }
  }

  private def parseOne(tokenizer: core.TokenizerInterface, structureParser: StructureParser, source: String, filename: String, oldResults: StructureResults): StructureResults = {
      val tokens =
        tokenizer.tokenizeString(source, filename)
          .filter(_.tpe != core.TokenType.Comment)
          .map(Namer0)
      structureParser.parse(tokens, oldResults)
    }

  def usedNames(program: Program, procedures: ProceduresMap, declarations: Seq[StructureDeclarations.Declaration]): Map[String, String] = {
    val alwaysUsedNames =
      program.dialect.tokenMapper.allCommandNames.map(_ -> "primitive command") ++
        program.dialect.tokenMapper.allReporterNames.map(_ -> "primitive reporter")

    program.usedNames ++
      breedPrimitives(declarations) ++
      procedures.keys.map(_ -> "procedure") ++
      alwaysUsedNames
  }

  private def breedPrimitives(declarations: Seq[StructureDeclarations.Declaration]): Map[String, String] = {
    import BreedIdentifierHandler._
    import org.nlogo.core.StructureDeclarations.Breed

    declarations.flatMap {
      case breed: Breed =>
        val pairs = Seq(breedCommands _ -> "breed command", breedReporters _ -> "breed reporter", breedHomonymProcedures _ -> "breed")
        pairs flatMap { case (f, label) => f(breed).map(_ -> label) }
      case _ => Seq()
    }.toMap
  }

  def findProcedurePositions(tokens: Seq[Token]): Map[String, ProcedureSyntax] = {
    import scala.annotation.tailrec
    def procedureSyntax(tokens: Seq[Token]): Option[(String, ProcedureSyntax)] = {
      val ident = tokens(1)
      if (ident.tpe == core.TokenType.Ident)
        Some((ident.text, ProcedureSyntax(tokens.head, ident, tokens.last)))
      else
        None
    }

    @tailrec
    def splitOnProcedureStarts(tokens:       Seq[Token],
                              existingProcs: Seq[Seq[Token]]): Seq[Seq[Token]] = {
      if (tokens.isEmpty || tokens.head.tpe == core.TokenType.Eof)
        existingProcs
      else {
        val headValue = tokens.head.value
        if (headValue == "TO" || headValue == "TO-REPORT") {
          val sizeToEnd = tokens.takeWhile(t => t.value != "END").size
          val (procedureTokens, remainingTokens) = tokens.splitAt(sizeToEnd + 1)
          splitOnProcedureStarts(remainingTokens, existingProcs :+ procedureTokens)
        } else {
          splitOnProcedureStarts(
            tokens.dropWhile(t => ! (t.value == "TO" || t.value == "TO-REPORT")),
            existingProcs)
        }
      }
    }

    splitOnProcedureStarts(tokens, Seq()).flatMap(procedureSyntax).toMap
  }

  def findIncludes(tokens: Iterator[Token]): Seq[String] = {

    val includesPositionedTokens =
      tokens.dropWhile(! _.text.equalsIgnoreCase("__includes"))
    includesPositionedTokens.next
    if (includesPositionedTokens.next.tpe != core.TokenType.OpenBracket)
      Seq()
    else
      includesPositionedTokens
        .takeWhile(_.tpe != core.TokenType.CloseBracket)
        .filter(_.tpe == core.TokenType.Literal)
        .map(_.value)
        .collect {
          case s: String => s
        }.toSeq
  }

}
/// for each source file. knits stages together. throws CompilerException

class StructureParser(
  displayName: Option[String],
  subprogram: Boolean) {

  def parse(tokens: Iterator[Token], oldResults: StructureResults): StructureResults =
    StructureCombinators.parse(tokens) match {
      case Right(declarations) =>
        StructureChecker.rejectDuplicateDeclarations(declarations)
        StructureChecker.rejectDuplicateNames(declarations,
          StructureParser.usedNames(
            oldResults.program, oldResults.procedures, declarations))
        StructureConverter.convert(declarations, displayName,
          if (subprogram)
            StructureResults(program = oldResults.program)
          else oldResults,
          subprogram)
      case Left((msg, token)) =>
        exception(msg, token)
    }

}
