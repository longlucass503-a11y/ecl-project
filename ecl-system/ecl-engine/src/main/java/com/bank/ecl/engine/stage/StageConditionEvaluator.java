package com.bank.ecl.engine.stage;

import com.bank.ecl.engine.core.AssetInput;
import com.bank.ecl.engine.core.Stage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段划分引擎的 JSON 条件匹配器。
 * 解析 {@code tbl_stage_rule.conditions} JSON，评估借据是否满足条件。
 *
 * <p>支持的 JSON 条件类型：
 * <ul>
 *   <li>范围匹配：{"overdue_days": {"min": 31, "max": 90}}</li>
 *   <li>枚举匹配：{"five_category": {"in": ["次级","可疑","损失"]}}</li>
 *   <li>OR 逻辑：{"or": {"sub1": ..., "sub2": ...}}</li>
 *   <li>AND 逻辑：{"and": {"sub1": ..., "sub2": ...}}</li>
 *   <li>CRR 下降：{"crr_drop": {"system": "CRR", "use_lookup": true}}</li>
 *   <li>等值匹配：{"default_flag": true}</li>
 *   <li>兜底：{"default": true}</li>
 * </ul>
 */
public class StageConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(StageConditionEvaluator.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, java.lang.reflect.Field> FIELD_CACHE;

    static {
        Map<String, java.lang.reflect.Field> cache = new HashMap<>();
        for (java.lang.reflect.Field field : AssetInput.class.getDeclaredFields()) {
            field.setAccessible(true);
            cache.put(field.getName(), field);
        }
        FIELD_CACHE = Collections.unmodifiableMap(cache);
    }

    private StageConditionEvaluator() {
        // utility class
    }

    /**
     * @param conditionsJson 阶段规则的 conditions JSON 字符串
     * @param asset          借据（提供字段值）
     * @param crrDropMap     当前评级 → 下降阈值（可为 null）
     * @return true=条件满足
     */
    @SuppressWarnings("unchecked")
    public static boolean evaluate(String conditionsJson,
                                   AssetInput asset,
                                   Map<String, Integer> crrDropMap) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return true;  // 空条件 = 无条件通过
        }

        Map<String, Object> conditions;
        try {
            conditions = mapper.readValue(conditionsJson,
                    new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse conditions JSON: {}", conditionsJson, e);
            return false;
        }

        return evaluateMap(conditions, asset, crrDropMap);
    }

    // ======================== 内部分发 ========================

    @SuppressWarnings("unchecked")
    private static boolean evaluateMap(Map<String, Object> conditions,
                                        AssetInput asset,
                                        Map<String, Integer> crrDropMap) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("or".equals(key)) {
                // OR: 任一子条件满足即通过
                if (!evaluateOr((Map<String, Object>) value, asset, crrDropMap)) {
                    return false;
                }
            } else if ("and".equals(key)) {
                // AND: 所有子条件满足才通过
                if (!evaluateMap((Map<String, Object>) value, asset, crrDropMap)) {
                    return false;
                }
            } else if ("crr_drop".equals(key)) {
                if (!evaluateCrrDrop(asset, crrDropMap)) {
                    return false;
                }
            } else if ("default".equals(key) && Boolean.TRUE.equals(value)) {
                // 兜底条件：无条件通过
                return true;
            } else if (value instanceof Map) {
                // 嵌套 JSON 对象 → 范围匹配或枚举匹配
                Map<String, Object> inner = (Map<String, Object>) value;
                if (inner.containsKey("min") || inner.containsKey("max")) {
                    if (!evaluateRange(key, inner, asset)) {
                        return false;
                    }
                } else if (inner.containsKey("in")) {
                    if (!evaluateIn(key, (List<Object>) inner.get("in"), asset)) {
                        return false;
                    }
                } else {
                    // 未知嵌套结构 → 尝试等值匹配（递归）
                    if (!evaluateMap(inner, asset, crrDropMap)) {
                        return false;
                    }
                }
            } else {
                // 标量值 → 等值匹配
                if (!evaluateEquals(key, value, asset)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ======================== 各条件实现 ========================

    @SuppressWarnings("unchecked")
    private static boolean evaluateOr(Map<String, Object> subConditions,
                                      AssetInput asset,
                                      Map<String, Integer> crrDropMap) {
        // OR: 任一子条件满足即通过
        for (Map.Entry<String, Object> entry : subConditions.entrySet()) {
            // 把每个子条件包装成单 key Map 来复用 evaluateMap
            Map<String, Object> single = Map.of(entry.getKey(), entry.getValue());
            if (evaluateMap(single, asset, crrDropMap)) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateRange(String fieldName,
                                         Map<String, Object> range,
                                         AssetInput asset) {
        Integer actual = getIntField(asset, fieldName);
        if (actual == null) {
            return false;
        }

        if (range.containsKey("min")) {
            int min = toInt(range.get("min"));
            if (actual < min) {
                return false;
            }
        }
        if (range.containsKey("max")) {
            int max = toInt(range.get("max"));
            if (actual > max) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateIn(String fieldName,
                                      List<Object> allowedValues,
                                      AssetInput asset) {
        Object actual = getField(asset, fieldName);
        if (actual == null) {
            return false;
        }
        String actualStr = actual.toString();
        for (Object allowed : allowedValues) {
            if (actualStr.equals(allowed.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateEquals(String fieldName,
                                          Object expectedValue,
                                          AssetInput asset) {
        Object actual = getField(asset, fieldName);
        // 双方都为 null 视为匹配；一方为 null 另一方不为 null 视为不匹配
        if (actual == null && expectedValue == null) {
            return true;
        }
        if (actual == null || expectedValue == null) {
            return false;
        }
        return actual.toString().equals(expectedValue.toString());
    }

    private static boolean evaluateCrrDrop(AssetInput asset,
                                           Map<String, Integer> crrDropMap) {
        if (crrDropMap == null || crrDropMap.isEmpty()) {
            return false;
        }
        String currentRating = asset.getCrrRating();
        Integer dropLevels = asset.getRatingDropLevels();
        if (currentRating == null || dropLevels == null) {
            return false;
        }
        String key = String.join("|",
                nvl(asset.getGroupId()),
                "INTERNAL_CRR",
                "INTERNAL_CRR",
                nvl(currentRating));
        Integer threshold = crrDropMap.get(key);
        if (threshold == null) {
            return false;  // 该评级无规则 → 不满足
        }
        return dropLevels >= threshold;
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }

    // ======================== 反射字段访问 ========================

    /**
     * 从 AssetInput 通过字段名反射获取值。
     * 映射关系：
     * - overdue_days → overdueDays
     * - five_category → fiveCategory
     * - crr_rating → crrRating
     * - default_flag → defaultFlag
     * - media_sentiment → mediaSentiment
     * - repayment_status → （暂无对应字段，返回 null）
     * - holdback_days_met → （暂无对应字段，返回 null）
     */
    private static Object getField(AssetInput asset, String fieldName) {
        String camelName = toCamelCase(fieldName);
        java.lang.reflect.Field field = FIELD_CACHE.get(camelName);
        if (field == null) {
            log.debug("[StageConditionEvaluator] field '{}' (snake: '{}') not found on AssetInput", camelName, fieldName);
            return null;
        }
        try {
            return field.get(asset);
        } catch (IllegalAccessException e) {
            log.debug("[StageConditionEvaluator] cannot access field '{}'", camelName);
            return null;
        }
    }

    private static Integer getIntField(AssetInput asset, String fieldName) {
        Object value = getField(asset, fieldName);
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (int i = 0; i < snakeCase.length(); i++) {
            char c = snakeCase.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }
        return result.toString();
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.error("[StageConditionEvaluator] cannot parse integer from value: {}", value);
            return 0;
        }
    }
}
