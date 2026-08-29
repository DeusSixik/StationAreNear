package dev.sixik.stationarenear.structures.util;

/**
 * Единый реестр тегов и metadata-ключей для генерации станций, триггеров структур
 * и квестов.
 * <p>
 * Строки вроде {@code quest_room}, {@code door_trigger} и {@code electric_switch}
 * должны жить здесь, а не размазываться по Java-коду и генерируемым JSON-примерам.
 */
public final class TagsConstants {

    private TagsConstants() {
    }

    /**
     * Теги комнат и зон, по которым Director и квесты ищут подходящие куски станции
     * или trigger-зоны.
     */
    public static final class Quest {

        private Quest() {
        }

        /** Универсальная квестовая комната для тестовых и fallback-задач. */
        public static final String QUEST_ROOM = "quest_room";

        /** Комната или зона с гермодверями для заданий на ремонт дверей. */
        public static final String DOOR_ROOM = "door_room";

        /** Зона, куда можно раскидывать мусор и считать его для задания уборки. */
        public static final String TRASH = "trash";

        /** Цель рядом с розеткой для установки холодильника или микроволновки. */
        public static final String ELECTRIC = "electric";

        public static final String SOCKET = "socket";

        /** Цель рядом с трубами для установки раковины. */
        public static final String PIPES = "pipes";

        /** Цель для электрического щитка/переключателя станции. */
        public static final String ELECTRIC_SWITCH = "electric_switch";

        /** Общий кухонный тег для зон установки бытовых объектов. */
        public static final String KITCHEN_PLACE = "kitchen_place";
    }

    /**
     * Строковые типы trigger-ов, которые сохраняются в metadata шаблонов структур.
     */
    public static final class Trigger {

        private Trigger() {
        }

        /** Обычная точка спавна мобов. */
        public static final String MOB_SPAWN = "mob_spawn";

        /** Точка спавна мобов, покупаемая за бюджет опасности станции. */
        public static final String DANGER_MOB_SPAWN = "danger_mob_spawn";

        /** Trigger, который ставит один объект из object-pool. */
        public static final String OBJECT_PLACER = "object_placer";

        /** Quest-only версия object placer-а: срабатывает только когда квесту нужно разложить нужный объект. */
        public static final String QUEST_OBJECT_PLACER = "quest_object_placer";

        /** Alias для {@link #QUEST_OBJECT_PLACER}. */
        public static final String QUEST_OBJECT_PLACE = "quest_object_place";

        /** Alias для {@link #QUEST_OBJECT_PLACER}, когда metadata явно называет pool объектов для квеста. */
        public static final String QUEST_OBJECT_POOL = "quest_object_pool";

        /** Старый alias object placer-а для loot-триггеров. */
        public static final String LOOT = "loot";

        /** Старый alias object placer-а из ранних metadata. */
        public static final String OBJECT_PLACE = "object_place";

        /** Alias object placer-а, когда metadata явно называет pool объектов. */
        public static final String OBJECT_POOL = "object_pool";

        /** Зональный placer, который заполняет валидные клетки пола объектами из pool. */
        public static final String OBJECT_ZONE_PLACER = "object_zone_placer";

        /** Alias для {@link #OBJECT_ZONE_PLACER}. */
        public static final String OBJECT_ZONE_PLACE = "object_zone_place";

        /** Alias для {@link #OBJECT_ZONE_PLACER}. */
        public static final String ZONE_OBJECT_PLACER = "zone_object_placer";

        /** Alias для {@link #OBJECT_ZONE_PLACER}, когда он используется как напольный декор. */
        public static final String FLOOR_OBJECT_PLACER = "floor_object_placer";

        /** Trigger установки гермодвери. */
        public static final String DOOR_TRIGGER = "door_trigger";

        /** Старый/editor alias для {@link #DOOR_TRIGGER}. */
        public static final String DOOR_SPAWNER = "door_spawner";

        /** Короткий alias для {@link #DOOR_TRIGGER}. */
        public static final String DOOR = "door";

        /** Alias для trigger-а гермодвери pressure door. */
        public static final String PRESSURE_DOOR = "pressure_door";

        /** Alias для trigger-а pressure tight door. */
        public static final String PRESSURE_TIGHT_DOOR = "pressure_tight_door";

        /** Универсальный trigger квестовой точки или зоны. */
        public static final String QUEST = "quest";

        /** Alias универсального квестового trigger-а. */
        public static final String QUEST_TRIGGER = "quest_trigger";

        /** Alias универсального квестового trigger-а. */
        public static final String TRIGGER_QUEST = "trigger_quest";

        /** Trigger зоны установки квестового предмета или блока рядом с целью. */
        public static final String QUEST_PLACE = "quest_place";

        /** Alias для {@link #QUEST_PLACE}. */
        public static final String PLACE_QUEST = "place_quest";

        /** Alias для {@link #QUEST_PLACE}. */
        public static final String QUEST_PLACE_TRIGGER = "quest_place_trigger";

        /** Alias для {@link #QUEST_PLACE}. */
        public static final String PLACE_TRIGGER = "place_trigger";

        /** Trigger переключения аварийных и штатных ламп станции. */
        public static final String LAMP_SWITCH = "lamp_switch";
        public static final String LAMP = "lamp";
        public static final String LAMP_TRIGGER = "lamp_trigger";

        public static final String SOUND_TRIGGER = "sound_trigger";
        public static final String SOUND = "sound";

        public static final String TRIGGER = "trigger";

        /** Старое значение для служебных/прочих trigger-ов. */
        public static final String OTHER = "other";
    }

    /**
     * Теги connection-ов и значения по умолчанию для стыковки кусков станции.
     */
    public static final class Connection {

        private Connection() {
        }

        /** Обычный проходимый corridor/doorway connection. */
        public static final String CORRIDOR = "corridor";

        /** Значение по умолчанию, если template не задал более конкретный tag. */
        public static final String DEFAULT = "default";
    }

    /**
     * NBT/JSON-ключи, в которых хранятся теги и параметры генерации, связанные с тегами.
     */
    public static final class Keys {

        private Keys() {
        }

        /** Одиночный tag в trigger data. */
        public static final String TAG = "tag";

        /** Список или CSV-строка тегов в trigger/template/connection data. */
        public static final String TAGS = "tags";

        /** Уровень опасности станции, прокинутый в trigger data на этапе placement. */
        public static final String STATION_DANGER = "stationDanger";

        /** JSON-ключ offer-а Director со списком подходящих target-тегов. */
        public static final String TARGET_TAGS = "target_tags";

        /** JSON-ключ offer-а Director с обязательными тегами station pieces. */
        public static final String REQUIRED_PIECE_TAGS = "required_piece_tags";

        /** JSON-ключ offer-а Director с обязательными ids station pieces. */
        public static final String REQUIRED_PIECES = "required_pieces";

        /** JSON/NBT-ключ для пропуска автоспавна части квестовых элементов. */
        public static final String QUEST_ELEMENT_SPAWN_SKIPS = "quest_element_spawn_skips";

        /** Флаг object-zone placer-а: trigger активируется только квестом/Director. */
        public static final String ONLY_QUESTS = "onlyQuests";

        /** Alias для {@link #ONLY_QUESTS}. */
        public static final String ONLY_QUEST = "onlyQuest";

        /** CamelCase alias для quest-only флагов размещения. */
        public static final String QUEST_ONLY = "questOnly";

        /** Snake-case JSON alias для quest-only флагов размещения и station config. */
        public static final String QUEST_ONLY_SNAKE = "quest_only";
    }
}
