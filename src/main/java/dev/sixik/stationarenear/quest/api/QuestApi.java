package dev.sixik.stationarenear.quest.api;

import dev.sixik.stationarenear.navigation.StationCodeGenerator;
import dev.sixik.stationarenear.navigation.world.SolarNavigationStationCleaner;
import dev.sixik.stationarenear.quest.data.QuestDefinition;
import dev.sixik.stationarenear.quest.data.QuestLocalization;
import dev.sixik.stationarenear.quest.data.QuestObjectiveKind;
import dev.sixik.stationarenear.quest.data.QuestObjectiveState;
import dev.sixik.stationarenear.quest.data.QuestStationState;
import dev.sixik.stationarenear.quest.data.QuestTask;
import dev.sixik.stationarenear.quest.data.QuestValueCodec;
import dev.sixik.stationarenear.quest.event.QuestAssignedEvent;
import dev.sixik.stationarenear.quest.event.QuestCompletedEvent;
import dev.sixik.stationarenear.quest.event.QuestMissionFailedEvent;
import dev.sixik.stationarenear.quest.event.QuestProgressChangedEvent;
import dev.sixik.stationarenear.quest.event.QuestStartedEvent;
import dev.sixik.stationarenear.quest.event.QuestTaskCompletedEvent;
import dev.sixik.stationarenear.quest.event.StationQuestsCompletedEvent;
import dev.sixik.stationarenear.quest.world.QuestSavedData;
import dev.sixik.stationarenear.structures.world.StationSavedData;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Публичный API для регистрации типов квестовых задач и отслеживания прогресса задач станции.
 * <p>
 * Если у вызывающего кода уже есть {@link ServerLevel}, лучше использовать методы с level;
 * перегрузки только с UUID являются удобными helper-методами и пытаются найти загруженный level автоматически.
 */
public final class QuestApi {

    private static final Map<String, QuestDefinition> DEFINITIONS = new Object2ObjectLinkedOpenHashMap<>();
    private static final Map<String, QuestLocalization> LOCALIZATIONS = new Object2ObjectLinkedOpenHashMap<>();

    private QuestApi() {
    }

    public static QuestTask quest(String id, int count) {
        return new QuestTask(id, count);
    }

    public static QuestTask quest(String id, int count, String targetTriggerId) {
        return new QuestTask(id, count, targetTriggerId);
    }

    /**
     * Регистрирует id квестовой задачи и Java-тип, в котором хранится её прогресс.
     * Повторная регистрация того же id с тем же типом возвращает уже существующее описание.
     *
     * @param id уникальный id квестовой задачи, например {@code stationarenear:kill_mobs}
     * @param progressType поддерживаемый тип значения прогресса, например {@code Boolean.class}
     * @return зарегистрированное описание квестовой задачи
     */
    public static QuestDefinition register(String id, Class<?> progressType) {
        return register(id, progressType, QuestObjectiveKind.CUSTOM, null, false);
    }

    public static QuestDefinition register(String id, Class<?> progressType, QuestObjectiveKind kind) {
        return register(id, progressType, kind, null, true);
    }

    public static QuestDefinition register(String id, Class<?> progressType, QuestObjectiveKind kind, QuestLocalization localization) {
        return register(id, progressType, kind, localization, true);
    }

    public static QuestDefinition register(String id, Class<?> progressType, QuestObjectiveKind kind, String playerText, String samText) {
        return register(id, progressType, kind, new QuestLocalization(playerText, samText), true);
    }

    public static QuestLocalization registerLocalization(String id, String playerText, String samText) {
        return registerLocalization(id, new QuestLocalization(playerText, samText));
    }

    public static QuestLocalization registerLocalization(String id, QuestLocalization localization) {
        String normalizedId = normalizeId(id);
        localization = localization == null ? QuestLocalization.fallback(normalizedId) : localization;
        LOCALIZATIONS.put(normalizedId, localization);
        QuestDefinition existing = DEFINITIONS.get(normalizedId);
        if (existing != null) {
            DEFINITIONS.put(normalizedId, new QuestDefinition(existing.id(), existing.progressType(), existing.kind(), localization));
        }
        return localization;
    }

    public static QuestLocalization localization(String id) {
        String normalizedId = normalizeId(id);
        QuestDefinition definition = DEFINITIONS.get(normalizedId);
        if (definition != null) {
            return definition.localization();
        }
        return LOCALIZATIONS.getOrDefault(normalizedId, QuestLocalization.fallback(normalizedId));
    }

    private static QuestDefinition register(String id, Class<?> progressType, QuestObjectiveKind kind, QuestLocalization localization, boolean overwriteMetadata) {
        String normalizedId = normalizeId(id);
        Class<?> normalizedType = QuestValueCodec.normalize(progressType);
        if (!QuestValueCodec.supports(normalizedType)) {
            throw new IllegalArgumentException("Unsupported quest progress type: " + progressType.getName());
        }

        QuestObjectiveKind normalizedKind = kind == null ? QuestObjectiveKind.CUSTOM : kind;
        QuestLocalization normalizedLocalization = localization == null
                ? LOCALIZATIONS.getOrDefault(normalizedId, QuestLocalization.fallback(normalizedId))
                : localization;
        LOCALIZATIONS.putIfAbsent(normalizedId, normalizedLocalization);

        QuestDefinition existing = DEFINITIONS.get(normalizedId);
        if (existing != null) {
            if (!existing.progressType().equals(normalizedType)) {
                throw new IllegalArgumentException("Quest `" + normalizedId + "` is already registered as " + existing.progressType().getSimpleName());
            }
            if (overwriteMetadata) {
                QuestDefinition updated = new QuestDefinition(normalizedId, normalizedType, normalizedKind, normalizedLocalization);
                DEFINITIONS.put(normalizedId, updated);
                return updated;
            }
            return existing;
        }

        QuestDefinition definition = new QuestDefinition(normalizedId, normalizedType, normalizedKind, normalizedLocalization);
        DEFINITIONS.put(normalizedId, definition);
        return definition;
    }

    /**
     * Ищет зарегистрированное описание квестовой задачи по id.
     */
    public static Optional<QuestDefinition> definition(String id) {
        return Optional.ofNullable(DEFINITIONS.get(normalizeId(id)));
    }

    /**
     * Возвращает все зарегистрированные задачи в порядке регистрации.
     */
    public static Collection<QuestDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    /**
     * Добавляет одну квестовую задачу на станцию, если она ещё не назначена.
     * Отправляет {@link QuestAssignedEvent}, когда задача действительно добавлена.
     *
     * @return {@code true}, если задача была добавлена; {@code false}, если она уже существовала
     */
    public static boolean assign(ServerLevel level, UUID stationId, String id) {
        QuestDefinition definition = requireDefinition(id);
        QuestSavedData data = QuestSavedData.get(level);
        QuestStationState stationState = data.station(stationId);
        boolean added = stationState.putIfAbsent(new QuestObjectiveState(definition.id(), false, new CompoundTag()));
        if (!added) {
            return false;
        }

        data.station(stationState);
        MinecraftForge.EVENT_BUS.post(new QuestAssignedEvent(level, stationId, definition));
        return true;
    }

    /**
     * Добавляет несколько квестовых задач на станцию.
     *
     * @return количество реально добавленных задач
     */
    public static int assign(ServerLevel level, UUID stationId, Collection<String> ids) {
        int assigned = 0;
        for (String id : ids) {
            if (assign(level, stationId, id)) {
                assigned++;
            }
        }
        return assigned;
    }

    /**
     * Добавляет несколько квестовых задач на станцию.
     *
     * @return количество реально добавленных задач
     */
    public static int assign(ServerLevel level, UUID stationId, String... ids) {
        int assigned = 0;
        for (String id : ids) {
            if (assign(level, stationId, id)) {
                assigned++;
            }
        }
        return assigned;
    }

    /**
     * Помечает активную квестовую задачу как выполненную.
     * Отправляет {@link QuestCompletedEvent}; если это была последняя активная задача станции,
     * дополнительно отправляет {@link StationQuestsCompletedEvent}.
     *
     * @return {@code true}, если задача была выполнена именно этим вызовом
     */
    public static boolean complete(ServerLevel level, UUID stationId, String id) {
        return complete(level, stationId, id, null);
    }

    public static boolean complete(ServerLevel level, UUID stationId, String id, ServerPlayer player) {
        QuestDefinition definition = requireDefinition(id);
        QuestSavedData data = QuestSavedData.get(level);
        Optional<QuestStationState> stationOptional = data.stationIfPresent(stationId);
        if (stationOptional.isEmpty()) {
            return false;
        }

        QuestStationState stationState = stationOptional.get();
        Optional<QuestObjectiveState> objectiveOptional = stationState.objective(definition.id());
        if (objectiveOptional.isEmpty() || objectiveOptional.get().completed()) {
            return false;
        }

        QuestObjectiveState objective = objectiveOptional.get();
        Object completedValue = QuestValueCodec.completedValue(definition.progressType(), objective.progress());
        CompoundTag progress = objective.progress();
        mergeProgressValue(progress, QuestValueCodec.encode(definition.progressType(), completedValue));
        stationState.put(objective.complete(progress));
        data.station(stationState);
        data.markQuestCompleted(definition.id());

        MinecraftForge.EVENT_BUS.post(new QuestTaskCompletedEvent(level, stationId, definition, completedValue));
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(level, stationId, definition, completedValue));
        if (!stationState.hasActiveObjectives()) {
            MinecraftForge.EVENT_BUS.post(new StationQuestsCompletedEvent(level, stationId));
        }
        return true;
    }

    public static boolean fail(ServerLevel level, UUID stationId, String reason) {
        QuestSavedData data = QuestSavedData.get(level);
        Optional<QuestStationState> stationOptional = data.stationIfPresent(stationId);
        if (stationOptional.isEmpty()) {
            return false;
        }
        MinecraftForge.EVENT_BUS.post(new QuestMissionFailedEvent(level, stationId, stationOptional.get(), reason));
        return true;
    }

    /**
     * Обновляет прогресс активной квестовой задачи.
     * Значение должно соответствовать типу, зарегистрированному через {@link #register(String, Class)}.
     * Отправляет {@link QuestProgressChangedEvent}, когда значение сохранено.
     *
     * @return {@code true}, если прогресс был обновлён
     */
    public static boolean progress(ServerLevel level, UUID stationId, String id, Object value) {
        QuestDefinition definition = requireDefinition(id);
        QuestSavedData data = QuestSavedData.get(level);
        Optional<QuestStationState> stationOptional = data.stationIfPresent(stationId);
        if (stationOptional.isEmpty()) {
            return false;
        }

        QuestStationState stationState = stationOptional.get();
        Optional<QuestObjectiveState> objectiveOptional = stationState.objective(definition.id());
        if (objectiveOptional.isEmpty() || objectiveOptional.get().completed()) {
            return false;
        }

        QuestObjectiveState objective = objectiveOptional.get();
        Object oldProgress = QuestValueCodec.decode(definition.progressType(), objective.progress());
        CompoundTag progress = objective.progress();
        mergeProgressValue(progress, QuestValueCodec.encode(definition.progressType(), value));
        stationState.put(objective.withProgress(progress));
        data.station(stationState);

        MinecraftForge.EVENT_BUS.post(new QuestProgressChangedEvent(level, stationId, definition, oldProgress, value));
        return true;
    }

    /**
     * Читает сохранённый прогресс квестовой задачи.
     *
     * @return типизированное значение прогресса или {@code null}, если прогресса нет
     */
    public static Object progress(ServerLevel level, UUID stationId, String id) {
        QuestDefinition definition = requireDefinition(id);
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .flatMap(station -> station.objective(definition.id()))
                .map(objective -> QuestValueCodec.decode(definition.progressType(), objective.progress()))
                .orElse(null);
    }

    /**
     * Читает сохранённый прогресс и приводит его к указанному типу.
     *
     * @throws IllegalArgumentException если сохранённое значение имеет другой тип
     */
    public static <T> Optional<T> progress(ServerLevel level, UUID stationId, String id, Class<T> type) {
        Object value = progress(level, stationId, id);
        if (value == null) {
            return Optional.empty();
        }
        Class<?> normalized = QuestValueCodec.normalize(type);
        if (!normalized.isInstance(value)) {
            throw new IllegalArgumentException("Quest progress value is " + value.getClass().getSimpleName() + ", not " + normalized.getSimpleName());
        }
        return Optional.of(type.cast(value));
    }

    /**
     * Возвращает id всех назначенных задач, которые ещё не выполнены.
     */
    public static String[] getActive(ServerLevel level, UUID stationId) {
        return activeObjectives(level, stationId, false);
    }

    /**
     * Возвращает id всех выполненных задач станции.
     */
    public static String[] getCompleted(ServerLevel level, UUID stationId) {
        return activeObjectives(level, stationId, true);
    }

    /**
     * Проверяет, назначена ли конкретная задача и остаётся ли она активной.
     */
    public static boolean isActive(ServerLevel level, UUID stationId, String id) {
        String normalizedId = normalizeId(id);
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .flatMap(station -> station.objective(normalizedId))
                .map(objective -> !objective.completed())
                .orElse(false);
    }

    /**
     * Проверяет, выполнена ли конкретная задача.
     */
    public static boolean isCompleted(ServerLevel level, UUID stationId, String id) {
        String normalizedId = normalizeId(id);
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .flatMap(station -> station.objective(normalizedId))
                .map(QuestObjectiveState::completed)
                .orElse(false);
    }

    public static void startQuest(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, String> objectiveTexts, long durationSeconds) {
        startQuest(level, stationId, tasks, objectiveTexts, durationSeconds, 0.0D);
    }

    public static void startQuest(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, String> objectiveTexts, long durationSeconds, double moneyReward) {
        startQuestMillis(level, stationId, tasks, objectiveTexts, Math.multiplyExact(durationSeconds, 1000L), moneyReward);
    }

    public static void startQuestMillis(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, String> objectiveTexts, long durationMillis) {
        startQuestMillis(level, stationId, tasks, objectiveTexts, durationMillis, 0.0D);
    }

    public static void startQuestMillis(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, String> objectiveTexts, long durationMillis, double moneyReward) {
        Map<String, QuestLocalization> localizations = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, String> sourceTexts = objectiveTexts == null ? Map.of() : objectiveTexts;
        for (Map.Entry<String, String> entry : sourceTexts.entrySet()) {
            localizations.put(normalizeId(entry.getKey()), new QuestLocalization(entry.getValue(), ""));
        }
        startQuestMillisLocalized(level, stationId, tasks, localizations, durationMillis, "", moneyReward);
    }

    public static void startQuestLocalized(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, QuestLocalization> objectiveTexts, long durationSeconds) {
        startQuestLocalized(level, stationId, tasks, objectiveTexts, durationSeconds, 0.0D);
    }

    public static void startQuestLocalized(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, QuestLocalization> objectiveTexts, long durationSeconds, double moneyReward) {
        startQuestMillisLocalized(level, stationId, tasks, objectiveTexts, Math.multiplyExact(durationSeconds, 1000L), "", moneyReward);
    }

    public static void startQuestLocalized(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, QuestLocalization> objectiveTexts, long durationSeconds, String stationCode) {
        startQuestLocalized(level, stationId, tasks, objectiveTexts, durationSeconds, stationCode, 0.0D);
    }

    public static void startQuestLocalized(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, QuestLocalization> objectiveTexts, long durationSeconds, String stationCode, double moneyReward) {
        startQuestMillisLocalized(level, stationId, tasks, objectiveTexts, Math.multiplyExact(durationSeconds, 1000L), stationCode, moneyReward);
    }

    public static void startQuestMillisLocalized(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, QuestLocalization> objectiveTexts, long durationMillis) {
        startQuestMillisLocalized(level, stationId, tasks, objectiveTexts, durationMillis, "", 0.0D);
    }

    public static void startQuestMillisLocalized(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, QuestLocalization> objectiveTexts, long durationMillis, String stationCode) {
        startQuestMillisLocalized(level, stationId, tasks, objectiveTexts, durationMillis, stationCode, 0.0D);
    }

    public static void startQuestMillisLocalized(ServerLevel level, UUID stationId, Collection<QuestTask> tasks, Map<String, QuestLocalization> objectiveTexts, long durationMillis, String stationCode, double moneyReward) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Quest must contain at least one task");
        }
        if (durationMillis <= 0L) {
            throw new IllegalArgumentException("Quest duration must be positive");
        }

        QuestSavedData data = QuestSavedData.get(level);
        QuestStationState stationState = data.station(stationId);
        stationState.displayStationCode(stationCode);
        stationState.moneyReward(moneyReward);
        List<QuestTask> normalizedTasks = new ArrayList<>();
        Map<String, String> normalizedTexts = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, String> normalizedSamTexts = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, QuestLocalization> sourceTexts = objectiveTexts == null ? Map.of() : objectiveTexts;

        for (QuestTask task : tasks) {
            QuestDefinition definition = requireDefinition(task.id());
            QuestLocalization definitionLocalization = definition.localization();
            QuestLocalization sourceLocalization = sourceTexts.getOrDefault(definition.id(), sourceTexts.getOrDefault(task.id(), definitionLocalization));
            String text = sourceLocalization.playerText(definitionLocalization.playerText(definition.id()));
            String samText = sourceLocalization.samText().isBlank()
                    ? definitionLocalization.samText(text)
                    : sourceLocalization.samText(text);
            QuestTask normalizedTask = new QuestTask(definition.id(), task.count(), task.targetTriggerId());
            normalizedTasks.add(normalizedTask);
            normalizedTexts.put(definition.id(), text);
            normalizedSamTexts.put(definition.id(), samText);
            stationState.put(new QuestObjectiveState(
                    definition.id(),
                    false,
                    QuestValueCodec.encode(definition.progressType(), initialProgressValue(definition.progressType())),
                    task.count(),
                    text,
                    task.targetTriggerId()
            ));
        }

        stationState.startTimer(durationMillis);
        data.station(stationState);
        data.currentStationId(stationId);

        String announcement = missionAnnouncement(level, stationId, normalizedTasks, normalizedSamTexts, durationMillis, stationCode);
        MinecraftForge.EVENT_BUS.post(new QuestStartedEvent(level, stationId, normalizedTasks, normalizedTexts, durationMillis, announcement, stationState.moneyReward()));
    }

    /**
     * Запускает таймер станции в реальных секундах активной работы сервера.
     * Время уменьшается только между серверными тиками, поэтому при остановке сервера таймер заморожен.
     */
    public static void startTimer(ServerLevel level, UUID stationId, long durationSeconds) {
        startTimerMillis(level, stationId, Math.multiplyExact(durationSeconds, 1000L));
    }

    /**
     * Запускает таймер станции в миллисекундах активной работы сервера.
     */
    public static void startTimerMillis(ServerLevel level, UUID stationId, long durationMillis) {
        QuestSavedData data = QuestSavedData.get(level);
        QuestStationState stationState = data.station(stationId);
        stationState.startTimer(durationMillis);
        data.station(stationState);
    }

    /**
     * Очищает таймер станции, если он был задан.
     */
    public static boolean clearTimer(ServerLevel level, UUID stationId) {
        QuestSavedData data = QuestSavedData.get(level);
        Optional<QuestStationState> stationOptional = data.stationIfPresent(stationId);
        if (stationOptional.isEmpty() || !stationOptional.get().hasTimer()) {
            return false;
        }
        QuestStationState stationState = stationOptional.get();
        stationState.clearTimer();
        data.station(stationState);
        return true;
    }

    /**
     * Проверяет, есть ли у станции таймер.
     */
    public static boolean hasTimer(ServerLevel level, UUID stationId) {
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .map(QuestStationState::hasTimer)
                .orElse(false);
    }

    /**
     * Проверяет, истёк ли таймер станции.
     */
    public static boolean isTimerExpired(ServerLevel level, UUID stationId) {
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .map(QuestStationState::timerExpired)
                .orElse(false);
    }

    /**
     * Возвращает оставшееся время таймера в миллисекундах.
     */
    public static long timerRemainingMillis(ServerLevel level, UUID stationId) {
        return QuestSavedData.get(level)
                .stationIfPresent(stationId)
                .map(QuestStationState::timerRemainingMillis)
                .orElse(0L);
    }

    /**
     * Возвращает оставшееся время таймера в секундах с округлением вверх.
     */
    public static long timerRemainingSeconds(ServerLevel level, UUID stationId) {
        long millis = timerRemainingMillis(level, stationId);
        return millis <= 0L ? 0L : (millis + 999L) / 1000L;
    }

    /**
     * Удаляет все квестовые данные станции.
     */
    public static boolean clear(ServerLevel level, UUID stationId) {
        return QuestSavedData.get(level).remove(stationId);
    }

    /**
     * Возвращает id станции, на которой сейчас висит активное задание.
     */
    public static Optional<UUID> currentStationId(ServerLevel level) {
        return QuestSavedData.get(level).currentStationId();
    }

    /**
     * Останавливает текущее задание и удаляет связанные с ним данные.
     */
    public static boolean stopCurrentQuest(ServerLevel level) {
        QuestSavedData data = QuestSavedData.get(level);
        Optional<UUID> stationId = data.currentStationId();
        if (stationId.isEmpty()) {
            return false;
        }

        boolean removed = data.remove(stationId.get());
        if (!removed) {
            data.clearCurrentStation();
        }
        return true;
    }

    /**
     * Удобная перегрузка для {@link #startQuest(ServerLevel, UUID, Collection, Map, long)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static void startQuest(UUID stationId, Collection<QuestTask> tasks, Map<String, String> objectiveTexts, long durationSeconds) {
        startQuest(requireLevel(stationId), stationId, tasks, objectiveTexts, durationSeconds);
    }

    /**
     * Удобная перегрузка для {@link #startQuestMillis(ServerLevel, UUID, Collection, Map, long)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static void startQuestMillis(UUID stationId, Collection<QuestTask> tasks, Map<String, String> objectiveTexts, long durationMillis) {
        startQuestMillis(requireLevel(stationId), stationId, tasks, objectiveTexts, durationMillis);
    }

    public static void startTimer(UUID stationId, long durationSeconds) {
        startTimer(requireLevel(stationId), stationId, durationSeconds);
    }

    /**
     * Удобная перегрузка для {@link #startTimerMillis(ServerLevel, UUID, long)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static void startTimerMillis(UUID stationId, long durationMillis) {
        startTimerMillis(requireLevel(stationId), stationId, durationMillis);
    }

    /**
     * Удобная перегрузка для {@link #clearTimer(ServerLevel, UUID)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static boolean clearTimer(UUID stationId) {
        return clearTimer(requireLevel(stationId), stationId);
    }

    /**
     * Удобная перегрузка для {@link #hasTimer(ServerLevel, UUID)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static boolean hasTimer(UUID stationId) {
        return hasTimer(requireLevel(stationId), stationId);
    }

    /**
     * Удобная перегрузка для {@link #isTimerExpired(ServerLevel, UUID)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static boolean isTimerExpired(UUID stationId) {
        return isTimerExpired(requireLevel(stationId), stationId);
    }

    /**
     * Удобная перегрузка для {@link #timerRemainingMillis(ServerLevel, UUID)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static long timerRemainingMillis(UUID stationId) {
        return timerRemainingMillis(requireLevel(stationId), stationId);
    }

    /**
     * Удобная перегрузка для {@link #timerRemainingSeconds(ServerLevel, UUID)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static long timerRemainingSeconds(UUID stationId) {
        return timerRemainingSeconds(requireLevel(stationId), stationId);
    }

    /**
     * Удобная перегрузка для {@link #assign(ServerLevel, UUID, String)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static boolean assign(UUID stationId, String id) {
        return assign(requireLevel(stationId), stationId, id);
    }

    /**
     * Удобная перегрузка для {@link #complete(ServerLevel, UUID, String)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static boolean complete(UUID stationId, String id) {
        return complete(requireLevel(stationId), stationId, id);
    }

    /**
     * Удобная перегрузка для {@link #progress(ServerLevel, UUID, String, Object)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static boolean progress(UUID stationId, String id, Object value) {
        return progress(requireLevel(stationId), stationId, id, value);
    }

    /**
     * Удобная перегрузка для {@link #progress(ServerLevel, UUID, String)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static Object progress(UUID stationId, String id) {
        return progress(requireLevel(stationId), stationId, id);
    }

    /**
     * Удобная перегрузка для {@link #getActive(ServerLevel, UUID)}.
     * Пытается автоматически найти level, к которому относится станция.
     */
    public static String[] getActive(UUID stationId) {
        return getActive(requireLevel(stationId), stationId);
    }

    private static void mergeProgressValue(CompoundTag target, CompoundTag encoded) {
        target.putString("type", encoded.getString("type"));
        target.remove("null");
        if (encoded.getBoolean("null")) {
            target.putBoolean("null", true);
        }
        target.remove("value");
        net.minecraft.nbt.Tag value = encoded.get("value");
        if (value != null) {
            target.put("value", value.copy());
        }
    }

    private static Object initialProgressValue(Class<?> type) {
        Class<?> normalizedType = QuestValueCodec.normalize(type);
        if (normalizedType == Boolean.class) {
            return false;
        }
        if (normalizedType == Integer.class) {
            return 0;
        }
        if (normalizedType == Long.class) {
            return 0L;
        }
        if (normalizedType == Float.class) {
            return 0.0F;
        }
        if (normalizedType == Double.class) {
            return 0.0D;
        }
        if (normalizedType == String.class) {
            return "";
        }
        if (normalizedType == CompoundTag.class) {
            return new CompoundTag();
        }
        return null;
    }

    private static String missionAnnouncement(ServerLevel level, UUID stationId, List<QuestTask> tasks, Map<String, String> texts, long durationMillis) {
        return missionAnnouncement(level, stationId, tasks, texts, durationMillis, "");
    }

    private static String missionAnnouncement(ServerLevel level, UUID stationId, List<QuestTask> tasks, Map<String, String> texts, long durationMillis, String stationCode) {
        StringBuilder builder = new StringBuilder();
        builder.append("Team, you have a new assignment. Travel to station code ").append(stationCode == null || stationCode.isBlank() ? stationSpeechCode(level, stationId) : stationCode).append(". ");
        builder.append("Use this code in the terminal scan command. ");
        builder.append("Your objectives are: ");
        for (int i = 0; i < tasks.size(); i++) {
            QuestTask task = tasks.get(i);
            builder.append(i + 1).append(". ").append(texts.getOrDefault(task.id(), task.id()));
            if (task.count() > 1) {
                builder.append(". Required count: ").append(task.count());
            }
            builder.append(". ");
        }
        long totalSeconds = Math.max(0L, (durationMillis + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        builder.append("You have ").append(minutes).append(" minutes and ").append(seconds).append(" seconds.");
        return builder.toString();
    }

    private static String stationSpeechCode(ServerLevel level, UUID stationId) {
        return StationSavedData.get(level)
                .station(stationId)
                .map(station -> station.customData().getString(SolarNavigationStationCleaner.KEY_NAVIGATION_STATION_CODE))
                .filter(code -> code != null && !code.isBlank())
                .orElseGet(() -> stationSpeechCode(stationId));
    }

    private static String stationSpeechCode(UUID stationId) {
        return StationCodeGenerator.code(stationId);
    }

    private static String[] activeObjectives(ServerLevel level, UUID stationId, boolean completed) {
        Optional<QuestStationState> stationOptional = QuestSavedData.get(level).stationIfPresent(stationId);
        if (stationOptional.isEmpty()) {
            return new String[0];
        }

        List<String> ids = new ArrayList<>();
        for (QuestObjectiveState objective : stationOptional.get().objectives()) {
            if (objective.completed() == completed) {
                ids.add(objective.id());
            }
        }
        return ids.toArray(String[]::new);
    }

    private static QuestDefinition requireDefinition(String id) {
        String normalizedId = normalizeId(id);
        QuestDefinition definition = DEFINITIONS.get(normalizedId);
        if (definition == null) {
            throw new IllegalArgumentException("Quest `" + normalizedId + "` is not registered");
        }
        return definition;
    }

    private static ServerLevel requireLevel(UUID stationId) {
        return findLevel(stationId).orElseThrow(() -> new IllegalStateException("Cannot resolve level for quest station `" + stationId + "`; use level-aware QuestApi methods"));
    }

    private static Optional<ServerLevel> findLevel(UUID stationId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Optional.empty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (QuestSavedData.get(level).hasStation(stationId) || StationSavedData.get(level).station(stationId).isPresent()) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    private static String normalizeId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Quest id cannot be blank");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!(c >= 'a' && c <= 'z')
                    && !(c >= '0' && c <= '9')
                    && c != '_'
                    && c != '-'
                    && c != '.'
                    && c != ':'
                    && c != '/') {
                throw new IllegalArgumentException("Invalid quest id: " + id);
            }
        }
        return normalized;
    }
}
