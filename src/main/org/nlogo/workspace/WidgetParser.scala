// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.workspace

import org.nlogo.{ api, plot },
  api.ModelReader, api.StringUtils.escapeString,
  plot.PlotLoader

class WidgetParser(
  parser: api.ParserServices,
  worldLoader: Option[WorldLoaderInterface] = None,
  plotManager: Option[plot.PlotManagerInterface] = None,
  compilerTestingMode: Boolean = false
) {

  def parseWidgets(widgetsSection: Seq[String], netLogoVersion: String = api.Version.version):
      (Seq[String], Map[String, List[String]], Seq[String], Seq[String], String)  = {

    // parsing widgets dumps information into these four mutable vals.
    // as well as a few places in the workspace.
    val interfaceGlobals = new collection.mutable.ArrayBuffer[String]
    val constraints = new collection.mutable.HashMap[String, List[String]]
    val buttons = new collection.mutable.ArrayBuffer[String] // for TestCompileAll
    val monitors = new collection.mutable.ArrayBuffer[String] // for TestCompileAll
    val interfaceGlobalCommands = new StringBuilder

    //===
    // each widget type has its own parsing method
    //===

    def parseSlider(widget: Seq[String]) {
      interfaceGlobals += widget(6)
      interfaceGlobalCommands.append("set " + widget(6) + " " + widget(9) + "\n")
      constraints(widget(6)) = List("SLIDER", widget(7), widget(8), widget(10), widget(9))
    }

    def parseSwitch(widget: Seq[String]) {
      interfaceGlobals += widget(6)
      val defaultAsString = (widget(7).toDouble == 0).toString
      interfaceGlobalCommands.append("set " + widget(6) + " " + defaultAsString + "\n")
      constraints(widget(6)) = List("SWITCH", defaultAsString)
    }

    def parseChoiceOrChooser(widget: Seq[String]) {
      interfaceGlobals += widget(6)
      val valSpec = "[" + widget(7) + "]"
      constraints(widget(6)) = List("CHOOSER", valSpec, widget(8))
      val vals = parser.readFromString(valSpec).asInstanceOf[api.LogoList]
      val defaultAsString = api.Dump.logoObject(vals.get(widget(8).toInt), true, false)
      interfaceGlobalCommands.append(
        "set " + widget(6) + " " + defaultAsString + "\n")
    }

    def parseInputBox(widget: Seq[String]) {
      interfaceGlobals += widget(5)
      val defaultVal = escapeString(ModelReader.restoreLines(widget(6)))
      if (widget(9) == "Number" || widget(9) == "Color")
        interfaceGlobalCommands.append("set " + widget(5) + " " + defaultVal + "\n")
      else
        interfaceGlobalCommands.append("set " + widget(5) + " \"" + defaultVal + "\"\n")
      constraints(widget(5)) = List("INPUTBOX", defaultVal, widget(9))
    }

    def parsePlot(widget: Seq[String]) {
      // ick, side effects.
      // might replace identity soon as we might actually convert old models for headless.
      // JC - 9/14/10
      for(manager <- plotManager)
        PlotLoader.parsePlot(widget.toArray, manager.newPlot(""))
    }

    def parseButton(widget: Seq[String]) {
      val buttonSource = ModelReader.restoreLines(widget(6)) match {
        case "NIL" => ""
        case s => s
      }
      buttons +=
        (widget(10) match {
          case "TURTLE" => "ask turtles [" + buttonSource + "\n]" // newline to protect against comments
          case "PATCH"  => "ask patches [" + buttonSource + "\n]" // newline to protect against comments
          case "OBSERVER" => buttonSource
        })
    }

    def parseMonitor(widget: Seq[String]) {
      val monitorSource = widget(6)
      if (monitorSource != "NIL")
      // add "__ignore" to turn the reporter into a command, so we can handle buttons and
      // monitors uniformly.  newline to protect against comments
        monitors += "__ignore (" + ModelReader.restoreLines(monitorSource) + "\n)"
    }

    def parseView(widget: Seq[String]) {
      for(loader <- worldLoader)
        (new WorldLoader).load(widget, loader)
    }

    // finally parse all the widgets in the WIDGETS section
    for (widget <- ModelReader.parseWidgets(widgetsSection))
      widget.head match {
        case "SLIDER" => parseSlider(widget)
        case "SWITCH" => parseSwitch(widget)
        case "CHOICE" | "CHOOSER" => parseChoiceOrChooser(widget)
        case "INPUTBOX" => parseInputBox(widget)
        case "PLOT" => parsePlot(widget)
        case "BUTTON" if compilerTestingMode => parseButton(widget)
        case "MONITOR" if compilerTestingMode => parseMonitor(widget)
        case "GRAPHICS-WINDOW" => parseView(widget)
        case _ => // ignore
      }

    (interfaceGlobals, constraints.toMap, buttons, monitors, interfaceGlobalCommands.toString)

  }

}
