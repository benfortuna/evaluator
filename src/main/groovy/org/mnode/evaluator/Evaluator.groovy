/**

* This file is part of Base Modules.
 *
 * Copyright (c) 2010, Ben Fortuna [fortuna@micronode.com]
 *
 * Base Modules is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Base Modules is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Base Modules.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.mnode.evaluator

import groovy.swing.SwingBuilder
import groovy.lang.GroovyShell
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.PopupMenu
import java.awt.MenuItem
import javax.swing.JFrame
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.Action
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import java.awt.Component
import javax.swing.JList
import javax.swing.DefaultListModel
import java.awt.event.KeyEvent
import java.awt.Color
import java.awt.Window
import java.net.URI
import java.awt.Desktop
import javax.swing.JScrollPane
import javax.swing.Icon
import javax.swing.ImageIcon
import java.awt.image.ImageProducer
import java.awt.Toolkit
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.Image
import javax.swing.SwingConstants
import javax.swing.JTable
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext
import javax.swing.text.StyledDocument
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.Document
import javax.swing.text.AttributeSet
import javax.swing.text.SimpleAttributeSet
import groovy.swing.LookAndFeelHelper
import java.awt.BorderLayout
import groovy.ui.OutputTransforms
import groovyx.net.ws.WSClient
import net.sf.json.groovy.JsonSlurper
import groovy.sql.Sql

/*
@Grapes([
    @Grab(group='jxlayer', module='layer', version='3.0'),
    @Grab(group='net.sf.json-lib', module='json-lib', version='2.3', classifier='jdk15'),
//    @Grab(group='com.seaglasslookandfeel', module='seaglasslookandfeel', version='0.1.7.2')])
    @Grab(group='org.codehaus.groovy.modules', module='groovyws', version='0.5.1')])
    */
class Evaluator {

     static void close(def frame, def exit) {
         if (exit) {
             System.exit(0)
         }
         else {
             frame.visible = false
         }
     }

    static void appendOutput(String text, AttributeSet style, Document doc){
        doc.insertString(doc.length, text, style)
//        ensureNoDocLengthOverflow(doc)
    }

    static void appendOutput(Window window, AttributeSet style, Document doc) {
        appendOutput(window.toString(), style, doc)
    }

    static void appendOutput(Object object, AttributeSet style, Document doc) {
        appendOutput(object.toString(), style, doc)
    }

    static void appendOutput(Component component, AttributeSet style, Document doc) {
        SimpleAttributeSet sas = new SimpleAttributeSet();
        sas.addAttribute(StyleConstants.NameAttribute, "component")
        StyleConstants.setComponent(sas, component)
        appendOutput(component.toString(), sas, doc)
    }

    static void appendOutput(Icon icon, AttributeSet style, Document doc) {
        SimpleAttributeSet sas = new SimpleAttributeSet();
        sas.addAttribute(StyleConstants.NameAttribute, "icon")
        StyleConstants.setIcon(sas, icon)
        appendOutput(icon.toString(), sas, doc)
    }

  static void main(def args) {
    LookAndFeelHelper.instance.addLookAndFeelAlias('seaglass', 'com.seaglasslookandfeel.SeaGlassLookAndFeel')

    StyledDocument doc = new DefaultStyledDocument()
    def inputStyle = doc.addStyle("input", StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE))
    StyleConstants.setForeground(inputStyle, Color.LIGHT_GRAY)
    
    def resultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
    
    def binding = new Binding()   
    binding.variables._outputTransforms = []
    binding.variables._outputTransforms += { it -> if (it instanceof ImageProducer) new ImageIcon(Toolkit.defaultToolkit.createImage(it)) }
    binding.variables._outputTransforms += OutputTransforms.loadOutputTransforms()
    
    binding.variables._ui = new SwingBuilder()
    binding.variables._xml = new XmlSlurper()
    binding.variables._json = new JsonSlurper()
    
    binding.variables._ws = [:]
    binding.variables._ws.currency = new WebService(wsdl: "http://www.webservicex.net/CurrencyConvertor.asmx?WSDL")
    binding.variables._ws.market = new WebService(wsdl: "http://www.webservicex.net/stockquote.asmx?WSDL")
    binding.variables._ws.whois = new WebService(wsdl: "http://www.webservicex.net/whois.asmx?WSDL")
    binding.variables._ws.units = new WebService(wsdl: "http://www.webservicex.net/ConvertCooking.asmx?WSDL")
    binding.variables._ws.geo = new WebService(wsdl: "http://www.webservicex.net/geoipservice.asmx?WSDL")
    binding.variables._ws.barcode = new WebService(wsdl: "http://www.webservicex.net/barcode.asmx?WSDL")
    
    binding.variables._db = [:]
    binding.variables._db.dmowner = new Database(driverClass: "oracle.jdbc.pool.OracleConnectionPoolDataSource", url: "jdbc:oracle:thin:DM_OWNER/tiger@mdxodb02:1521:ora10dv")
    
    def shell = new GroovyShell(binding)
    
    def evaluate = { expression ->
        def result = shell.evaluate(expression)
        OutputTransforms.transformResult(result, shell.context._outputTransforms)
//        return result
    }
    
    def evaluations = []
    
    def synonyms = []
    synonyms += new Synonym(name: 'avg', input: '{ it.sum() / it.size() }')
    synonyms += new Synonym(name: 'median', input: '{ it.size() % 2 == 0 ? it[it.size() / 2 - 1 as int] : it[(it.size() + 1) / 2 - 1 as int] }')
    synonyms += new Synonym(name: 'mode', input: '{ vals -> vals.max { vals.count(it) } }')
    synonyms += new Synonym(name: 'range', input: '{ it.max() - it.min() }')
    synonyms += new Synonym(name: 'nslookup', input: '{ InetAddress.getAllByName(it) }')
    synonyms += new Synonym(name: 'reverseLookup', input: '{ InetAddress.getByAddress((byte[]) it).hostName }')
    synonyms += new Synonym(name: 'pieChart', input: '''{ data, labels, height = 150, width = 400 -> new URL("http://chart.apis.google.com/chart?cht=p3&chs=${width}x${height}&chd=t:${URLEncoder.encode(data.join(','))}&chl=${URLEncoder.encode(labels.join('|'))}").content }''')
    synonyms += new Synonym(name: 'table', input: '{ result, columns = null, height = 150, width = 200 -> _ui.scrollPane(preferredSize: new java.awt.Dimension(width, height)) { _ui.table { tableModel(list: result) { if (columns) { columns.each { column -> closureColumn(header: column, read: { row -> (row.containsKey(column)) ? row[column] : "-" } ) } } else { closureColumn(header: "Result", read: { row -> row} ) } } } } }')
    synonyms += new Synonym(name: 'currency', input: '{ from, Object[] to -> def result = ["${from}":1]; for (c in to) { result += ["${c}":_ws.currency.client.ConversionRate(from, c)] }; return result }')
    synonyms += new Synonym(name: 'stockQuote', input: '{ symbol -> _xml.parseText(_ws.market.client.GetQuote(symbol)) }')
    synonyms += new Synonym(name: 'printNode', input: '{ node -> node.children().collect { "${it.name()} = ${it.text()}"} }')
    synonyms += new Synonym(name: 'whois', input: '{ name -> _ws.whois.client.GetWhoIS(name).split("\\n") }')
    synonyms += new Synonym(name: 'convertUnits', input: '{ amount, from, Object[] to -> def result = ["${from}":"${amount}"]; for (c in to) { result += ["${c}":_ws.units.client.ChangeCookingUnit(amount, from, c)] }; return result }')
    synonyms += new Synonym(name: 'geoLocate', input: '{ address -> _ws.geo.client.GetGeoIP(address) }')
    synonyms += new Synonym(name: 'geoLocateHost', input: '{ host -> geoLocate(nslookup(host)[0].hostAddress) }')
    synonyms += new Synonym(name: 'barCode', input: '{ text, size = 50 -> java.awt.Toolkit.defaultToolkit.createImage(_ws.barcode.client.Code39(text, size, true)) }')
    synonyms += new Synonym(name: 'freebase', input: '{ query, limit = 50 -> _json.parse(new URL("http://www.freebase.com/api/service/search?query=${URLEncoder.encode(query)}&limit=${limit}")).result }')
    synonyms += new Synonym(name: 'map', input: '{ query, height = 250, width = 300, zoom = 14 -> new URL("http://maps.google.com/maps/api/staticmap?center=${URLEncoder.encode(query)}&zoom=${zoom}&size=${width}x${height}&maptype=roadmap&markers=color:blue|label:S|40.702147,-74.015794&markers=color:green|label:G|40.711614,-74.012318&markers=color:red|color:red|label:C|40.718217,-73.998284&sensor=false").content }')
    synonyms += new Synonym(name: 'walpha', input: '{ query, appid = "XXXX" -> _xml.parse(new URL("http://api.wolframalpha.com/v1/query?input=${URLEncoder.encode(query)}&appid=${appid}").content) }')
    synonyms += new Synonym(name: 'date', input: '{ format = "short" -> (format == "short") ? new Date().format("dd/MM/yy") : new Date().format("dd/MM/yy HH:mm:ss") }')
    
    for (synonym in synonyms) {
        try {
            evaluate("${synonym.name} = ${synonym.input}")
        }
        catch (Exception e) {
            e.printStackTrace()
        }
    }
    
    def swing = new SwingBuilder()
    
    def display = { evaluation ->
//        swing.edt {
//             def doc = resultField.styledDocument
             if (doc.length > 0 && doc.getText(doc.length - 1, 1) != '\n') {
                 appendOutput('\n', inputStyle, doc)
             }
             appendOutput(evaluation.input, inputStyle, doc)
             appendOutput('\n', inputStyle, doc)
             appendOutput(evaluation.result, resultStyle, doc)
//             doc.insertString(doc.length, "${evaluation.input}\n", inputStyle)
//             doc.insertString(doc.length, "= ${evaluation.result}\n", resultStyle)
//        }
    }
    
    def editSynonyms = { parent ->
        swing.dialog(title: 'Synonyms', size: [400, 300], show: true, owner: parent, modal: true, locationRelativeTo: parent) {
            borderLayout()
            panel(border: emptyBorder(5)) {
                borderLayout()
                scrollPane(horizontalScrollBarPolicy: JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, border: null) {
                    list(id: 'synonymsList')
                    synonymsList.cellRenderer = new SynonymListCellRenderer()
                    def synonymsModel = new DefaultListModel()
                    for (synonym in synonyms) {
                        synonymsModel.addElement(synonym)
                    }
                    synonymsList.model = synonymsModel
                    synonymsList.selectionModel.valueChanged = {
                        if (synonymsList.selectedValue) {
                            synonymEditField.text = synonymsList.selectedValue.input
                        }
                        else {
                            synonymEditField.text = null
                        }
                    }
                }
            }
            panel(border: emptyBorder(5), constraints: BorderLayout.SOUTH) {
                borderLayout()
                scrollPane(border: null) {
                    textArea(rows: 4, id: 'synonymEditField')
                }
            }
        }
    }
    
    def editWebServices = { parent ->
        swing.dialog(title: 'Web Services', size: [400, 250], show: true, owner: parent, modal: true, locationRelativeTo: parent) {
            borderLayout()
            panel(border: emptyBorder(5)) {
                borderLayout()
                scrollPane(horizontalScrollBarPolicy: JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, border: null) {
                    list(id: 'wsList')
                    wsList.cellRenderer = new DefaultListCellRenderer()
                    def wsModel = new DefaultListModel()
                    for (ws in binding.variables._ws) {
                        wsModel.addElement(ws.key)
                    }
                    wsList.model = wsModel
                    wsList.selectionModel.valueChanged = {
                        if (wsList.selectedValue) {
                            wsEditField.text = binding.variables._ws[wsList.selectedValue].wsdl
                        }
                        else {
                            wsEditField.text = null
                        }
                    }
                }
            }
            panel(border: emptyBorder(5), constraints: BorderLayout.SOUTH) {
                borderLayout()
//                scrollPane(border: null) {
                    textField(id: 'wsEditField')
//                }
            }
        }
    }
    
    def editDatabases = { parent ->
        swing.dialog(title: 'Databases', size: [400, 250], show: true, owner: parent, modal: true, locationRelativeTo: parent) {
            borderLayout()
            panel(border: emptyBorder(5)) {
                borderLayout()
                scrollPane(horizontalScrollBarPolicy: JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, border: null) {
                    list(id: 'dbList')
                    dbList.cellRenderer = new DefaultListCellRenderer()
                    def dbModel = new DefaultListModel()
                    for (db in binding.variables._db) {
                        dbModel.addElement(db.key)
                    }
                    dbList.model = dbModel
                    dbList.selectionModel.valueChanged = {
                        if (dbList.selectedValue) {
                            dbEditField.text = binding.variables._db[dbList.selectedValue].url
                        }
                        else {
                            dbEditField.text = null
                        }
                    }
                }
            }
            panel(border: emptyBorder(5), constraints: BorderLayout.SOUTH) {
                borderLayout()
//                scrollPane(border: null) {
                    textField(id: 'dbEditField')
//                }
            }
        }
    }
    
    def inputPrompt = 'Enter an expression'

    swing.edt {
      lookAndFeel('seaglass', 'system') //, 'substance')

      frame(title: 'Evaluator', size: [350, 480], show: true, locationRelativeTo: null,
              defaultCloseOperation: JFrame.DO_NOTHING_ON_CLOSE, iconImage: imageIcon('/logo-16.png', id: 'logo').image, id: 'evaluatorFrame') {
          
          actions {
              action(id: 'exitAction', name: 'Exit', accelerator: shortcut('Q'), closure: { close(evaluatorFrame, true) })
              action(id: 'editSynonymsAction', name: 'Synonyms..', closure: { editSynonyms(evaluatorFrame) })
              action(id: 'editWebServicesAction', name: 'Web Services..', closure: { editWebServices(evaluatorFrame) })
              action(id: 'editDatabasesAction', name: 'Databases..', closure: { editDatabases(evaluatorFrame) })
              action(id: 'onlineHelpAction', name: 'Online Help', accelerator: 'F1', closure: { Desktop.desktop.browse(URI.create('http://basetools.org/evaluator')) })
              action(id: 'showTipsAction', name: 'Tips', closure: { tips.showDialog(evaluatorFrame) }, enabled: false)
              action(id: 'aboutAction', name: 'About', closure: {
                  dialog(title: 'About Evaluator', size: [350, 250], show: true, owner: evaluatorFrame, modal: true, locationRelativeTo: evaluatorFrame) {
                      borderLayout()
                      label(text: 'Evaluator 1.0', constraints: BorderLayout.NORTH, border: emptyBorder(10))
                      panel(constraints: BorderLayout.CENTER, border: emptyBorder(10)) {
                          borderLayout()
                          scrollPane(horizontalScrollBarPolicy: JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, border: null) {
                              table(id: 'propertyTable') {
                                  def systemProps = []
                                  for (propName in System.properties.keySet()) {
                                      systemProps.add([property: propName, value: System.properties.getProperty(propName)])
                                  }
                                  tableModel(list: systemProps) {
                                      propertyColumn(header:'Property', propertyName:'property')
                                      propertyColumn(header:'Value', propertyName:'value')
                                  }
                              }
                          }
                      }
                  }
              })
          }
          
          menuBar {
              menu(text: 'File', mnemonic: 'F') {
                  menuItem(exitAction)
              }
              menu(text: "Edit", mnemonic: 'E') {
                  menuItem(editSynonymsAction)
                  separator()
                  menuItem(editWebServicesAction)
                  separator()
                  menuItem(editDatabasesAction)
              }
              menu(text: 'View', mnemonic: 'V') {
                  checkBoxMenuItem(text: "Number Pad", id: 'viewNumPad')
                  separator()
                  checkBoxMenuItem(text: "Word Wrap", id: 'viewWordWrap')
              }
              menu(text: "Help", mnemonic: 'H') {
                  menuItem(onlineHelpAction)
                  menuItem(showTipsAction)
                  separator()
                  menuItem(aboutAction)
              }
          }
          
          borderLayout()
//          tabbedPane(border: emptyBorder(5), id: 'tabs') {
              panel(name: 'Sheet 1') {
                  borderLayout()
                  scrollPane() {
//                        textArea(document: doc, editable: false, wrapStyleWord: true, columns: 15, rows: 4, id: 'resultField')
                        textPane(document: doc, editable: false, id: 'resultField')
                        bind(source: viewWordWrap, sourceProperty:'selected', target: resultField, targetProperty: 'lineWrap')
//                        list(id: 'evaluations')
//                        evaluations.cellRenderer = new EvaluationListCellRenderer()
//                        def evaluationModel = new DefaultListModel()
//                        evaluations.model = evaluationModel
                  }
                  textField(text: inputPrompt, constraints: BorderLayout.SOUTH, columns: 15, foreground: Color.LIGHT_GRAY, id: 'inputField')
                 inputField.focusGained = {
                     if (inputField.text == inputPrompt) {
                         inputField.text = null
                         inputField.foreground = Color.BLACK
                     }
                 }
                 inputField.focusLost = {
                     if (!inputField.text) {
                         inputField.text = inputPrompt
                         inputField.foreground = Color.LIGHT_GRAY
                     }
                 }
                 inputField.keyPressed = { e ->
                     if (e.keyCode == KeyEvent.VK_ESCAPE) {
                         inputField.text = null
                     }
                     else if (e.keyCode == KeyEvent.VK_UP && evaluations) {
                         inputField.text = evaluations[evaluations.size - 1].input
                     }
                 }
                  inputField.actionPerformed = {
                      if (inputField.text) {
//                            resultField.text = "${resultField.text}\n${evaluate(inputField.text)}"
                            def evaluation = new Evaluation(input: inputField.text, result: 'Calculating..')
                            doOutside {
                                try {
                                    evaluation.result = evaluate(evaluation.input)
                                }
                                catch (Exception e) {
                                    evaluation.result = e
                                }
                                evaluations += evaluation
                                
                                doLater {
                                    display evaluation
//                            evaluations.ensureIndexIsVisible(evaluations.model.size - 1)
                                    resultField.caretPosition = doc.length - 1
                                }
                            }
                            inputField.text = ""
                      }
                  }
              }
//          }
          panel(constraints: BorderLayout.SOUTH, border: emptyBorder(5), id: 'numPad') {
              gridLayout(rows: 4, columns: 5)
              
              def appendInput = {
                  if (!inputField.text || inputField.text == inputPrompt) {
                      if (it == '.') {
                        inputField.text = "0${it}"
                      }
                      else {
                        inputField.text = it
                      }
                  }
                  else if ((it instanceof Number || it == '.') && (inputField.text[-1].isNumber() || inputField.text[-1] == '.')) {
                      inputField.text = "${inputField.text}${it}"
                  }
                  else if (it == '.' && !inputField.text[-1].isNumber()) {
                      inputField.text = "${inputField.text} 0${it}"
                  }
                  else {
                      inputField.text = "${inputField.text} ${it}"
                  }
              }
              
              button(text: '7', id: 'but7', actionPerformed: { appendInput(7) })
              //but7.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('7'), 'doClick')
              //but7.actionMap.put('doClick', { but7.doClick() } as Action)
              button(text: '8', id: 'but8', actionPerformed: { appendInput(8) })
              button(text: '9', id: 'but9', actionPerformed: { appendInput(9) })
              button(text: '/', actionPerformed: { appendInput('/') })
              button(text: '(', actionPerformed: { appendInput('(') })
              
              button(text: '4', id: 'but4', actionPerformed: { appendInput(4) })
              button(text: '5', id: 'but5', actionPerformed: { appendInput(5) })
              button(text: '6', id: 'but6', actionPerformed: { appendInput(6) })
              button(text: '*', actionPerformed: { appendInput('*') })
              button(text: ')', actionPerformed: { appendInput(')') })
              
              button(text: '1', id: 'but1', actionPerformed: { appendInput(1) })
              button(text: '2', id: 'but2', actionPerformed: { appendInput(2) })
              button(text: '3', id: 'but3', actionPerformed: { appendInput(3) })
              button(text: '-', actionPerformed: { appendInput('-') })
              button(text: '%', toolTipText: 'Modulus', actionPerformed: { appendInput('%') })
              
              button(text: '0', id: 'but0', actionPerformed: { appendInput(0) })
              button(text: '.', actionPerformed: { appendInput('.') })
              button(text: '=', actionPerformed: { inputField.postActionEvent() })
              button(text: '+', actionPerformed: { appendInput('+') })
              button(text: '**', toolTipText: 'Power of', actionPerformed: { appendInput('**') })
          }
          bind(source: viewNumPad, sourceProperty:'selected', target: numPad, targetProperty:'visible')
          
          if (SystemTray.isSupported()) {
              TrayIcon trayIcon = new TrayIcon(logo.image, 'Evaluator')
              trayIcon.imageAutoSize = false
              trayIcon.mousePressed = { event ->
                  if (event.button == MouseEvent.BUTTON1) {
                      evaluatorFrame.visible = true
                  }
              }
              
              PopupMenu popupMenu = new PopupMenu('Evaluator')
              MenuItem openMenuItem = new MenuItem('Open')
              openMenuItem.actionPerformed = {
                  evaluatorFrame.visible = true
              }
              popupMenu.add(openMenuItem)
              popupMenu.addSeparator()
              MenuItem exitMenuItem = new MenuItem('Exit')
              exitMenuItem.actionPerformed = {
                  close(evaluatorFrame, true)
              }
              popupMenu.add(exitMenuItem)
              trayIcon.popupMenu = popupMenu
              
              SystemTray.systemTray.add(trayIcon)
          }
      }
      evaluatorFrame.windowClosing = {
          close(evaluatorFrame, !SystemTray.isSupported())
      }
    }
  }
}

class Evaluation {
    def input
    def result
    
    String toString() {
        if (result) {
            if (result instanceof Exception) {
                return "<html><p>${input}</p><p style='font-style:italic;color:silver'>Resulted in exception: ${result}</p></html>"
            }
            else if (result instanceof Closure) {
                return "<html><p>${input}</p><p style='font-style:italic;color:silver'>Resulted in closure</p></html>"
            }
            else if (result instanceof ImageProducer || result instanceof Image) {
                return "<html><p>${input}</p><p style='font-style:italic;color:silver'>Resulted in image</p></html>"
            }
            else if (result instanceof JTable) {
                return "<html><p>${input}</p><p style='font-style:italic;color:silver'>Resulted in table</p></html>"
            }
            else {
                return "<html><p>${input}</p><p> = ${result}</p></html>"
            }
        }
        else {
            return "<html><p>${input}</p><p style='font-style:italic;color:silver'>No Result</p></html>"
        }
    }
}

class EvaluationListCellRenderer extends DefaultListCellRenderer {
    
    public EvaluationListCellRenderer() {
        verticalTextPosition = SwingConstants.TOP
        horizontalTextPosition = SwingConstants.CENTER
        
        horizontalAlignment = SwingConstants.LEFT
    }
    
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value.result instanceof ImageProducer) {
            icon = new SwingBuilder().imageIcon(Toolkit.defaultToolkit.createImage(value.result))
        }
        else if (value.result instanceof Image) {
            icon = new SwingBuilder().imageIcon(value.result)
        }
        else if (value.result instanceof JComponent) {
            BufferedImage image = new BufferedImage((int) value.result.preferredSize.width, (int) value.result.preferredSize.height, BufferedImage.TYPE_INT_RGB)
            Graphics2D g2 = image.createGraphics()
            value.result.paint(g2)
            g2.dispose()
            icon = new SwingBuilder().imageIcon(image)
        }
        else {
            icon = null
        }
        return this
    }
}

class Synonym {
    def name
    def input
    
    String toString() {
        return name
    }
}

class WebService {
    def name
    def wsdl
    @Lazy def client = { def c = new WSClient(wsdl, this.class.classLoader); c.initialize(); c }()
    
    String toString() {
        return name
    }
}

class Database {
    def driverClass
    def url
    @Lazy def client = { Class.forName(driverClass); Sql.newInstance(url) }()
}

class SynonymListCellRenderer extends DefaultListCellRenderer {
    
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        return this
    }
}
