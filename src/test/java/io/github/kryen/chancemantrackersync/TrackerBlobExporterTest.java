package io.github.kryen.chancemantrackersync;

import net.runelite.api.QuestState;
import org.junit.Assert;
import org.junit.Test;

public class TrackerBlobExporterTest
{
    @Test
    public void toQuestStatusMapsQuestStates()
    {
        Assert.assertEquals(0, TrackerBlobExporter.toQuestStatus(QuestState.NOT_STARTED));
        Assert.assertEquals(1, TrackerBlobExporter.toQuestStatus(QuestState.IN_PROGRESS));
        Assert.assertEquals(2, TrackerBlobExporter.toQuestStatus(QuestState.FINISHED));
        Assert.assertEquals(0, TrackerBlobExporter.toQuestStatus(null));
    }
}
