package io.github.kryen.chancemantrackersync;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

final class AchievementDiaryCaptureStore
{
    private static final String CONFIG_GROUP = "chancemantrackersync";
    private static final String CONFIG_KEY = "achievementDiaryTaskStatesJson";
    private static final Type TASK_STATES_TYPE = new TypeToken<Map<String, Map<String, List<Boolean>>>>() { }.getType();
    private static final int MAX_SEGMENT_ROWS = 4;
    private static final double MIN_SEGMENT_SCORE = 0.45;

    private final Gson gson = new Gson();
    private final Map<String, Map<String, List<String>>> taskDefinitions = loadTaskDefinitions();

    Map<String, Map<String, List<Boolean>>> loadStoredTaskStates(ConfigManager configManager)
    {
        String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
        if (json == null || json.trim().isEmpty())
        {
            return new LinkedHashMap<>();
        }

        Map<String, Map<String, List<Boolean>>> parsed = gson.fromJson(json, TASK_STATES_TYPE);
        return parsed != null ? parsed : new LinkedHashMap<>();
    }

    void storeCaptures(ConfigManager configManager, List<Capture> captures)
    {
        if (captures == null || captures.isEmpty())
        {
            return;
        }

        Map<String, Map<String, List<Boolean>>> taskStates = loadStoredTaskStates(configManager);
        for (Capture capture : captures)
        {
            Map<String, List<Boolean>> diaryStates = taskStates.computeIfAbsent(capture.diaryName, key -> new LinkedHashMap<>());
            List<Boolean> tierStates = diaryStates.computeIfAbsent(capture.tierName, key -> createFalseList(capture.taskCount));
            while (tierStates.size() < capture.taskCount)
            {
                tierStates.add(false);
            }

            for (Map.Entry<Integer, Boolean> entry : capture.taskStates.entrySet())
            {
                tierStates.set(entry.getKey(), entry.getValue());
            }
        }

        configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, gson.toJson(taskStates));
    }

    List<Capture> parseCaptures(Widget[] children)
    {
        if (children == null || children.length == 0 || children[0] == null)
        {
            return Collections.emptyList();
        }

        String diaryName = mapDiaryTitle(children[0].getText());
        if (diaryName == null)
        {
            return Collections.emptyList();
        }

        Map<String, List<String>> tiers = taskDefinitions.get(diaryName);
        if (tiers == null || tiers.isEmpty())
        {
            return Collections.emptyList();
        }

        List<RowState> rows = extractRows(children);
        if (rows.isEmpty())
        {
            return Collections.emptyList();
        }

        Map<String, Capture> captures = new LinkedHashMap<>();
        String currentTier = null;

        for (int rowIndex = 0; rowIndex < rows.size();)
        {
            RowState row = rows.get(rowIndex);
            if (isTierHeader(row.normalizedText))
            {
                currentTier = toTierName(row.normalizedText);
                rowIndex++;
                continue;
            }

            if (currentTier == null)
            {
                rowIndex++;
                continue;
            }

            List<String> tierTasks = tiers.get(currentTier);
            if (tierTasks == null || tierTasks.isEmpty())
            {
                rowIndex++;
                continue;
            }

            SegmentMatch match = findBestMatch(rows, rowIndex, tierTasks);
            if (match == null)
            {
                rowIndex++;
                continue;
            }

            Capture capture = captures.computeIfAbsent(currentTier, key -> new Capture(diaryName, key, tierTasks.size()));
            capture.taskStates.put(match.taskIndex, match.completed);
            rowIndex = match.nextRowIndex;
        }

        return new ArrayList<>(captures.values());
    }

    private SegmentMatch findBestMatch(List<RowState> rows, int rowIndex, List<String> tierTasks)
    {
        SegmentMatch bestMatch = null;
        StringBuilder combined = new StringBuilder();
        boolean completed = false;

        for (int endIndex = rowIndex; endIndex < rows.size() && endIndex < rowIndex + MAX_SEGMENT_ROWS; endIndex++)
        {
            RowState row = rows.get(endIndex);
            if (endIndex > rowIndex && isTierHeader(row.normalizedText))
            {
                break;
            }

            if (combined.length() > 0)
            {
                combined.append(' ');
            }
            combined.append(row.normalizedText);
            completed |= row.completed;

            for (int taskIndex = 0; taskIndex < tierTasks.size(); taskIndex++)
            {
                double score = similarity(combined.toString(), normalizeText(tierTasks.get(taskIndex)));
                if (score < MIN_SEGMENT_SCORE)
                {
                    continue;
                }

                if (bestMatch == null || score > bestMatch.score)
                {
                    bestMatch = new SegmentMatch(taskIndex, completed, endIndex + 1, score);
                }
            }
        }

        return bestMatch;
    }

    private List<RowState> extractRows(Widget[] children)
    {
        List<RowState> rows = new ArrayList<>();
        for (int index = 1; index < children.length; index++)
        {
            Widget child = children[index];
            if (child == null)
            {
                continue;
            }

            String raw = child.getText();
            String normalized = normalizeText(raw);
            if (normalized.isEmpty())
            {
                continue;
            }

            rows.add(new RowState(normalized, raw != null && raw.contains("<str>")));
        }
        return rows;
    }

    private List<Boolean> createFalseList(int size)
    {
        List<Boolean> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++)
        {
            values.add(false);
        }
        return values;
    }

    private boolean isTierHeader(String normalized)
    {
        return "easy".equals(normalized)
            || "medium".equals(normalized)
            || "hard".equals(normalized)
            || "elite".equals(normalized);
    }

    private String toTierName(String normalized)
    {
        switch (normalized)
        {
            case "easy":
                return "Easy";
            case "medium":
                return "Medium";
            case "hard":
                return "Hard";
            case "elite":
                return "Elite";
            default:
                return null;
        }
    }

    private double similarity(String actual, String expected)
    {
        if (actual.isEmpty() || expected.isEmpty())
        {
            return 0.0;
        }

        String collapsedActual = collapse(actual);
        String collapsedExpected = collapse(expected);
        if (collapsedActual.equals(collapsedExpected))
        {
            return 1.0;
        }

        if (collapsedActual.contains(collapsedExpected) || collapsedExpected.contains(collapsedActual))
        {
            int minLength = Math.min(collapsedActual.length(), collapsedExpected.length());
            int maxLength = Math.max(collapsedActual.length(), collapsedExpected.length());
            return 0.9 * ((double) minLength / (double) maxLength);
        }

        Set<String> actualTokens = tokenize(actual);
        Set<String> expectedTokens = tokenize(expected);
        if (actualTokens.isEmpty() || expectedTokens.isEmpty())
        {
            return 0.0;
        }

        int overlap = 0;
        for (String token : actualTokens)
        {
            if (expectedTokens.contains(token))
            {
                overlap++;
            }
        }

        return (2.0 * overlap) / (actualTokens.size() + expectedTokens.size());
    }

    private Set<String> tokenize(String value)
    {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : value.split(" "))
        {
            if (!token.isEmpty())
            {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String collapse(String value)
    {
        return value.replace(" ", "");
    }

    private Map<String, Map<String, List<String>>> loadTaskDefinitions()
    {
        InputStream stream = AchievementDiaryCaptureStore.class.getResourceAsStream("/achievement_diaries.json");
        if (stream == null)
        {
            return Collections.emptyMap();
        }

        DiaryData data;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
        {
            data = gson.fromJson(reader, DiaryData.class);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Failed to load achievement diary definitions", ex);
        }

        if (data == null || data.diaries == null)
        {
            return Collections.emptyMap();
        }

        Map<String, Map<String, List<String>>> definitions = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<DiaryTask>>> diaryEntry : data.diaries.entrySet())
        {
            Map<String, List<String>> tiers = new LinkedHashMap<>();
            for (Map.Entry<String, List<DiaryTask>> tierEntry : diaryEntry.getValue().entrySet())
            {
                List<String> tasks = new ArrayList<>();
                for (DiaryTask task : tierEntry.getValue())
                {
                    if (task != null && task.name != null && !task.name.trim().isEmpty())
                    {
                        tasks.add(task.name.trim());
                    }
                }
                tiers.put(tierEntry.getKey(), tasks);
            }
            definitions.put(diaryEntry.getKey(), tiers);
        }
        return definitions;
    }

    private String mapDiaryTitle(String rawTitle)
    {
        String title = Text.removeTags(rawTitle == null ? "" : rawTitle)
            .replace(" ", "_")
            .toUpperCase();

        switch (title)
        {
            case "ARDOUGNE_AREA_TASKS":
                return "Ardougne";
            case "DESERT_TASKS":
                return "Desert";
            case "FALADOR_AREA_TASKS":
                return "Falador";
            case "FREMENNIK_TASKS":
                return "Fremennik";
            case "KANDARIN_TASKS":
                return "Kandarin";
            case "KARAMJA_AREA_TASKS":
                return "Karamja";
            case "KOUREND_&_KEBOS_TASKS":
                return "Kourend & Kebos";
            case "LUMBRIDGE_&_DRAYNOR_TASKS":
                return "Lumbridge & Draynor";
            case "MORYTANIA_TASKS":
                return "Morytania";
            case "VARROCK_TASKS":
                return "Varrock";
            case "WESTERN_AREA_TASKS":
                return "Western Provinces";
            case "WILDERNESS_AREA_TASKS":
                return "Wilderness";
            default:
                return null;
        }
    }

    private String normalizeText(String value)
    {
        return Text.removeTags(value == null ? "" : value)
            .toLowerCase()
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
    }

    static final class Capture
    {
        private final String diaryName;
        private final String tierName;
        private final int taskCount;
        private final Map<Integer, Boolean> taskStates = new LinkedHashMap<>();

        private Capture(String diaryName, String tierName, int taskCount)
        {
            this.diaryName = diaryName;
            this.tierName = tierName;
            this.taskCount = taskCount;
        }

        String getDiaryName()
        {
            return diaryName;
        }

        String getTierName()
        {
            return tierName;
        }

        int getCapturedTaskCount()
        {
            return taskStates.size();
        }
    }

    private static final class SegmentMatch
    {
        private final int taskIndex;
        private final boolean completed;
        private final int nextRowIndex;
        private final double score;

        private SegmentMatch(int taskIndex, boolean completed, int nextRowIndex, double score)
        {
            this.taskIndex = taskIndex;
            this.completed = completed;
            this.nextRowIndex = nextRowIndex;
            this.score = score;
        }
    }

    private static final class RowState
    {
        private final String normalizedText;
        private final boolean completed;

        private RowState(String normalizedText, boolean completed)
        {
            this.normalizedText = normalizedText;
            this.completed = completed;
        }
    }

    private static final class DiaryData
    {
        Map<String, Map<String, List<DiaryTask>>> diaries;
    }

    private static final class DiaryTask
    {
        String name;
    }
}