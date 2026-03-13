package io.github.kryen.chancemantrackersync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
    name = "Chanceman Tracker Sync",
    description = "Copies a tracker blob for chanceman-tracker.github.io",
    tags = {"chanceman", "tracker", "sync", "clipboard"}
)
public class ChancemanTrackerSyncPlugin extends Plugin
{
    static final String PLUGIN_VERSION = "0.1.0-SNAPSHOT";

    private static final Pattern HUNTER_RUMOUR_KC_PATTERN =
        Pattern.compile("You have completed <col=[0-9a-f]{6}>([0-9,]+)</col> rumours? for the Hunter Guild\\.");

    private final Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private final TrackerBlobExporter exporter = new TrackerBlobExporter();
    private final AchievementDiaryCaptureStore achievementDiaryCaptureStore = new AchievementDiaryCaptureStore();

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ChancemanTrackerSyncConfig config;

    @Inject
    private ConfigManager configManager;

    private NavigationButton navigationButton;
    private ChancemanTrackerSyncPanel panel;

    @Override
    protected void startUp()
    {
        panel = new ChancemanTrackerSyncPanel(this);
        BufferedImage icon = ImageUtil.loadImageResource(ChancemanTrackerSyncPlugin.class, "icon.png");
        navigationButton = NavigationButton.builder()
            .tooltip("Chanceman Tracker Sync")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navigationButton);
        log.debug("Chanceman Tracker Sync started");
    }

    @Override
    protected void shutDown()
    {
        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }
        panel = null;
        log.debug("Chanceman Tracker Sync stopped");
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        Matcher matcher = HUNTER_RUMOUR_KC_PATTERN.matcher(event.getMessage());
        if (!matcher.find())
        {
            return;
        }

        int kc = Integer.parseInt(matcher.group(1).replace(",", ""));
        configManager.setConfiguration("chancemantrackersync", "hunterRumoursCompleted", kc);
        log.debug("Captured Hunter Rumours count: {}", kc);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() != InterfaceID.JOURNALSCROLL)
        {
            return;
        }

        Widget textLayer = client.getWidget(InterfaceID.Journalscroll.TEXTLAYER);
        if (textLayer == null)
        {
            return;
        }

        Widget[] children = textLayer.getStaticChildren();
        if (children == null || children.length == 0)
        {
            return;
        }

        Widget screenTitle = client.getWidget(InterfaceID.Journalscroll.TITLE);
        String screenTitleText = screenTitle != null ? Text.removeTags(screenTitle.getText()) : "";
        String journalTitleText = children[0] != null ? Text.removeTags(children[0].getText()) : "";

        List<AchievementDiaryCaptureStore.Capture> captures = achievementDiaryCaptureStore.parseCaptures(children);
        if (captures.isEmpty())
        {
            if (config.developerLogging() && !journalTitleText.isEmpty())
            {
                List<String> previewRows = new ArrayList<>();
                for (int index = 1; index < children.length && previewRows.size() < 25; index++)
                {
                    Widget child = children[index];
                    if (child == null)
                    {
                        continue;
                    }

                    String rawText = child.getText();
                    String plainText = Text.removeTags(rawText == null ? "" : rawText).trim();
                    if (plainText.isEmpty())
                    {
                        continue;
                    }

                    previewRows.add((rawText != null && rawText.contains("<str>") ? "[x] " : "[ ] ") + plainText);
                }

                log.debug("No diary capture match for journal scroll. screenTitle='{}', journalTitle='{}', rows={}, previewRows={}",
                    screenTitleText, journalTitleText, children.length, previewRows);
            }
            return;
        }

        achievementDiaryCaptureStore.storeCaptures(configManager, captures);
        log.debug("Captured achievement diary task states for {}. screenTitle='{}', journalTitle='{}'",
            captures.stream()
                .map(capture -> capture.getDiaryName() + " " + capture.getTierName() + " (" + capture.getCapturedTaskCount() + ")")
                .collect(java.util.stream.Collectors.joining(", ")),
            screenTitleText,
            journalTitleText);
    }

    void copyTrackerBlob()
    {
        if (panel != null)
        {
            panel.setBusy("Collecting tracker data...");
        }

        clientThread.invoke(() ->
        {
            try
            {
                Map<String, Map<String, java.util.List<Boolean>>> diaryTaskStates =
                    achievementDiaryCaptureStore.loadStoredTaskStates(configManager);
                TrackerBlobExporter.ExportResult exportResult = exporter.export(
                    client,
                    config.developerLogging(),
                    config.hunterRumoursCompleted(),
                    diaryTaskStates
                );
                String json = gson.toJson(exportResult.blob);
                copyToClipboard(json);

                if (config.developerLogging())
                {
                    log.debug("Tracker blob:{}{}", System.lineSeparator(), json);
                }

                SwingUtilities.invokeLater(() ->
                {
                    if (panel != null)
                    {
                        panel.setResult("Copied tracker blob to clipboard.", exportResult.summary + System.lineSeparator() + System.lineSeparator() + json);
                    }
                });
            }
            catch (Exception ex)
            {
                log.warn("Failed to export tracker blob", ex);
                SwingUtilities.invokeLater(() ->
                {
                    if (panel != null)
                    {
                        panel.setError(ex.getMessage() != null ? ex.getMessage() : "Failed to export tracker blob.");
                    }
                });
            }
        });
    }

    private void copyToClipboard(String text)
    {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @Provides
    ChancemanTrackerSyncConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ChancemanTrackerSyncConfig.class);
    }
}