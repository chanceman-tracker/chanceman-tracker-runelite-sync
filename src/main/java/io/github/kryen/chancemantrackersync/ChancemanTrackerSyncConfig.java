package io.github.kryen.chancemantrackersync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("chancemantrackersync")
public interface ChancemanTrackerSyncConfig extends Config
{
    @ConfigItem(
        keyName = "hunterRumoursCompleted",
        name = "Hunter rumours completed",
        description = "Last Hunter Guild rumours total captured from chat",
        hidden = true
    )
    default int hunterRumoursCompleted()
    {
        return 0;
    }
}