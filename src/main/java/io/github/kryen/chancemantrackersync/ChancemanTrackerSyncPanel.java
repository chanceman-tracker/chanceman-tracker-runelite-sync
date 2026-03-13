package io.github.kryen.chancemantrackersync;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

class ChancemanTrackerSyncPanel extends PluginPanel
{
    private final JButton copyButton = new JButton("Copy tracker blob");
    private final JButton openSiteButton = new JButton("Open upload page");
    private final JLabel statusLabel = new JLabel("Ready");
    private final JTextArea previewArea = new JTextArea();

    ChancemanTrackerSyncPanel(ChancemanTrackerSyncPlugin plugin)
    {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel intro = new JLabel("Copy a local JSON blob for chanceman-tracker.github.io.");
        intro.setAlignmentX(LEFT_ALIGNMENT);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionRow.setAlignmentX(LEFT_ALIGNMENT);
        copyButton.addActionListener(event -> plugin.copyTrackerBlob());
        openSiteButton.addActionListener(event -> LinkBrowser.browse("https://chanceman-tracker.github.io/upload"));
        actionRow.add(copyButton);
        actionRow.add(openSiteButton);

        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        previewArea.setEditable(false);
        previewArea.setLineWrap(false);
        previewArea.setWrapStyleWord(false);
        previewArea.setText("No export yet.");

        JScrollPane scrollPane = new JScrollPane(previewArea);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        scrollPane.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 420));

        content.add(intro);
        content.add(actionRow);
        content.add(statusLabel);
        content.add(scrollPane);

        add(content, BorderLayout.CENTER);
    }

    void setBusy(String status)
    {
        copyButton.setEnabled(false);
        statusLabel.setText(status);
    }

    void setResult(String status, String preview)
    {
        copyButton.setEnabled(true);
        statusLabel.setText(status);
        previewArea.setText(preview);
        previewArea.setCaretPosition(0);
    }

    void setError(String status)
    {
        copyButton.setEnabled(true);
        statusLabel.setText(status);
    }
}
