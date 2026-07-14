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
    private static final Map<String, Integer> EXTERNAL_RATING_RANK = Map.ofEntries(
            Map.entry("Aaa", 1),
            Map.entry("Aa1", 2),
            Map.entry("Aa2", 3),
            Map.entry("Aa3", 4),
            Map.entry("A1", 5),
            Map.entry("A2", 6),
            Map.entry("A3", 7),
            Map.entry("Baa1", 8),
            Map.entry("Baa2", 9),
            Map.entry("Baa3", 10),
            Map.entry("Ba1", 11),
            Map.entry("Ba2", 12),
            Map.entry("Ba3", 13),
            Map.entry("B1", 14),
            Map.entry("B2", 15),
            Map.entry("B3", 16),
            Map.entry("Caa1", 17),
            Map.entry("Caa2", 18),
            Map.entry("Caa3", 19),
            Map.entry("Ca", 20),
            Map.entry("C", 21));

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
        if (conditions.containsKey("logic") && conditions.containsKey("conditions")) {
            return evaluateEditorConditions(conditions, asset, crrDropMap);
        }

        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("or".equals(key)) {
                // OR: 任一子条件满足即通过
                if (!evaluateOr(value, asset, crrDropMap)) {
                    return false;
                }
            } else if ("and".equals(key)) {
                // AND: 所有子条件满足才通过
                if (!evaluateAnd(value, asset, crrDropMap)) {
                    return false;
                }
            } else if ("crr_drop".equals(key)) {
                // FIX: pass expectedValue so crr_drop:true actually evaluates
                if (!evaluateCrrDrop(asset, crrDropMap, value)) {
                    return false;
                }
            } else if ("default".equals(key) && Boolean.TRUE.equals(value)) {
                // 兜底条件：无条件通过
                return true;
            } else if (value instanceof Map) {
                // 嵌套 JSON 对象 → 范围匹配或枚举匹配
                Map<String, Object> inner = (Map<String, Object>) value;
                if (inner.containsKey("min") || inner.containsKey("max")
                        || inner.containsKey("gte") || inner.containsKey("gt")
                        || inner.containsKey("lte") || inner.containsKey("lt")) {
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

    @SuppressWarnings("unchecked")
    private static boolean evaluateEditorConditions(Map<String, Object> editorJson,
                                                    AssetInput asset,
                                                    Map<String, Integer> crrDropMap) {
        Object rawConditions = editorJson.get("conditions");
        if (!(rawConditions instanceof List<?> conditionItems)) {
            return false;
        }
        String logic = editorJson.getOrDefault("logic", "AND").toString();
        if ("OR".equalsIgnoreCase(logic)) {
            for (Object item : conditionItems) {
                if (item instanceof Map<?, ?> map
                        && evaluateEditorCondition((Map<String, Object>) map, asset, crrDropMap)) {
                    return true;
                }
            }
            return false;
        }

        for (Object item : conditionItems) {
            if (!(item instanceof Map<?, ?> map)
                    || !evaluateEditorCondition((Map<String, Object>) map, asset, crrDropMap)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateEditorCondition(Map<String, Object> condition,
                                                   AssetInput asset,
                                                   Map<String, Integer> crrDropMap) {
        String type = nvl(toStringValue(condition.get("type")));
        String operator = nvl(toStringValue(condition.get("operator")));
        Object value = condition.get("value");

        boolean result = switch (type) {
            case "逾期天数" -> compareNumber(asset.getOverdueDays(), operator, value);
            case "五级分类" -> evaluateEditorIn(asset.getFiveCategory(), operator, condition.get("values"));
            case "CRR 评级下降" -> evaluateCrrDrop(asset, crrDropMap, value);
            case "违约标识" -> evaluateDefaultFlag(asset, value);
            case "逾期天数范围" -> evaluateRangeCondition(asset.getOverdueDays(), condition);
            case "舆情事件" -> evaluateTextEvidence(asset.getOtherRiskInfo(), value)
                    || evaluateTextEvidence(asset.getMediaSentiment(), value);
            case "行业代码" -> evaluateEditorIn(asset.getIndustryCode(), operator, condition.get("values"));
            case "产品类型" -> {
                if ("in".equals(operator) || "not_in".equals(operator)) {
                    yield evaluateEditorIn(asset.getProductType(), operator, condition.get("values"));
                }
                yield evaluateStringEquals(asset.getProductType(), operator, condition.get("value"));
            }
            case "客户名称" -> evaluateTextEvidence(asset.getCustomerName(), value);
            case "客户名称列表" -> evaluateEditorIn(asset.getCustomerName(), operator, condition.get("values"));
            case "EAD均值比" -> compareDouble(asset.getTotalEad(), asset.getBatchEadAvg(), operator, value);
            default -> {
                log.warn("[StageCondition] unknown type='{}' operator='{}' value='{}' assetId={}",
                        type, operator, value, asset.getAssetId());
                yield false;
            }
        };
        log.debug("[StageCondition] type='{}' operator='{}' value='{}' assetId={} → {}",
                type, operator, value, asset.getAssetId(), result);
        return result;
    }

    private static boolean compareNumber(Integer actual, String operator, Object expectedValue) {
        if (actual == null || expectedValue == null) {
            return false;
        }
        int expected = toInt(expectedValue);
        return switch (operator) {
            case "gt" -> actual > expected;
            case "lt" -> actual < expected;
            case "lte" -> actual <= expected;
            case "eq" -> actual == expected;
            case "gte" -> actual >= expected;
            default -> false;
        };
    }

    private static boolean compareDouble(double actual, double batchAvg, String operator, Object expectedValue) {
        if (expectedValue == null) return false;
        double ratio = batchAvg > 0 ? actual / batchAvg : 0;
        double expected = toDouble(expectedValue);
        return switch (operator) {
            case "gt" -> ratio > expected;
            case "gte" -> ratio >= expected;
            case "lt" -> ratio < expected;
            case "lte" -> ratio <= expected;
            case "eq" -> Math.abs(ratio - expected) < 0.0001;
            default -> false;
        };
    }

    private static boolean evaluateStringEquals(String actual, String operator, Object expectedValue) {
        if (actual == null || expectedValue == null) return false;
        if ("eq".equals(operator)) return actual.equals(expectedValue.toString());
        if ("ne".equals(operator)) return !actual.equals(expectedValue.toString());
        return false;
    }

    private static boolean evaluateRangeCondition(Integer actual, Map<String, Object> condition) {
        if (actual == null) return false;
        Integer min = condition.get("min") != null ? toInt(condition.get("min")) : null;
        Integer max = condition.get("max") != null ? toInt(condition.get("max")) : null;
        boolean minEx = Boolean.TRUE.equals(condition.get("minExclusive"));
        boolean maxEx = Boolean.TRUE.equals(condition.get("maxExclusive"));
        if (min != null && max != null && min > max) return false;
        if (min != null && (minEx ? actual <= min : actual < min)) return false;
        if (max != null && (maxEx ? actual >= max : actual > max)) return false;
        return true;
    }

    private static boolean evaluateEditorIn(String actual, String operator, Object rawAllowedValues) {
        if (actual == null || !(rawAllowedValues instanceof List<?> allowedValues)) {
            return false;
        }
        boolean contains = allowedValues.stream().anyMatch(item -> actual.equals(item.toString()));
        return "not_in".equals(operator) ? !contains : contains;
    }

    private static boolean evaluateDefaultFlag(AssetInput asset, Object expectedValue) {
        // 空值或空字符串 → 表示"不关心此字段"，跳过此条件（即匹配所有）
        if (expectedValue == null || expectedValue.toString().isBlank()) {
            return true;
        }
        boolean expected = expectedValue instanceof Boolean b
                ? b
                : "是".equals(expectedValue.toString()) || "Y".equalsIgnoreCase(expectedValue.toString());
        if (asset.getIsNpl() != null) {
            boolean actual = "Y".equalsIgnoreCase(asset.getIsNpl());
            return actual == expected;
        }
        return asset.getDefaultFlag() != null && asset.getDefaultFlag() == expected;
    }

    private static boolean evaluateTextEvidence(String actual, Object expectedValue) {
        if (actual == null || expectedValue == null) {
            return false;
        }
        String expected = expectedValue.toString();
        return expected.isBlank() || actual.contains(expected);
    }

    // ======================== 各条件实现 ========================

    @SuppressWarnings("unchecked")
    private static boolean evaluateOr(Object subConditions,
                                      AssetInput asset,
                                      Map<String, Integer> crrDropMap) {
        if (subConditions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map
                        && evaluateMap((Map<String, Object>) map, asset, crrDropMap)) {
                    return true;
                }
            }
            return false;
        }
        if (subConditions instanceof Map<?, ?> map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                Map<String, Object> single = Map.of(entry.getKey(), entry.getValue());
                if (evaluateMap(single, asset, crrDropMap)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluateAnd(Object subConditions,
                                       AssetInput asset,
                                       Map<String, Integer> crrDropMap) {
        if (subConditions instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)
                        || !evaluateMap((Map<String, Object>) map, asset, crrDropMap)) {
                    return false;
                }
            }
            return true;
        }
        if (subConditions instanceof Map<?, ?> map) {
            return evaluateMap((Map<String, Object>) map, asset, crrDropMap);
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
            boolean minEx = Boolean.TRUE.equals(range.get("minExclusive"));
            if (minEx ? actual <= min : actual < min) {
                return false;
            }
        }
        if (range.containsKey("max")) {
            int max = toInt(range.get("max"));
            boolean maxEx = Boolean.TRUE.equals(range.get("maxExclusive"));
            if (maxEx ? actual >= max : actual > max) {
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
        return evaluateCrrDrop(asset, crrDropMap, null);
    }

    private static boolean evaluateCrrDrop(AssetInput asset,
                                           Map<String, Integer> crrDropMap,
                                           Object expectedValue) {
        // 空值或空字符串 → 表示"不关心此字段"，跳过此条件
        if (expectedValue == null || expectedValue.toString().isBlank()) {
            return true;
        }
        boolean expectDrop = expectedValue == null
                || !Boolean.FALSE.equals(expectedValue)
                || !"否".equals(expectedValue.toString());

        if (crrDropMap == null || crrDropMap.isEmpty()) {
            return !expectDrop;
        }

        boolean hasDrop = evaluateRatingDropCandidate(asset.getGroupId(), "INTERNAL_CRR",
                firstNonBlank(asset.getCrrFinal(), asset.getCrrIntThisYear(), asset.getCrrRating()),
                asset.getCrrIntLastYear(), asset.getRatingDropLevels(), crrDropMap);

        if (!hasDrop) {
            String externalAgency = firstNonBlank(asset.getExtRatingCoThisYear(), asset.getExtRatingCoLastYear());
            if (sameExternalAgency(asset)) {
                hasDrop = evaluateRatingDropCandidate(asset.getGroupId(),
                        externalAgency, asset.getExtRatingThisYear(), asset.getExtRatingLastYear(), null, crrDropMap);
            }
        }

        return expectDrop == hasDrop;
    }

    private static boolean evaluateRatingDropCandidate(String groupId,
                                                       String ratingAgency,
                                                       String currentRating,
                                                       String previousRating,
                                                       Integer precomputedDropLevels,
                                                       Map<String, Integer> crrDropMap) {
        Integer dropLevels = precomputedDropLevels != null
                ? precomputedDropLevels
                : calculateDropLevels(previousRating, currentRating);
        if (currentRating == null || dropLevels == null) {
            return false;
        }

        String key = String.join("|",
                nvl(groupId),
                nvl(ratingAgency),
                nvl(currentRating));
        Integer threshold = crrDropMap.get(key);
        if (threshold == null) {
            return false;  // 该评级无规则 → 不满足
        }
        return dropLevels >= threshold;
    }

    private static Integer calculateDropLevels(String previousRating, String currentRating) {
        Integer previousRank = ratingRank(previousRating);
        Integer currentRank = ratingRank(currentRating);
        if (previousRank == null || currentRank == null) {
            return null;
        }
        return Math.max(0, currentRank - previousRank);
    }

    private static Integer ratingRank(String rating) {
        if (rating == null || rating.isBlank()) {
            return null;
        }
        String trimmed = rating.trim();
        if (trimmed.matches("(?i)CRR\\d+")) {
            return Integer.parseInt(trimmed.replaceAll("(?i)CRR", ""));
        }
        return EXTERNAL_RATING_RANK.get(trimmed);
    }

    private static boolean sameExternalAgency(AssetInput asset) {
        String currentAgency = asset.getExtRatingCoThisYear();
        String previousAgency = asset.getExtRatingCoLastYear();
        return currentAgency == null || previousAgency == null || currentAgency.equals(previousAgency);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String toStringValue(Object value) {
        return value != null ? value.toString() : null;
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

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return 0.0; }
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
