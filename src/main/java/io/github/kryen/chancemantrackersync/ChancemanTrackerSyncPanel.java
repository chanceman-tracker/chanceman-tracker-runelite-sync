package io.github.kryen.chancemantrackersync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

class ChancemanTrackerSyncPanel extends PluginPanel
{
    private static final Color PRIMARY_BUTTON_COLOR = new Color(44, 94, 66);
    private static final Color PRIMARY_BUTTON_TEXT_COLOR = new Color(245, 247, 242);

    private final JButton directUploadButton = new JButton("Open tracker");
    private final JButton copyButton = new JButton("Manual copy");
    private final JLabel statusLabel = new JLabel("Ready");
    private final JTextArea previewArea = new JTextArea();

    ChancemanTrackerSyncPanel(ChancemanTrackerSyncPlugin plugin)
    {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel primaryActionColumn = new JPanel();
        primaryActionColumn.setLayout(new BoxLayout(primaryActionColumn, BoxLayout.Y_AXIS));
        primaryActionColumn.setAlignmentX(LEFT_ALIGNMENT);
        directUploadButton.addActionListener(event -> plugin.openTrackerWithData());
        stylePrimaryButton(directUploadButton);
        primaryActionColumn.add(directUploadButton);

        JPanel secondaryActionColumn = new JPanel();
        secondaryActionColumn.setLayout(new BoxLayout(secondaryActionColumn, BoxLayout.Y_AXIS));
        secondaryActionColumn.setAlignmentX(LEFT_ALIGNMENT);
        secondaryActionColumn.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        copyButton.addActionListener(event -> plugin.copyTrackerBlob());
        copyButton.setAlignmentX(LEFT_ALIGNMENT);
        copyButton.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 28));
        secondaryActionColumn.add(copyButton);

        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        previewArea.setEditable(false);
        previewArea.setLineWrap(false);
        previewArea.setWrapStyleWord(false);
        previewArea.setText("No export yet.");

        JScrollPane scrollPane = new JScrollPane(previewArea);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        scrollPane.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 420));

        content.add(primaryActionColumn);
        content.add(secondaryActionColumn);
        content.add(statusLabel);
        content.add(scrollPane);

        add(content, BorderLayout.CENTER);
    }

    void setBusy(String status)
    {
        setActionButtonsEnabled(false);
        statusLabel.setText(status);
    }

    void setResult(String status, String preview)
    {
        setActionButtonsEnabled(true);
        statusLabel.setText(status);
        previewArea.setText(preview);
        previewArea.setCaretPosition(0);
    }

    void setError(String status)
    {
        setActionButtonsEnabled(true);
        statusLabel.setText(status);
    }

    private void setActionButtonsEnabled(boolean enabled)
    {
        directUploadButton.setEnabled(enabled);
        copyButton.setEnabled(enabled);
    }

    private void stylePrimaryButton(JButton button)
    {
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(10);
        button.setMargin(new Insets(10, 12, 10, 12));
        button.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 42));
        button.setBackground(PRIMARY_BUTTON_COLOR);
        button.setForeground(PRIMARY_BUTTON_TEXT_COLOR);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(31, 67, 47)),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        BufferedImage icon = ImageUtil.loadImageResource(ChancemanTrackerSyncPlugin.class, "icon.png");
        if (icon != null)
        {
            Image scaledIcon = icon.getScaledInstance(18, 18, Image.SCALE_SMOOTH);
            button.setIcon(new ImageIcon(scaledIcon));
        }
    }
}
