package io.github.kryen.chancemantrackersync;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

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

    private final TrackerBlobExporter exporter = new TrackerBlobExporter();

    @Inject
    private Gson gson;

    private Gson prettyGson;

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
        prettyGson = gson.newBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

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
                TrackerBlobExporter.ExportResult exportResult = exporter.export(
                    client,
                    config.hunterRumoursCompleted()
                );
                String json = prettyGson.toJson(exportResult.blob);
                copyToClipboard(json);

                if (log.isDebugEnabled())
                {
                    log.debug("Tracker blob:{}{}", System.lineSeparator(), json);
                    log.debug("Named var snapshot:{}{}", System.lineSeparator(), prettyGson.toJson(exporter.buildDebugNamedVars(client)));
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