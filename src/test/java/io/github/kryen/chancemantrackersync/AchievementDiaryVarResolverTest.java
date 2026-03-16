package io.github.kryen.chancemantrackersync;

import com.google.gson.Gson;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import org.junit.Assert;
import org.junit.Test;

public class AchievementDiaryVarResolverTest
{
    private final AchievementDiaryVarResolver resolver = new AchievementDiaryVarResolver(new Gson());

    @Test
    public void resolvesAllDiariesWithTaskListsAvailable()
    {
        Map<String, Map<String, TrackerBlobExporter.AchievementDiaryTierBlob>> diaries =
            resolver.resolve(client(Collections.emptyMap(), Collections.emptyMap()));

        Assert.assertEquals(12, diaries.size());
        Assert.assertEquals(4, diaries.get("Ardougne").size());
        Assert.assertEquals(10, diaries.get("Ardougne").get("Easy").tasks.size());
        Assert.assertEquals(19, diaries.get("Karamja").get("Medium").tasks.size());
        Assert.assertEquals(13, diaries.get("Kourend & Kebos").get("Medium").tasks.size());

        for (Map<String, TrackerBlobExporter.AchievementDiaryTierBlob> tiers : diaries.values())
        {
            for (TrackerBlobExporter.AchievementDiaryTierBlob tier : tiers.values())
            {
                Assert.assertTrue(tier.taskStatesAvailable);
                Assert.assertFalse(tier.complete);
                for (Boolean task : tier.tasks)
                {
                    Assert.assertFalse(task);
                }
            }
        }
    }

    @Test
    public void resolvesVarpBackedTasksAndTierCompletion()
    {
        Map<Integer, Integer> varps = new LinkedHashMap<>();
        varps.put(1196, bitmask(0, 12));

        Map<Integer, Integer> varbits = new LinkedHashMap<>();
        varbits.put(4499, 1);

        TrackerBlobExporter.AchievementDiaryTierBlob tier = resolveTier("Ardougne", "Easy", varps, varbits);

        Assert.assertTrue(tier.complete);
        Assert.assertEquals(10, tier.tasks.size());
        Assert.assertTrue(tier.tasks.get(0));
        Assert.assertTrue(tier.tasks.get(9));
        Assert.assertFalse(tier.tasks.get(1));
        Assert.assertFalse(tier.tasks.get(8));
    }

    @Test
    public void resolvesVarbitBackedKaramjaTasksUsingExpectedValues()
    {
        Map<Integer, Integer> varbits = new LinkedHashMap<>();
        varbits.put(3577, 1);
        varbits.put(3566, 5);
        varbits.put(3573, 5);
        varbits.put(3575, 1);

        TrackerBlobExporter.AchievementDiaryTierBlob tier = resolveTier("Karamja", "Easy", Collections.emptyMap(), varbits);

        Assert.assertTrue(tier.complete);
        Assert.assertEquals(10, tier.tasks.size());
        Assert.assertTrue(tier.tasks.get(0));
        Assert.assertTrue(tier.tasks.get(7));
        Assert.assertTrue(tier.tasks.get(9));
        Assert.assertFalse(tier.tasks.get(1));
    }

    @Test
    public void resolvesDesertMediumSpecialCaseFromEitherVarp()
    {
        Map<Integer, Integer> nonIronmanVarps = new LinkedHashMap<>();
        nonIronmanVarps.put(1198, bitmask(22));

        TrackerBlobExporter.AchievementDiaryTierBlob nonIronmanTier =
            resolveTier("Desert", "Medium", nonIronmanVarps, Collections.emptyMap());

        Assert.assertTrue(nonIronmanTier.tasks.get(10));

        Map<Integer, Integer> ironmanVarps = new LinkedHashMap<>();
        ironmanVarps.put(1199, bitmask(9));

        TrackerBlobExporter.AchievementDiaryTierBlob ironmanTier =
            resolveTier("Desert", "Medium", ironmanVarps, Collections.emptyMap());

        Assert.assertTrue(ironmanTier.tasks.get(10));
    }

    @Test
    public void resolvesKourendAndKebosDedicatedVarps()
    {
        Map<Integer, Integer> varps = new LinkedHashMap<>();
        varps.put(2085, bitmask(25, 13, 24));

        Map<Integer, Integer> varbits = new LinkedHashMap<>();
        varbits.put(7930, 1);

        TrackerBlobExporter.AchievementDiaryTierBlob tier = resolveTier("Kourend & Kebos", "Medium", varps, varbits);

        Assert.assertTrue(tier.complete);
        Assert.assertEquals(13, tier.tasks.size());
        Assert.assertTrue(tier.tasks.get(0));
        Assert.assertTrue(tier.tasks.get(1));
        Assert.assertTrue(tier.tasks.get(12));
        Assert.assertFalse(tier.tasks.get(2));
    }

    @Test
    public void resolvesBitThirtyOneWithoutSignIssues()
    {
        Map<Integer, Integer> varps = new LinkedHashMap<>();
        varps.put(1196, bitmask(31));

        TrackerBlobExporter.AchievementDiaryTierBlob tier =
            resolveTier("Ardougne", "Hard", varps, Collections.emptyMap());

        Assert.assertTrue(tier.tasks.get(5));
        Assert.assertFalse(tier.tasks.get(4));
    }

    private TrackerBlobExporter.AchievementDiaryTierBlob resolveTier(String diaryName, String tierName,
        Map<Integer, Integer> varps, Map<Integer, Integer> varbits)
    {
        return resolver.resolve(client(varps, varbits)).get(diaryName).get(tierName);
    }

    private Client client(Map<Integer, Integer> varps, Map<Integer, Integer> varbits)
    {
        return (Client) Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[] {Client.class},
            (proxy, method, args) ->
            {
                String methodName = method.getName();
                if ("getVarpValue".equals(methodName))
                {
                    return varps.getOrDefault((Integer) args[0], 0);
                }
                if ("getVarbitValue".equals(methodName))
                {
                    return varbits.getOrDefault((Integer) args[0], 0);
                }
                if ("equals".equals(methodName))
                {
                    return proxy == args[0];
                }
                if ("hashCode".equals(methodName))
                {
                    return System.identityHashCode(proxy);
                }
                if ("toString".equals(methodName))
                {
                    return "AchievementDiaryVarResolverTestClient";
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private int bitmask(int... offsets)
    {
        int value = 0;
        for (int offset : offsets)
        {
            value |= 1 << offset;
        }
        return value;
    }

    private Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive())
        {
            return null;
        }
        if (type == boolean.class)
        {
            return false;
        }
        if (type == byte.class)
        {
            return (byte) 0;
        }
        if (type == char.class)
        {
            return (char) 0;
        }
        if (type == short.class)
        {
            return (short) 0;
        }
        if (type == int.class)
        {
            return 0;
        }
        if (type == long.class)
        {
            return 0L;
        }
        if (type == float.class)
        {
            return 0F;
        }
        if (type == double.class)
        {
            return 0D;
        }
        return null;
    }
}
