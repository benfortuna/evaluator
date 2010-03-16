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
//import sun.awt.image.URLImageSource
import groovyx.net.ws.WSClient

//@Grapes([
//    @Grab(group='com.seaglasslookandfeel', module='seaglasslookandfeel', version='0.1.7.2')])
//    @Grab(group='org.codehaus.groovy.modules', module='groovyws', version='0.5.1')])
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
/*
    static void appendOutput(URLImageSource imageSource, AttributeSet style, Document doc) {
        def icon = new ImageIcon(Toolkit.defaultToolkit.createImage(imageSource))
        appendOutput(icon, style, doc)
    }
*/
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
    
    binding.variables._ws = [:]
    binding.variables._ws.currency = new WSClient("http://www.webservicex.net/CurrencyConvertor.asmx?WSDL", Evaluator.class.classLoader)
    binding.variables._ws.market = new WSClient("http://www.webservicex.net/stockquote.asmx?WSDL", Evaluator.class.classLoader)
    binding.variables._ws.whois = new WSClient("http://www.webservicex.net/whois.asmx?WSDL", Evaluator.class.classLoader)
    binding.variables._ws.units = new WSClient("http://www.webservicex.net/ConvertCooking.asmx?WSDL", Evaluator.class.classLoader)
    
    def shell = new GroovyShell(binding)
    
    def evaluate = { expression ->
        def result = shell.evaluate(expression)
        OutputTransforms.transformResult(result, shell.context._outputTransforms)
//        return result
    }
    
    def evaluations = []
    
    def synonyms = []
    synonyms += new Synonym()
    synonyms[-1].name = 'avg'
    synonyms[-1].input = '{ it.sum() / it.size() }'
    
    synonyms += new Synonym()
    synonyms[-1].name = 'median'
    synonyms[-1].input = '{ it.size() % 2 == 0 ? it[it.size() / 2 - 1 as int] : it[(it.size() + 1) / 2 - 1 as int] }'
    
    synonyms += new Synonym()
    synonyms[-1].name = 'mode'
    synonyms[-1].input = '{ vals -> vals.max { vals.count(it) } }'
    
    synonyms += new Synonym()
    synonyms[-1].name = 'range'
    synonyms[-1].input = '{ it.max() - it.min() }'
    
    synonyms += new Synonym()
    synonyms[-1].name = 'nslookup'
    synonyms[-1].input = '{ InetAddress.getAllByName(it) }'
    
    synonyms += new Synonym()
    synonyms[-1].name = 'reverseLookup'
    synonyms[-1].input = '{ InetAddress.getByAddress((byte[]) it).hostName }'
        
    synonyms += new Synonym()
    synonyms[-1].name = 'pieChart'
    synonyms[-1].input = '''{ data, labels -> new URL("http://chart.apis.google.com/chart?cht=p3&chs=500x150&chd=t:${data.join(',')}&chl=${labels.join('|')}").content }'''
        
    synonyms += new Synonym()
    synonyms[-1].name = 'table'
    synonyms[-1].input = '{ result -> _ui.table { tableModel(list: result) { closureColumn(header: "Result", read: { row -> row} ) } } }'
        
    synonyms += new Synonym()
    synonyms[-1].name = 'currency'
    synonyms[-1].input = '{ from, to -> _ws.currency.initialize(); _ws.currency.ConversionRate(from, to) }'
        
    synonyms += new Synonym()
    synonyms[-1].name = 'quote'
    synonyms[-1].input = '''{ symbol -> _ws.market.initialize(); new XmlParser().parseText(_ws.market.GetQuote(symbol)) }'''
        
    synonyms += new Synonym()
    synonyms[-1].name = 'printNode'
    synonyms[-1].input = '{ node -> node.children().collect { "${it.name()} = ${it.text()}"} }'
        
    synonyms += new Synonym()
    synonyms[-1].name = 'whois'
    synonyms[-1].input = '''{ name -> _ws.whois.initialize(); _ws.whois.GetWhoIS(name).split('\\n') }'''

    synonyms += new Synonym()
    synonyms[-1].name = 'convertUnits'
    synonyms[-1].input = '''{ amount, from, to -> _ws.units.initialize(); _ws.units.ChangeCookingUnit(amount, _ws.units.create('net.webservicex.Cookings'), _ws.units.create('net.webservicex.Cookings')) }'''
    
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
    
    swing.edt {
      lookAndFeel('seaglass', 'system') //, 'substance')

      frame(title: 'Evaluator', size: [350, 480], show: true, locationRelativeTo: null,
              defaultCloseOperation: JFrame.DO_NOTHING_ON_CLOSE, iconImage: imageIcon('/logo-16.png', id: 'logo').image, id: 'evaluatorFrame') {
          
          actions {
              action(id: 'exitAction', name: 'Exit', accelerator: shortcut('Q'), closure: { close(evaluatorFrame, true) })
              action(id: 'editSynonymsAction', name: 'Synonyms..', closure: { editSynonyms(evaluatorFrame) })
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
                  def inputText = 'Enter an expression'
                  textField(text: inputText, constraints: BorderLayout.SOUTH, columns: 15, foreground: Color.LIGHT_GRAY, id: 'inputField')
                 inputField.focusGained = {
                     if (inputField.text == inputText) {
                         inputField.text = null
                         inputField.foreground = Color.BLACK
                     }
                 }
                 inputField.focusLost = {
                     if (!inputField.text) {
                         inputField.text = inputText
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
                            def evaluation = new Evaluation()
                            evaluation.input = inputField.text
                            try {
                                evaluation.result = evaluate(inputField.text)
                            }
                            catch (Exception e) {
                                evaluation.result = e
                            }
                            evaluations += evaluation
                            display evaluation
//                            evaluations.ensureIndexIsVisible(evaluations.model.size - 1)
                            resultField.caretPosition = doc.length - 1
                          inputField.text = ""
                      }
                  }
              }
//          }
          panel(constraints: BorderLayout.SOUTH, border: emptyBorder(5), id: 'numPad') {
              gridLayout(rows: 4, columns: 3)
              button(text: '7', id: 'but7', actionPerformed: { inputField.text = "${inputField.text}7" })
              //but7.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('7'), 'doClick')
              //but7.actionMap.put('doClick', { but7.doClick() } as Action)
              button(text: '8', id: 'but8', actionPerformed: { inputField.text = "${inputField.text}8" })
              button(text: '9', id: 'but9', actionPerformed: { inputField.text = "${inputField.text}9" })
              button(text: '/')
              
              button(text: '4', id: 'but4')
              but4.actionPerformed = { inputField.text = "${inputField.text}4" }
              button(text: '5', id: 'but5')
              but5.actionPerformed = { inputField.text = "${inputField.text}5" }
              button(text: '6', id: 'but6')
              but6.actionPerformed = { inputField.text = "${inputField.text}6" }
              button(text: '*')
              
              button(text: '1', id: 'but1')
              but1.actionPerformed = { inputField.text = "${inputField.text}1" }
              button(text: '2', id: 'but2')
              but2.actionPerformed = { inputField.text = "${inputField.text}2" }
              button(text: '3', id: 'but3')
              but3.actionPerformed = { inputField.text = "${inputField.text}3" }
              button(text: '-')
              
              button(text: '0', id: 'but0')
              but0.actionPerformed = { inputField.text = "${inputField.text}0" }
              button(text: '.')
              button(text: '+')
              button(text: '=')
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

class SynonymListCellRenderer extends DefaultListCellRenderer {
    
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        return this
    }
}
