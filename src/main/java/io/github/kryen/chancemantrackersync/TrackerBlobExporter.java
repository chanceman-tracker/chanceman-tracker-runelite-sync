package io.github.kryen.chancemantrackersync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.AccountType;

final class TrackerBlobExporter
{
    static final int SCHEMA_VERSION = 1;

    ExportResult export(Client client, int hunterRumoursCompleted, Map<String, Map<String, List<Boolean>>> achievementDiaryTaskStates)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            throw new IllegalStateException("You must be logged in before copying a tracker blob.");
        }

        final Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            throw new IllegalStateException("Local player data is not available yet. Try again in a moment.");
        }

        TrackerBlob blob = new TrackerBlob();
        blob.schemaVersion = SCHEMA_VERSION;
        blob.generatedAt = Instant.now().toString();
        blob.pluginVersion = ChancemanTrackerSyncPlugin.PLUGIN_VERSION;
        blob.source = "chanceman-tracker-sync";
        blob.player = buildPlayerBlob(client, localPlayer.getName(), hunterRumoursCompleted, achievementDiaryTaskStates);

        return new ExportResult(blob, buildSummary(blob));
    }

    static int toQuestStatus(QuestState state)
    {
        if (state == null)
        {
            return 0;
        }

        switch (state)
        {
            case FINISHED:
                return 2;
            case IN_PROGRESS:
                return 1;
            case NOT_STARTED:
            default:
                return 0;
        }
    }

    private PlayerBlob buildPlayerBlob(Client client, String playerName, int hunterRumoursCompleted, Map<String, Map<String, List<Boolean>>> achievementDiaryTaskStates)
    {
        PlayerBlob player = new PlayerBlob();
        player.name = playerName;
        player.accountType = normalizeAccountType(client.getAccountType());
        player.levels = collectLevels(client);
        player.quests = collectQuests(client);
        player.questPoints = client.getVarpValue(VarPlayer.QUEST_POINTS);
        player.achievementDiaries = collectAchievementDiaries(client, achievementDiaryTaskStates);
        player.combatAchievements = collectCombatAchievements(client);
        player.slayer = collectSlayer(client);
        player.barbarianTraining = collectBarbarianTraining(client);
        player.hunterRumoursCompleted = Math.max(hunterRumoursCompleted, 0);
        return player;
    }

    private Map<String, Integer> collectLevels(Client client)
    {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            levels.put(skill.getName(), client.getRealSkillLevel(skill));
        }
        return levels;
    }

    private Map<String, Integer> collectQuests(Client client)
    {
        Map<String, Integer> quests = new LinkedHashMap<>();
        for (Quest quest : Quest.values())
        {
            quests.put(quest.getName(), toQuestStatus(quest.getState(client)));
        }
        return quests;
    }

    private Map<String, Map<String, AchievementDiaryTierBlob>> collectAchievementDiaries(Client client, Map<String, Map<String, List<Boolean>>> achievementDiaryTaskStates)
    {
        Map<String, Map<String, AchievementDiaryTierBlob>> diaries = new LinkedHashMap<>();
        diaries.put("Ardougne", buildDiaryRegion(client, Varbits.DIARY_ARDOUGNE_EASY, Varbits.DIARY_ARDOUGNE_MEDIUM, Varbits.DIARY_ARDOUGNE_HARD, Varbits.DIARY_ARDOUGNE_ELITE, achievementDiaryTaskStates.get("Ardougne")));
        diaries.put("Desert", buildDiaryRegion(client, Varbits.DIARY_DESERT_EASY, Varbits.DIARY_DESERT_MEDIUM, Varbits.DIARY_DESERT_HARD, Varbits.DIARY_DESERT_ELITE, achievementDiaryTaskStates.get("Desert")));
        diaries.put("Falador", buildDiaryRegion(client, Varbits.DIARY_FALADOR_EASY, Varbits.DIARY_FALADOR_MEDIUM, Varbits.DIARY_FALADOR_HARD, Varbits.DIARY_FALADOR_ELITE, achievementDiaryTaskStates.get("Falador")));
        diaries.put("Fremennik", buildDiaryRegion(client, Varbits.DIARY_FREMENNIK_EASY, Varbits.DIARY_FREMENNIK_MEDIUM, Varbits.DIARY_FREMENNIK_HARD, Varbits.DIARY_FREMENNIK_ELITE, achievementDiaryTaskStates.get("Fremennik")));
        diaries.put("Kandarin", buildDiaryRegion(client, Varbits.DIARY_KANDARIN_EASY, Varbits.DIARY_KANDARIN_MEDIUM, Varbits.DIARY_KANDARIN_HARD, Varbits.DIARY_KANDARIN_ELITE, achievementDiaryTaskStates.get("Kandarin")));
        diaries.put("Karamja", buildDiaryRegion(client, Varbits.DIARY_KARAMJA_EASY, Varbits.DIARY_KARAMJA_MEDIUM, Varbits.DIARY_KARAMJA_HARD, Varbits.DIARY_KARAMJA_ELITE, achievementDiaryTaskStates.get("Karamja")));
        diaries.put("Kourend & Kebos", buildDiaryRegion(client, Varbits.DIARY_KOUREND_EASY, Varbits.DIARY_KOUREND_MEDIUM, Varbits.DIARY_KOUREND_HARD, Varbits.DIARY_KOUREND_ELITE, achievementDiaryTaskStates.get("Kourend & Kebos")));
        diaries.put("Lumbridge & Draynor", buildDiaryRegion(client, Varbits.DIARY_LUMBRIDGE_EASY, Varbits.DIARY_LUMBRIDGE_MEDIUM, Varbits.DIARY_LUMBRIDGE_HARD, Varbits.DIARY_LUMBRIDGE_ELITE, achievementDiaryTaskStates.get("Lumbridge & Draynor")));
        diaries.put("Morytania", buildDiaryRegion(client, Varbits.DIARY_MORYTANIA_EASY, Varbits.DIARY_MORYTANIA_MEDIUM, Varbits.DIARY_MORYTANIA_HARD, Varbits.DIARY_MORYTANIA_ELITE, achievementDiaryTaskStates.get("Morytania")));
        diaries.put("Varrock", buildDiaryRegion(client, Varbits.DIARY_VARROCK_EASY, Varbits.DIARY_VARROCK_MEDIUM, Varbits.DIARY_VARROCK_HARD, Varbits.DIARY_VARROCK_ELITE, achievementDiaryTaskStates.get("Varrock")));
        diaries.put("Western Provinces", buildDiaryRegion(client, Varbits.DIARY_WESTERN_EASY, Varbits.DIARY_WESTERN_MEDIUM, Varbits.DIARY_WESTERN_HARD, Varbits.DIARY_WESTERN_ELITE, achievementDiaryTaskStates.get("Western Provinces")));
        diaries.put("Wilderness", buildDiaryRegion(client, Varbits.DIARY_WILDERNESS_EASY, Varbits.DIARY_WILDERNESS_MEDIUM, Varbits.DIARY_WILDERNESS_HARD, Varbits.DIARY_WILDERNESS_ELITE, achievementDiaryTaskStates.get("Wilderness")));
        return diaries;
    }

    private Map<String, AchievementDiaryTierBlob> buildDiaryRegion(Client client, int easyVarbit, int mediumVarbit, int hardVarbit, int eliteVarbit, Map<String, List<Boolean>> capturedTaskStates)
    {
        Map<String, AchievementDiaryTierBlob> region = new LinkedHashMap<>();
        region.put("Easy", buildDiaryTier(client, easyVarbit, capturedTaskStates != null ? capturedTaskStates.get("Easy") : null));
        region.put("Medium", buildDiaryTier(client, mediumVarbit, capturedTaskStates != null ? capturedTaskStates.get("Medium") : null));
        region.put("Hard", buildDiaryTier(client, hardVarbit, capturedTaskStates != null ? capturedTaskStates.get("Hard") : null));
        region.put("Elite", buildDiaryTier(client, eliteVarbit, capturedTaskStates != null ? capturedTaskStates.get("Elite") : null));
        return region;
    }

    private AchievementDiaryTierBlob buildDiaryTier(Client client, int varbit, List<Boolean> capturedTaskStates)
    {
        AchievementDiaryTierBlob tier = new AchievementDiaryTierBlob();
        if (capturedTaskStates != null)
        {
            tier.taskStatesAvailable = true;
            tier.tasks = new ArrayList<>(capturedTaskStates);
            tier.complete = capturedTaskStates.stream().allMatch(Boolean::booleanValue);
            return tier;
        }

        tier.complete = client.getVarbitValue(varbit) > 0;
        tier.taskStatesAvailable = false;
        tier.tasks = Collections.emptyList();
        return tier;
    }

    private CombatAchievementsBlob collectCombatAchievements(Client client)
    {
        CombatAchievementsBlob combat = new CombatAchievementsBlob();
        combat.taskCounts = new LinkedHashMap<>();
        combat.taskCounts.put("easy", client.getVarbitValue(Varbits.COMBAT_TASK_EASY));
        combat.taskCounts.put("medium", client.getVarbitValue(Varbits.COMBAT_TASK_MEDIUM));
        combat.taskCounts.put("hard", client.getVarbitValue(Varbits.COMBAT_TASK_HARD));
        combat.taskCounts.put("elite", client.getVarbitValue(Varbits.COMBAT_TASK_ELITE));
        combat.taskCounts.put("master", client.getVarbitValue(Varbits.COMBAT_TASK_MASTER));
        combat.taskCounts.put("grandmaster", client.getVarbitValue(Varbits.COMBAT_TASK_GRANDMASTER));

        combat.tiers = new LinkedHashMap<>();
        combat.tiers.put("easy", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_EASY) > 0);
        combat.tiers.put("medium", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_MEDIUM) > 0);
        combat.tiers.put("hard", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_HARD) > 0);
        combat.tiers.put("elite", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_ELITE) > 0);
        combat.tiers.put("master", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_MASTER) > 0);
        combat.tiers.put("grandmaster", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_GRANDMASTER) > 0);

        combat.count = 0;
        for (int count : combat.taskCounts.values())
        {
            combat.count += count;
        }

        return combat;
    }

    private SlayerBlob collectSlayer(Client client)
    {
        SlayerBlob slayer = new SlayerBlob();
        slayer.points = client.getVarbitValue(Varbits.SLAYER_POINTS);
        slayer.taskStreak = client.getVarbitValue(Varbits.SLAYER_TASK_STREAK);

        SlayerUnlocksBlob unlocks = new SlayerUnlocksBlob();
        unlocks.biggerAndBadder = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS) > 0;
        unlocks.likeABoss = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_BOSSES) > 0;
        unlocks.superiorsEnabled = client.getVarbitValue(Varbits.SUPERIOR_ENABLED) > 0;
        unlocks.superiorsToggledOff = client.getVarbitValue(VarbitID.SLAYER_TOGGLEOFF_SUPERIORMOBS) > 0;
        unlocks.aviansies = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_AVIANSIES) > 0;
        unlocks.basilisks = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_BASILISK) > 0;
        unlocks.fossilIslandWyverns = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_FOSSILWYVERNBLOCK) > 0;
        unlocks.grotesqueGuardians = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_GROTESQUEKILLS) > 0;
        unlocks.lizardmen = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_LIZARDMEN) > 0;
        unlocks.mithrilDragons = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_MITHRILDRAGONS) > 0;
        unlocks.slayerRingStorage = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_STORAGE) > 0;
        unlocks.tzHaar = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_TZHAAR) > 0;
        unlocks.vampyres = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_VAMPYRES) > 0;
        unlocks.warpedCreatures = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_WARPED_CREATURES) > 0;
        unlocks.wildernessExtraTasks = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_WILDY_EXTRATASKS) > 0;
        slayer.unlocks = unlocks;

        slayer.rawUnlockVarps = new LinkedHashMap<>();
        slayer.rawUnlockVarps.put("SLAYER_UNLOCK_1", client.getVarpValue(VarPlayer.SLAYER_UNLOCK_1));
        slayer.rawUnlockVarps.put("SLAYER_UNLOCK_2", client.getVarpValue(VarPlayer.SLAYER_UNLOCK_2));

        slayer.namedVars = new LinkedHashMap<>();
        slayer.namedVars.put("SUPERIOR_ENABLED", client.getVarbitValue(Varbits.SUPERIOR_ENABLED));
        slayer.namedVars.put("SLAYER_POINTS", client.getVarbitValue(Varbits.SLAYER_POINTS));
        slayer.namedVars.put("SLAYER_TASK_STREAK", client.getVarbitValue(Varbits.SLAYER_TASK_STREAK));
        slayer.namedVars.put("SLAYER_TASK_BOSS", client.getVarbitValue(Varbits.SLAYER_TASK_BOSS));
        slayer.namedVars.put("SLAYER_UNLOCK_SUPERIORMOBS", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS));
        slayer.namedVars.put("SLAYER_TOGGLEOFF_SUPERIORMOBS", client.getVarbitValue(VarbitID.SLAYER_TOGGLEOFF_SUPERIORMOBS));
        slayer.namedVars.put("SLAYER_UNLOCK_BOSSES", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_BOSSES));
        slayer.namedVars.put("SLAYER_UNLOCK_AVIANSIES", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_AVIANSIES));
        slayer.namedVars.put("SLAYER_UNLOCK_BASILISK", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_BASILISK));
        slayer.namedVars.put("SLAYER_UNLOCK_FOSSILWYVERNBLOCK", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_FOSSILWYVERNBLOCK));
        slayer.namedVars.put("SLAYER_UNLOCK_GROTESQUEKILLS", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_GROTESQUEKILLS));
        slayer.namedVars.put("SLAYER_UNLOCK_LIZARDMEN", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_LIZARDMEN));
        slayer.namedVars.put("SLAYER_UNLOCK_MITHRILDRAGONS", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_MITHRILDRAGONS));
        slayer.namedVars.put("SLAYER_UNLOCK_STORAGE", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_STORAGE));
        slayer.namedVars.put("SLAYER_UNLOCK_TZHAAR", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_TZHAAR));
        slayer.namedVars.put("SLAYER_UNLOCK_VAMPYRES", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_VAMPYRES));
        slayer.namedVars.put("SLAYER_UNLOCK_WARPED_CREATURES", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_WARPED_CREATURES));
        slayer.namedVars.put("SLAYER_UNLOCK_WILDY_EXTRATASKS", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_WILDY_EXTRATASKS));
        return slayer;
    }

    private BarbarianTrainingBlob collectBarbarianTraining(Client client)
    {
        BarbarianTrainingBlob training = new BarbarianTrainingBlob();
        training.namedVars = new LinkedHashMap<>();
        training.namedVars.put("BRUT_FISHING_S", client.getVarbitValue(VarbitID.BRUT_FISHING_S));
        training.namedVars.put("BRUT_FISHING_R", client.getVarbitValue(VarbitID.BRUT_FISHING_R));
        training.namedVars.put("BRUT_FIRE", client.getVarbitValue(VarbitID.BRUT_FIRE));
        training.namedVars.put("BRUT_TRACKER", client.getVarbitValue(VarbitID.BRUT_TRACKER));
        training.namedVars.put("BRUT_HERB_POTION", client.getVarbitValue(VarbitID.BRUT_HERB_POTION));
        training.namedVars.put("BRUT_SMITH_SPEAR", client.getVarbitValue(VarbitID.BRUT_SMITH_SPEAR));
        training.namedVars.put("BRUT_SMITH_HASTA", client.getVarbitValue(VarbitID.BRUT_SMITH_HASTA));
        training.namedVars.put("BRUT_CRAFT_SHIP", client.getVarbitValue(VarbitID.BRUT_CRAFT_SHIP));
        training.namedVars.put("BRUT_TRIED_POOL", client.getVarbitValue(VarbitID.BRUT_TRIED_POOL));
        training.namedVars.put("BRUT_TRIED_DOOR", client.getVarbitValue(VarbitID.BRUT_TRIED_DOOR));
        training.namedVars.put("BRUT_PRAYER_TOTAL", client.getVarbitValue(VarbitID.BRUT_PRAYER_TOTAL));
        training.namedVars.put("BRUT_ALL_CERT_DONE", client.getVarbitValue(VarbitID.BRUT_ALL_CERT_DONE));
        training.namedVars.put("BRUT_FARMING_PLANTING", client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING));
        training.namedVars.put("BRUT_FARMING_SMASHING", client.getVarbitValue(VarbitID.BRUT_FARMING_SMASHING));
        training.namedVars.put("BRUT_MINIQUEST", client.getVarbitValue(VarbitID.BRUT_MINIQUEST));
        training.namedVars.put("BRUT_SMASH_POTS_AUTOMATICALLY", client.getVarbitValue(VarbitID.BRUT_SMASH_POTS_AUTOMATICALLY));
        training.namedVars.put("BRUT_DIBBER_FAILED_ATTEMPTS", client.getVarbitValue(VarbitID.BRUT_DIBBER_FAILED_ATTEMPTS));
        return training;
    }

    Map<String, Integer> buildDebugNamedVars(Client client)
    {
        Map<String, Integer> namedVars = new LinkedHashMap<>();
        namedVars.put("ACCOUNT_TYPE", client.getVarbitValue(Varbits.ACCOUNT_TYPE));
        namedVars.put("SUPERIOR_ENABLED", client.getVarbitValue(Varbits.SUPERIOR_ENABLED));
        namedVars.put("SLAYER_POINTS", client.getVarbitValue(Varbits.SLAYER_POINTS));
        namedVars.put("SLAYER_TASK_STREAK", client.getVarbitValue(Varbits.SLAYER_TASK_STREAK));
        namedVars.put("SLAYER_TASK_BOSS", client.getVarbitValue(Varbits.SLAYER_TASK_BOSS));
        namedVars.put("COMBAT_TASK_EASY", client.getVarbitValue(Varbits.COMBAT_TASK_EASY));
        namedVars.put("COMBAT_TASK_MEDIUM", client.getVarbitValue(Varbits.COMBAT_TASK_MEDIUM));
        namedVars.put("COMBAT_TASK_HARD", client.getVarbitValue(Varbits.COMBAT_TASK_HARD));
        namedVars.put("COMBAT_TASK_ELITE", client.getVarbitValue(Varbits.COMBAT_TASK_ELITE));
        namedVars.put("COMBAT_TASK_MASTER", client.getVarbitValue(Varbits.COMBAT_TASK_MASTER));
        namedVars.put("COMBAT_TASK_GRANDMASTER", client.getVarbitValue(Varbits.COMBAT_TASK_GRANDMASTER));
        namedVars.put("SLAYER_UNLOCK_SUPERIORMOBS", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS));
        namedVars.put("SLAYER_TOGGLEOFF_SUPERIORMOBS", client.getVarbitValue(VarbitID.SLAYER_TOGGLEOFF_SUPERIORMOBS));
        namedVars.put("SLAYER_UNLOCK_BOSSES", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_BOSSES));
        namedVars.put("SLAYER_UNLOCK_AVIANSIES", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_AVIANSIES));
        namedVars.put("SLAYER_UNLOCK_BASILISK", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_BASILISK));
        namedVars.put("SLAYER_UNLOCK_FOSSILWYVERNBLOCK", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_FOSSILWYVERNBLOCK));
        namedVars.put("SLAYER_UNLOCK_GROTESQUEKILLS", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_GROTESQUEKILLS));
        namedVars.put("SLAYER_UNLOCK_LIZARDMEN", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_LIZARDMEN));
        namedVars.put("SLAYER_UNLOCK_MITHRILDRAGONS", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_MITHRILDRAGONS));
        namedVars.put("SLAYER_UNLOCK_STORAGE", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_STORAGE));
        namedVars.put("SLAYER_UNLOCK_TZHAAR", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_TZHAAR));
        namedVars.put("SLAYER_UNLOCK_VAMPYRES", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_VAMPYRES));
        namedVars.put("SLAYER_UNLOCK_WARPED_CREATURES", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_WARPED_CREATURES));
        namedVars.put("SLAYER_UNLOCK_WILDY_EXTRATASKS", client.getVarbitValue(VarbitID.SLAYER_UNLOCK_WILDY_EXTRATASKS));
        namedVars.put("SLAYER_UNLOCK_1", client.getVarpValue(VarPlayer.SLAYER_UNLOCK_1));
        namedVars.put("SLAYER_UNLOCK_2", client.getVarpValue(VarPlayer.SLAYER_UNLOCK_2));
        namedVars.put("BRUT_FISHING_S", client.getVarbitValue(VarbitID.BRUT_FISHING_S));
        namedVars.put("BRUT_FISHING_R", client.getVarbitValue(VarbitID.BRUT_FISHING_R));
        namedVars.put("BRUT_FIRE", client.getVarbitValue(VarbitID.BRUT_FIRE));
        namedVars.put("BRUT_TRACKER", client.getVarbitValue(VarbitID.BRUT_TRACKER));
        namedVars.put("BRUT_HERB_POTION", client.getVarbitValue(VarbitID.BRUT_HERB_POTION));
        namedVars.put("BRUT_SMITH_SPEAR", client.getVarbitValue(VarbitID.BRUT_SMITH_SPEAR));
        namedVars.put("BRUT_SMITH_HASTA", client.getVarbitValue(VarbitID.BRUT_SMITH_HASTA));
        namedVars.put("BRUT_CRAFT_SHIP", client.getVarbitValue(VarbitID.BRUT_CRAFT_SHIP));
        namedVars.put("BRUT_TRIED_POOL", client.getVarbitValue(VarbitID.BRUT_TRIED_POOL));
        namedVars.put("BRUT_TRIED_DOOR", client.getVarbitValue(VarbitID.BRUT_TRIED_DOOR));
        namedVars.put("BRUT_PRAYER_TOTAL", client.getVarbitValue(VarbitID.BRUT_PRAYER_TOTAL));
        namedVars.put("BRUT_ALL_CERT_DONE", client.getVarbitValue(VarbitID.BRUT_ALL_CERT_DONE));
        namedVars.put("BRUT_FARMING_PLANTING", client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING));
        namedVars.put("BRUT_FARMING_SMASHING", client.getVarbitValue(VarbitID.BRUT_FARMING_SMASHING));
        namedVars.put("BRUT_MINIQUEST", client.getVarbitValue(VarbitID.BRUT_MINIQUEST));
        namedVars.put("BRUT_SMASH_POTS_AUTOMATICALLY", client.getVarbitValue(VarbitID.BRUT_SMASH_POTS_AUTOMATICALLY));
        namedVars.put("BRUT_DIBBER_FAILED_ATTEMPTS", client.getVarbitValue(VarbitID.BRUT_DIBBER_FAILED_ATTEMPTS));
        namedVars.put("QUEST_POINTS", client.getVarpValue(VarPlayer.QUEST_POINTS));
        return namedVars;
    }

    private String normalizeAccountType(AccountType accountType)
    {
        if (accountType == null)
        {
            return "unknown";
        }

        switch (accountType)
        {
            case IRONMAN:
                return "ironman";
            case ULTIMATE_IRONMAN:
                return "ultimate_ironman";
            case HARDCORE_IRONMAN:
                return "hardcore_ironman";
            case GROUP_IRONMAN:
                return "group_ironman";
            case HARDCORE_GROUP_IRONMAN:
                return "hardcore_group_ironman";
            case NORMAL:
            default:
                return "normal";
        }
    }

    private String buildSummary(TrackerBlob blob)
    {
        List<String> lines = new ArrayList<>();
        lines.add("Player: " + blob.player.name);
        lines.add("Account type: " + blob.player.accountType);
        lines.add("Quest points: " + blob.player.questPoints);
        lines.add("Combat achievement tasks counted: " + blob.player.combatAchievements.count);
        lines.add("Easy combat tier complete: " + blob.player.combatAchievements.tiers.get("easy"));
        lines.add("Slayer points: " + blob.player.slayer.points);
        lines.add("Superiors unlocked: " + blob.player.slayer.unlocks.biggerAndBadder);
        lines.add("Boss tasks unlocked: " + blob.player.slayer.unlocks.likeABoss);
        lines.add("Hunter rumours completed: " + blob.player.hunterRumoursCompleted);
        return String.join(System.lineSeparator(), lines);
    }

    static final class ExportResult
    {
        final TrackerBlob blob;
        final String summary;

        ExportResult(TrackerBlob blob, String summary)
        {
            this.blob = blob;
            this.summary = summary;
        }
    }

    static final class TrackerBlob
    {
        int schemaVersion;
        String generatedAt;
        String pluginVersion;
        String source;
        PlayerBlob player;
    }

    static final class PlayerBlob
    {
        String name;
        String accountType;
        Map<String, Integer> levels;
        Map<String, Integer> quests;
        int questPoints;
        Map<String, Map<String, AchievementDiaryTierBlob>> achievementDiaries;
        CombatAchievementsBlob combatAchievements;
        SlayerBlob slayer;
        BarbarianTrainingBlob barbarianTraining;
        int hunterRumoursCompleted;
    }

    static final class AchievementDiaryTierBlob
    {
        boolean complete;
        boolean taskStatesAvailable;
        List<Boolean> tasks;
    }

    static final class CombatAchievementsBlob
    {
        int count;
        Map<String, Integer> taskCounts;
        Map<String, Boolean> tiers;
    }

    static final class SlayerBlob
    {
        int points;
        int taskStreak;
        SlayerUnlocksBlob unlocks;
        Map<String, Integer> rawUnlockVarps;
        Map<String, Integer> namedVars;
    }

    static final class SlayerUnlocksBlob
    {
        Boolean biggerAndBadder;
        Boolean likeABoss;
        Boolean superiorsEnabled;
        Boolean superiorsToggledOff;
        Boolean aviansies;
        Boolean basilisks;
        Boolean fossilIslandWyverns;
        Boolean grotesqueGuardians;
        Boolean lizardmen;
        Boolean mithrilDragons;
        Boolean slayerRingStorage;
        Boolean tzHaar;
        Boolean vampyres;
        Boolean warpedCreatures;
        Boolean wildernessExtraTasks;
    }

    static final class BarbarianTrainingBlob
    {
        Map<String, Integer> namedVars;
    }

}