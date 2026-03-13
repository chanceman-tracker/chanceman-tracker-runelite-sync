package io.github.kryen.chancemantrackersync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ChancemanTrackerSyncPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ChancemanTrackerSyncPlugin.class);
        RuneLite.main(args);
    }
}
