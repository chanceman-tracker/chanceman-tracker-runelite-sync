package io.github.kryen.chancemantrackersync;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;

final class AchievementDiaryVarResolver
{
    private static final String SPECS_RESOURCE = "/achievement_diary_var_specs.json";
    private static final Type SPECS_TYPE = new TypeToken<LinkedHashMap<String, LinkedHashMap<String, TierSpec>>>() { }.getType();
    private static final String[] DIARY_ORDER = {
        "Ardougne",
        "Desert",
        "Falador",
        "Fremennik",
        "Kandarin",
        "Karamja",
        "Kourend & Kebos",
        "Lumbridge & Draynor",
        "Morytania",
        "Varrock",
        "Western Provinces",
        "Wilderness"
    };
    private static final String[] TIER_ORDER = {"Easy", "Medium", "Hard", "Elite"};

    private final Map<String, Map<String, TierSpec>> specs;

    @Inject
    AchievementDiaryVarResolver(Gson gson)
    {
        this.specs = loadSpecs(gson);
    }

    Map<String, Map<String, TrackerBlobExporter.AchievementDiaryTierBlob>> resolve(Client client)
    {
        Map<String, Map<String, TrackerBlobExporter.AchievementDiaryTierBlob>> diaries = new LinkedHashMap<>();
        for (String diaryName : DIARY_ORDER)
        {
            Map<String, TierSpec> diarySpec = specs.get(diaryName);
            if (diarySpec == null)
            {
                throw new IllegalStateException("Missing achievement diary definition for " + diaryName);
            }

            Map<String, TrackerBlobExporter.AchievementDiaryTierBlob> tiers = new LinkedHashMap<>();
            for (String tierName : TIER_ORDER)
            {
                tiers.put(tierName, buildTierBlob(client, diaryName, tierName, diarySpec.get(tierName)));
            }
            diaries.put(diaryName, tiers);
        }
        return diaries;
    }

    private TrackerBlobExporter.AchievementDiaryTierBlob buildTierBlob(Client client, String diaryName, String tierName, TierSpec spec)
    {
        if (spec == null || spec.complete == null || spec.tasks == null)
        {
            throw new IllegalStateException("Missing achievement diary spec for " + diaryName + " / " + tierName);
        }

        TrackerBlobExporter.AchievementDiaryTierBlob tier = new TrackerBlobExporter.AchievementDiaryTierBlob();
        tier.complete = resolveSpec(spec.complete, client, diaryName, tierName, -1);
        tier.taskStatesAvailable = true;
        tier.tasks = new ArrayList<>(spec.tasks.size());
        for (int taskIndex = 0; taskIndex < spec.tasks.size(); taskIndex++)
        {
            tier.tasks.add(resolveSpec(spec.tasks.get(taskIndex), client, diaryName, tierName, taskIndex));
        }
        return tier;
    }

    private boolean resolveSpec(VarSpec spec, Client client, String diaryName, String tierName, int taskIndex)
    {
        if (spec == null || spec.type == null)
        {
            return false;
        }

        if ("Desert".equals(diaryName) && "Medium".equals(tierName) && taskIndex == 10)
        {
            return isBitSet(client.getVarpValue(1199), 9) || isBitSet(client.getVarpValue(1198), 22);
        }

        switch (spec.type)
        {
            case "bits":
                return spec.value != null && client.getVarbitValue(spec.varId) == spec.value;
            case "player":
                return spec.offset != null && isBitSet(client.getVarpValue(spec.varId), spec.offset);
            default:
                return false;
        }
    }

    private boolean isBitSet(int value, int offset)
    {
        return ((value >>> offset) & 1) != 0;
    }

    private Map<String, Map<String, TierSpec>> loadSpecs(Gson gson)
    {
        InputStream stream = AchievementDiaryVarResolver.class.getResourceAsStream(SPECS_RESOURCE);
        if (stream == null)
        {
            throw new IllegalStateException("Missing achievement diary specs resource: " + SPECS_RESOURCE);
        }

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
        {
            Map<String, Map<String, TierSpec>> parsed = gson.fromJson(reader, SPECS_TYPE);
            if (parsed == null || parsed.isEmpty())
            {
                throw new IllegalStateException("Achievement diary specs resource is empty");
            }
            return parsed;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Failed to load achievement diary specs", ex);
        }
    }

    static final class TierSpec
    {
        VarSpec complete;
        List<VarSpec> tasks;
    }

    static final class VarSpec
    {
        String type;
        Integer value;
        Integer offset;
        @SerializedName("var_id")
        int varId;
    }
}
