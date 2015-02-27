// @ExecutionModes({on_single_node="main_menu_scripting/freeplaneGTD[addons.listNextActions]"})
//=========================================================
// Freeplane GTD+
//
// Copyright (c)2014 Gergely Papp
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//=========================================================

import freeplaneGTD.ClipBoardUtil
import freeplaneGTD.DateUtil
import freeplaneGTD.GTDMapReader
import freeplaneGTD.Tag
import groovy.swing.SwingBuilder
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.TextUtils
import org.freeplane.plugin.script.proxy.Proxy
import org.xhtmlrenderer.simple.FSScrollPane
import org.xhtmlrenderer.simple.XHTMLPanel
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler
import org.xhtmlrenderer.swing.BasicPanel
import org.xhtmlrenderer.swing.LinkListener

import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import java.awt.*
import java.awt.datatransfer.Clipboard
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.ParseException

String title = 'GTD Next Actions'
String userPath = c.userDirectory.toString()
String txtVer = '1.3'
String txtURI = 'http://www.itworks.hu/index.php/freeplane-gtd+'


class ReportModel {
    boolean filterDone
    int selPane
    Proxy.Node rootNode;
    def actionList;
    GTDMapReader mapreader;
    String todayText = TextUtils.getText("freeplaneGTD.view.when.today")
    String thisWeekText = TextUtils.getText("freeplaneGTD.view.when.this_week")

    def taskDateComparator = { a, b ->
        def aw = a['when']
        def bw = b['when']
        if ((!aw && !bw) || aw.equals(bw)) {
            return 0
        }
        Date ad = null
        Date bd = null
        if (aw == todayText) ad = new Date()
        else if (aw == thisWeekText) ad = new Date() + 7
        else if (aw instanceof Date) ad = aw
        else if (aw instanceof String) {
            try {
                ad = DateUtil.stdFormat.parse(aw)
            } catch (ParseException pe) {
                ad = null
            }
        }
        if (bw == todayText) bd = new Date()
        else if (bw == thisWeekText) bd = new Date() + 7
        else if (bw instanceof Date) bd = bw
        else if (bw instanceof String) {
            try {
                bd = DateUtil.stdFormat.parse(bw)
            } catch (ParseException pe) {
                bd = null
            }
        }
        if (!ad && !bd) {
            return aw < bw ? 1 : -1
        }
        if (ad && !bd) return -1
        if (!ad && bd) return 1
        return ad < bd ? -1 : 1
    }
    def taskSortComparator = { a, b ->
        if ((!a['priority'] && !b['priority']) || a['priority'].equals(b['priority'])) {
            return taskDateComparator(a, b)
        }
        if (!a['priority']) return 1
        if (!b['priority']) return -1
        return a['priority'] < b['priority'] ? -1 : 1
    }

    ReportModel(Proxy.Node rootNode) {
        this.rootNode = rootNode
        this.mapreader = GTDMapReader.instance
    }

    //--------------------------------------------------------------
    // parse the GTD mind map
    void parseMap() {
        // Expand any nodes with next action shorthand
        mapreader.convertShorthand(rootNode);

        // Get next action lists
        actionList = mapreader.getActionList(rootNode, filterDone);
    }

    int projectCount() {
        return actionList.size()
    }

    int delegateCount() {
        //Filter the missing delegates
        Set delegateGroups = actionList.groupBy({ it['who'] }).keySet()
        Set delegates = []
        delegateGroups.each {
            def individuals = it?.split(',')
            if (individuals) delegates.addAll(individuals)
        }
        return delegates.size()
    }

    static Tag getStyleSheet() {
        Tag style = new Tag('style',
                '/*<![CDATA[*/' +
                        'body {color:#000000; font-family:Calibri, Verdana, Arial; font-size:13pt; padding: 10px 25px 0px 25px; }\n' +
                        'h1 {font-size:24pt; font-weight:bold;}\n' +
                        'a {text-decoration: none; color:#990000;}\n' +
                        '.priority {padding: 2px; display:inline-block; margin-right: 2px; color: black; font-weight:bold;}\n' +
                        '.priority-0 {background-color: rgb(215,48,39);}\n' +
                        '.priority-1 {background-color: rgb(244,109,67);}\n' +
                        '.priority-2 {background-color: rgb(253,174,97);}\n' +
                        '.priority-3 {background-color: rgb(204,174,89)}\n' +
                        '.priority-4 {background-color: rgb(255,255,191);}\n' +
                        '.priority-5 {background-color: rgb(217,239,139);}\n' +
                        '.priority-6 {background-color: rgb(166,217,106);}\n' +
                        '.priority-7 {background-color: rgb(102,189,99);}\n' +
                        '.priority-8 {background-color: rgb(26,152,80);}\n' +
                        '.priority-9 {background-color: rgb(16,82,50);}' +
                        '.note {background-color: rgb(240,250,240);font-size:10pt}' +
                        '/*]]>*/',
                [type: 'text/css'])

        return style
    }

    Tag displayNote(Proxy.Node n) {
        /*       if (n.noteText) {
                   Tag tag = new Tag('div',null,[class: 'note'])
                   tag.addContent(n.displayedText)
                   return tag
               }*/
        return null
    }


    String projectText() {
        Tag html = new Tag('html', null, [xmlns: 'http://www.w3.org/1999/xhtml'])
        Tag head = html.addChild('head')
        head.addContent(getStyleSheet())
        head.addChild('title')
        Tag body = html.addChild('body')
        body.addContent('h1', TextUtils.getText("freeplaneGTD_view_project"))
        Map naByGroup = actionList.groupBy { it['project'] }
        naByGroup = naByGroup.sort { it.toString().toLowerCase() }
        naByGroup.each {
            key, value ->
                body.addContent('h2', key)
                Tag curItem = body.addChild('ul')
                def curGroup = naByGroup[key].sort { a, b -> taskSortComparator(a, b) }
                curGroup.each {
                    Tag wrap = curItem.addChild('li')
                    if (it['done']) wrap = wrap.addChild('del')
                    if (it['priority']) {
                        wrap = wrap.addContent('span', it['priority'], [class: 'priority priority-' + it['priority']])
                    }
                    wrap.addContent('a', it['action'], [href: it['nodeID']]).addContent(
                            (it['who'] ? ' [' + it['who'] + ']' : '') +
                                    (it['when'] ? ' {' + it['when'] + '}' : '') +
                                    (it['context'] ? ' @' + it['context'] : ''))
                    wrap.addContent(displayNote(it['node']) ?: '')
                }
        }
        return '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">\n' + html.toString()
    }

    String delegateText() {
        Tag html = new Tag('html', null, [xmlns: 'http://www.w3.org/1999/xhtml'])
        Tag head = html.addChild('head')
        head.addContent(getStyleSheet())
        head.addChild('title')
        Tag body = html.addChild('body')
        body.addContent('h1', TextUtils.getText("freeplaneGTD_view_who"))
        Map naByGroupFull = actionList.groupBy { it['who'] }
        Map naByGroup = [:]
        naByGroupFull.each {
            key, value ->
                def keyList = key?.split(',')
                keyList.each {
                    naByGroup.put(it, value)
                }
        }
        naByGroup = naByGroup.sort { it.toString().toLowerCase() }
        naByGroup.each {
            key, value ->
                if (key) {
                    body.addContent('h2', key)
                    Tag curItem = body.addChild('ul')
                    def curGroup = naByGroup[key].sort { a, b -> taskSortComparator(a, b) }
                    curGroup.each {
                        Tag wrap = curItem.addChild('li')
                        if (it['done']) wrap = wrap.addChild('del')
                        if (it['priority']) {
                            wrap = wrap.addContent('span', it['priority'], [class: 'priority priority-' + it['priority']])
                        }
                        wrap.addContent('a', it['action'], [href: it['nodeID']]).addContent(
                                (it['when'] ? ' {' + it['when'] + '}' : '') +
                                        (it['context'] ? ' @' + it['context'] : '') +
                                        ' for ' + it['project'])
                        wrap.addContent(displayNote(it['node']) ?: '')
                    }
                }
        }
        return '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "resources/schema/xhtml/xhtml-1/xhtml1-strict.dtd">\n' + html.toString()
    }

    String contextText() {
        Tag html = new Tag('html', null, [xmlns: 'http://www.w3.org/1999/xhtml'])
        Tag head = html.addChild('head')
        head.addContent(getStyleSheet())
        head.addChild('title')
        Tag body = html.addChild('body')
        body.addContent('h1', TextUtils.getText("freeplaneGTD_view_context"))
        Map naByGroupFull = actionList.groupBy { it['context'] }

        Map naByGroup = [:]
        naByGroupFull.each {
            key, value ->
                def keyList = key?.split(',')
                keyList.each {
                    naByGroup.put(it, value)
                }
        }
        naByGroup = naByGroup.sort { it.toString().toLowerCase() }
        naByGroup.each {
            key, value ->
                body.addContent('h2', key ?: TextUtils.getText("freeplaneGTD.view.context.unassigned"))
                Tag curItem = body.addChild('ul')
                def curGroup = naByGroup[key].sort { a, b -> taskSortComparator(a, b) }
                curGroup.each {
                    Tag wrap = curItem.addChild('li')
                    if (it['done']) wrap = wrap.addChild('del')
                    if (it['priority']) {
                        wrap = wrap.addContent('span', it['priority'], [class: 'priority priority-' + it['priority']])
                    }
                    wrap.addContent('a', it['action'], [href: it['nodeID']]).addContent(
                            (it['who'] ? ' [' + it['who'] + ']' : '') +
                                    (it['when'] ? ' {' + it['when'] + '}' : '') +
                                    ' for ' + it['project'])
                    wrap.addContent(displayNote(it['node']) ?: '')
                }
        }
        return '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "resources/schema/xhtml/xhtml-1/xhtml1-strict.dtd">\n' + html.toString()
    }

    String timelineText() {
        Tag html = new Tag('html', null, [xmlns: 'http://www.w3.org/1999/xhtml'])
        Tag head = html.addChild('head')
        head.addContent(getStyleSheet())
        head.addChild('title')
        Tag body = html.addChild('body')
        body.addContent('h1', TextUtils.getText("freeplaneGTD_view_when"))
        def sortedList = actionList.sort { a, b -> taskDateComparator(a, b) }
        def naByGroup = sortedList.groupBy { it['when'] }
        naByGroup.each {
            key, value ->
                body.addContent('h2', key)
                Tag curItem = body.addChild('ul')
                def curGroup = naByGroup[key].sort { a, b -> taskSortComparator(a, b) }
                curGroup.each {
                    Tag wrap = curItem.addChild('li')
                    if (it['done']) wrap = wrap.addChild('del')
                    if (it['priority']) {
                        wrap = wrap.addContent('span', it['priority'], [class: 'priority priority-' + it['priority']])
                    }
                    wrap.addContent('a', it['action'], [href: it['nodeID']]).addContent(
                            (it['who'] ? ' [' + it['who'] + ']' : '') +
                                    (it['context'] ? ' @' + it['context'] : '') +
                                    ' for ' + it['project'])
                    wrap.addContent(displayNote(it['node']) ?: '')
                }
        }
        return '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "resources/schema/xhtml/xhtml-1/xhtml1-strict.dtd">\n' + html.toString()
    }
}

def panelTitle = { panelT, count = null ->
    new Tag('html', new Tag('body',
            new Tag('div', panelT, [style: 'font-weight:bold;font-style:italic']), [height: '50']).
            addContent(count != null ? new Tag('div', count, [style: 'font-size:24pt;color:#666666;text-align:center']).toString() : ''))
}
ReportModel report = new ReportModel(node.map.root)

def refresh = {
    report.parseMap()
    try {
        projectPane.setDocumentFromString(report.projectText(), null, new XhtmlNamespaceHandler())
        delegatePane.setDocumentFromString(report.delegateText(), null, new XhtmlNamespaceHandler())
        contextPane.setDocumentFromString(report.contextText(), null, new XhtmlNamespaceHandler())
        timelinePane.setDocumentFromString(report.timelineText(), null, new XhtmlNamespaceHandler())
    } catch (e) {
        println(report.projectText())
    }
    tabbedPane.setTitleAt(0, panelTitle(TextUtils.getText("freeplaneGTD.tab.project.title"), report.projectCount()).toString())
    tabbedPane.setTitleAt(1, panelTitle(TextUtils.getText("freeplaneGTD.tab.who.title"), report.delegateCount()).toString())

    tabbedPane.selectedIndex = report.selPane;
    cbFilterDone.selected = report.filterDone
}
SwingBuilder.edtBuilder {
    // make the frame half the height and width
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize()
    int frHeight = (screenSize.height) / 4 * 3
    int frWidth = (screenSize.width) / 4 * 3

    iconFrame = imageIcon(userPath + "/icons/fpgtdIcon.png").image
    iconLogo = imageIcon(userPath + "/resources/images/fpgtdLogo.png")
    mainFrame = frame(title: title,
            iconImage: iconFrame,
            size: [frWidth, frHeight],
            defaultCloseOperation: JFrame.DISPOSE_ON_CLOSE,
            show: false) {
        borderLayout()
        reportPanel = panel(constraints: BorderLayout.CENTER) {
            gridLayout(cols: 1, rows: 1)
            tabbedPane = tabbedPane(tabPlacement: JTabbedPane.RIGHT, selectedIndex: report.selPane) {

                projectPanel = panel(toolTipText: TextUtils.getText("freeplaneGTD.tab.project.tooltip")) {
                    gridLayout(cols: 1, rows: 1)
                }
                delegatePanel = panel(toolTipText: TextUtils.getText("freeplaneGTD.tab.who.tooltip")) {
                    gridLayout(cols: 1, rows: 1)
                }
                contextPanel = panel(name: panelTitle(TextUtils.getText("freeplaneGTD.tab.context.title")),
                        toolTipText: TextUtils.getText("freeplaneGTD.tab.context.tooltip")) {
                    gridLayout(cols: 1, rows: 1)
                }
                timelinePanel = panel(name: panelTitle(TextUtils.getText("freeplaneGTD.tab.when.title")),
                        toolTipText: TextUtils.getText("freeplaneGTD.tab.when.tooltip")) {
                    gridLayout(cols: 1, rows: 1)
                }
                panel(name: panelTitle(TextUtils.getText("freeplaneGTD.tab.about.title")),
                        toolTipText: TextUtils.getText("freeplaneGTD.tab.about.tooltip")) {
                    panel() {
                        gridLayout(cols: 2, rows: 1)
                        label(text: new Tag('html',
                                new Tag('body',
                                        new Tag('h1', 'Freeplane|')
                                                .addContent('span', 'GTD', [style: 'color:#ff3300'])
                                                .addContent('h2', 'Version ' + txtVer), [style: 'padding-left:25px'])),
                                icon: iconLogo,
                                horizontalAlignment: JLabel.CENTER);
                    }

                    linkURL = label(text: "<html><h4>by Gergely Papp<br/><h5>based on the original code by Auxilus Systems LLC</h5><h4>Licensed under GNU GPL Version 3</h4><a href='" + txtURI + "'>" + txtURI + "</a></html>", horizontalAlignment: JLabel.CENTER,
                            cursor: new Cursor(Cursor.HAND_CURSOR));
                }
            }
            projectPane = new XHTMLPanel()
            projectPanel.add(TextUtils.getText("freeplaneGTD.tab.project.tooltip"), new FSScrollPane(projectPane))

            delegatePane = new XHTMLPanel()
            delegatePanel.add(TextUtils.getText("freeplaneGTD.tab.project.tooltip"), new FSScrollPane(delegatePane))

            contextPane = new XHTMLPanel()
            contextPanel.add(TextUtils.getText("freeplaneGTD.tab.project.tooltip"), new FSScrollPane(contextPane))

            timelinePane = new XHTMLPanel()
            timelinePanel.add(TextUtils.getText("freeplaneGTD.tab.project.tooltip"), new FSScrollPane(timelinePane))
        }
        panel(constraints: BorderLayout.SOUTH) {
            boxLayout(axis: BoxLayout.X_AXIS)
            button(text: TextUtils.getText("freeplaneGTD.button.refresh"),
                    actionPerformed: {
                        refresh(mainFrame)
                    })
            button(text: TextUtils.getText("freeplaneGTD.button.copy"),
                    actionPerformed: {
                        Clipboard clip = projectPanel.getToolkit().getSystemClipboard();
                        if (clip != null) {
                            switch (report.selPane) {
                                case 0: curContent = report.projectText(); break;
                                case 1: curContent = report.delegateText(); break;
                                case 2: curContent = report.contextText(); break;
                                case 3: curContent = report.timelineText(); break;
                                default: curContent = report.projectText(); break;
                            }
                            clip.setContents(ClipBoardUtil.createTransferable(curContent), null)
                        }
                    })
            button(text: TextUtils.getText("freeplaneGTD.button.cancel"),
                    actionPerformed: {
                        mainFrame.hide()
                        mainFrame.dispose()
                    })
            cbFilterDone = checkBox(text: TextUtils.getText("freeplaneGTD.button.filter_done"),
                    selected: report.filterDone,
                    actionPerformed: { report.filterDone = it.source.selected; refresh(mainFrame) })
        }
    }

    // Register a change listener to track selected tab
    tabbedPane.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent evt) {
            JTabbedPane pane = (JTabbedPane) evt.getSource();
            // Get current tab index
            report.selPane = pane.getSelectedIndex();
        }
    });

    // Add hyperlink listener to about dialog
    linkURL.addMouseListener(
            new MouseAdapter() {
                public void mouseClicked(MouseEvent event) {
                    URI uriLink = new URI(txtURI);
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().browse(uriLink);
                        } catch (IOException e) { /* TODO: error handling */
                        }
                    } else { /* TODO: error handling */
                    }
                }
            })

}

//---------------------------------------------------------
// Process hyperlink to map node
//---------------------------------------------------------
class NodeLink extends LinkListener {
    Proxy.Controller ctrl
    JFrame frame

    NodeLink(Proxy.Controller ctrl, JFrame frame) {
        this.ctrl = ctrl
        this.frame = frame

    }

    public void linkClicked(BasicPanel panel, String uri) {
        String linkNodeID = uri;
        def nodesFound = ctrl.find { it.nodeID == linkNodeID };

        if (nodesFound[0] != null) {
            FoldToTop(nodesFound[0]);
            UnfoldBranch(nodesFound[0]);
            ctrl.select(nodesFound[0]);
            ctrl.centerOnNode(nodesFound[0]);
            ctrl.centerOnNode(nodesFound[0]);
            frame.dispose()
            frame.visible=false
        } else {
            UITools.informationMessage("Next Action not found in mind map. Refresh Next Action list");
        }
    }

    // recursive unfolding of branch
    private void UnfoldBranch(Proxy.Node thisNode) {
        Proxy.Node rootNode = thisNode.getMap().getRoot();
        if (thisNode != rootNode) {
            thisNode.setFolded(false);
            UnfoldBranch(thisNode.getParent());
        }
    }

    // fold to first level
    private void FoldToTop(Proxy.Node thisNode) {
        Proxy.Node rootNode = thisNode.getMap().getRoot();
        def Nodes = ctrl.findAll();
        Nodes.each {
            it.setFolded(true);
        }
        rootNode.setFolded(false);
    }

}

NodeLink nl = new NodeLink(c, mainFrame)
projectPane.getMouseTrackingListeners().each {
    if (it instanceof LinkListener) {
        projectPane.removeMouseTrackingListener(it)
    }
}
projectPane.addMouseTrackingListener(nl);
delegatePane.getMouseTrackingListeners().each {
    if (it instanceof LinkListener) {
        delegatePane.removeMouseTrackingListener(it)
    }
}
delegatePane.addMouseTrackingListener(nl);
contextPane.getMouseTrackingListeners().each {
    if (it instanceof LinkListener) {
        contextPane.removeMouseTrackingListener(it)
    }
}
contextPane.addMouseTrackingListener(nl);
timelinePane.getMouseTrackingListeners().each {
    if (it instanceof LinkListener) {
        timelinePane.removeMouseTrackingListener(it)
    }
}
timelinePane.addMouseTrackingListener(nl);

// on ESC key close frame
mainFrame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), TextUtils.getText("freeplaneGTD.button.cancel"));
mainFrame.getRootPane().getActionMap().put(TextUtils.getText("freeplaneGTD.button.cancel"),
        new CloseAction(mainFrame));

class CloseAction extends AbstractAction {
    JFrame frame

    CloseAction(frame) {
        this.frame = frame
    }

    public void actionPerformed(ActionEvent e) {
        this.frame.setVisible(false);
        this.frame.dispose();
    }
}

System.setProperty("xr.text.aa-smoothing-level", "1")
System.setProperty("xr.text.aa-fontsize-threshhold", "1")
System.setProperty("xr.text.aa-rendering-hint", "RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT")

report.selPane = config.getIntProperty('freeplaneGTD_default_view')
report.filterDone = config.getBooleanProperty('freeplaneGTD_filter_done')
refresh()
projectPane.scrollTo(new Point(0, 0))
delegatePane.scrollTo(new Point(0, 0))
contextPane.scrollTo(new Point(0, 0))
timelinePane.scrollTo(new Point(0, 0))
mainFrame.setLocationRelativeTo(UITools.frame)
mainFrame.visible = true
