package dev.sixik.stationarenear.quest.data;

import net.minecraft.nbt.CompoundTag;

public final class QuestValueCodec {

    private QuestValueCodec() {
    }

    public static boolean supports(Class<?> type) {
        Class<?> normalized = normalize(type);
        return normalized == Boolean.class
                || normalized == Integer.class
                || normalized == Long.class
                || normalized == Float.class
                || normalized == Double.class
                || normalized == String.class
                || normalized == CompoundTag.class;
    }

    public static CompoundTag encode(Class<?> type, Object value) {
        Class<?> normalized = normalize(type);
        if (value != null && !normalized.isInstance(value)) {
            throw new IllegalArgumentException("Quest progress value must be " + normalized.getSimpleName() + ", got " + value.getClass().getSimpleName());
        }

        CompoundTag tag = new CompoundTag();
        if (value == null) {
            tag.putString("type", normalized.getName());
            tag.putBoolean("null", true);
            return tag;
        }

        tag.putString("type", normalized.getName());
        if (normalized == Boolean.class) {
            tag.putBoolean("value", (Boolean) value);
        } else if (normalized == Integer.class) {
            tag.putInt("value", (Integer) value);
        } else if (normalized == Long.class) {
            tag.putLong("value", (Long) value);
        } else if (normalized == Float.class) {
            tag.putFloat("value", (Float) value);
        } else if (normalized == Double.class) {
            tag.putDouble("value", (Double) value);
        } else if (normalized == String.class) {
            tag.putString("value", (String) value);
        } else if (normalized == CompoundTag.class) {
            tag.put("value", ((CompoundTag) value).copy());
        } else {
            throw new IllegalArgumentException("Unsupported quest progress type: " + type.getName());
        }
        return tag;
    }

    public static Object decode(Class<?> type, CompoundTag tag) {
        if (tag == null || tag.isEmpty() || tag.getBoolean("null")) {
            return null;
        }

        Class<?> normalized = normalize(type);
        if (normalized == Boolean.class) {
            return tag.getBoolean("value");
        }
        if (normalized == Integer.class) {
            return tag.getInt("value");
        }
        if (normalized == Long.class) {
            return tag.getLong("value");
        }
        if (normalized == Float.class) {
            return tag.getFloat("value");
        }
        if (normalized == Double.class) {
            return tag.getDouble("value");
        }
        if (normalized == String.class) {
            return tag.getString("value");
        }
        if (normalized == CompoundTag.class) {
            return tag.getCompound("value").copy();
        }
        throw new IllegalArgumentException("Unsupported quest progress type: " + type.getName());
    }

    public static Object completedValue(Class<?> type, CompoundTag currentProgress) {
        Class<?> normalized = normalize(type);
        if (normalized == Boolean.class) {
            return true;
        }
        Object current = decode(normalized, currentProgress);
        return current == null ? defaultValue(normalized) : current;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Integer.class) {
            return 0;
        }
        if (type == Long.class) {
            return 0L;
        }
        if (type == Float.class) {
            return 0.0F;
        }
        if (type == Double.class) {
            return 0.0D;
        }
        if (type == String.class) {
            return "";
        }
        if (type == CompoundTag.class) {
            return new CompoundTag();
        }
        return null;
    }

    public static Class<?> normalize(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return type;
    }
}
