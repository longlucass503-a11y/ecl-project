package com.bank.ecl.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates JSON conditions against a field value map.
 * <p>
 * Supported condition formats:
 * <ul>
 *   <li>Legacy: {@code {"industry_codes": ["J","K"]}} — OR semantics: field value matches any array element</li>
 *   <li>Legacy: {@code {"overdue_days_ge": 90}} — numeric greater-or-equal check</li>
 *   <li>Legacy: {@code {"overdue_days_le": 180}} — numeric less-or-equal check</li>
 *   <li>Legacy: {@code {"product_type": "LC"}} — exact equality</li>
 *   <li>Editor:  {@code {"logic":"AND","conditions":[{"type":"逾期天数","operator":"gte","value":30}]}}</li>
 * </ul>
 * All conditions are combined with AND logic (editor format).
 */
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String SUFFIX_GE = "_ge";
    private static final String SUFFIX_LE = "_le";

    /** 中文类型名 -> fieldMap 中的字段名 */
    private static final Map<String, String> TYPE_TO_FIELD = Map.ofEntries(
            Map.entry("逾期天数", "overdueDays"),
            Map.entry("五级分类", "fiveCategory"),
            Map.entry("行业代码", "industryCode"),
            Map.entry("违约标识", "defaultFlag"),
            Map.entry("逾期天数范围", "overdueDays"),
            Map.entry("舆情事件", "otherRiskInfo"),
            Map.entry("CRR 评级下降", "ratingDropLevels"),
            Map.entry("是否不良", "isNpl"),
            Map.entry("客户类型", "customerType"),
            Map.entry("担保方式", "guaranteeType"),
            Map.entry("资产状态", "assetStatus"),
            Map.entry("行业分类", "industry"),
            Map.entry("CRR评级", "crrRating"),
            Map.entry("客户名称", "customerName"),
            Map.entry("产品类型", "productType"),
            Map.entry("客户名称列表", "customerName"),
            Map.entry("EAD均值比", "eadAvg")
    );

    private ConditionEvaluator() {
        // utility class
    }

    /**
     * Evaluate whether the given JSON conditions match the field map.
     *
     * @param conditionsJson JSON string representing conditions
     * @param fieldMap       field name to value map
     * @return true if all conditions are satisfied
     */
    @SuppressWarnings("unchecked")
    public static boolean evaluate(String conditionsJson, Map<String, Object> fieldMap) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return true;
        }
        if (fieldMap == null) {
            return false;
        }

        Map<String, Object> conditions;
        try {
            conditions = mapper.readValue(conditionsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to parse conditions JSON: {}", conditionsJson, e);
            return false;
        }

        // ── 编辑器格式优先检测 ──
        if (conditions.containsKey("logic") && conditions.containsKey("conditions")) {
            return evaluateEditorFormat(conditions, fieldMap);
        }

        // ── 旧格式 ──
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object conditionValue = entry.getValue();

            if (key.endsWith(SUFFIX_GE)) {
                String fieldName = key.substring(0, key.length() - SUFFIX_GE.length());
                if (!evaluateGe(fieldName, conditionValue, fieldMap)) {
                    return false;
                }
            } else if (key.endsWith(SUFFIX_LE)) {
                String fieldName = key.substring(0, key.length() - SUFFIX_LE.length());
                if (!evaluateLe(fieldName, conditionValue, fieldMap)) {
                    return false;
                }
            } else if (conditionValue instanceof List<?> arrayValues) {
                // OR semantics: field value must match at least one element in the array
                if (!evaluateOr(key, arrayValues, fieldMap)) {
                    return false;
                }
            } else {
                // Exact equality
                if (!evaluateEquals(key, conditionValue, fieldMap)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 支持编辑器格式：{"logic":"AND","conditions":[{"type":"逾期天数","operator":"gte","value":30}]}
     */
    @SuppressWarnings("unchecked")
    private static boolean evaluateEditorFormat(Map<String, Object> editorJson, Map<String, Object> fieldMap) {
        Object rawConditions = editorJson.get("conditions");
        if (!(rawConditions instanceof List<?> conditionItems)) {
            return false;
        }
        String logic = editorJson.getOrDefault("logic", "AND").toString();

        if ("OR".equalsIgnoreCase(logic)) {
            for (Object item : conditionItems) {
                if (item instanceof Map<?, ?> map
                        && evaluateEditorCondition((Map<String, Object>) map, fieldMap)) {
                    return true;
                }
            }
            return false;
        }

        // AND 逻辑（默认）
        for (Object item : conditionItems) {
            if (!(item instanceof Map<?, ?> map)
                    || !evaluateEditorCondition((Map<String, Object>) map, fieldMap)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估单条编辑器条件
     */
    private static boolean evaluateEditorCondition(Map<String, Object> cond, Map<String, Object> fieldMap) {
        String type = nvl(str(cond.get("type")));
        String operator = nvl(str(cond.get("operator")));
        Object rawValue = cond.get("value");
        List<?> rawValues = safeList(cond.get("values"));

        String fieldName = TYPE_TO_FIELD.getOrDefault(type, type);
        Object fieldValue = fieldMap.get(fieldName);

        // 通用 in / not_in（适用于行业代码、五级分类等）
        if ("in".equals(operator) || "not_in".equals(operator)) {
            boolean found = false;
            for (Object allowed : rawValues) {
                if (Objects.equals(str(fieldValue), str(allowed))) {
                    found = true;
                    break;
                }
            }
            return "in".equals(operator) ? found : !found;
        }

        // 数值比较（逾期天数等）：fieldValue 为 null 时不匹配
        if (fieldValue == null) {
            if ("gt".equals(operator) || "gte".equals(operator) || "lt".equals(operator) || "lte".equals(operator)) return false;
        }
        if ("gt".equals(operator)) return compareNum(fieldValue, rawValue) > 0;
        if ("gte".equals(operator)) return compareNum(fieldValue, rawValue) >= 0;
        if ("lt".equals(operator)) return compareNum(fieldValue, rawValue) < 0;
        if ("lte".equals(operator)) return compareNum(fieldValue, rawValue) <= 0;
        // eq / ne：优先用 values[0]（编辑器格式），其次用 value
        if ("eq".equals(operator)) {
            Object expected = !rawValues.isEmpty() ? rawValues.get(0) : rawValue;
            return Objects.equals(str(fieldValue), str(expected));
        }
        if ("ne".equals(operator)) {
            Object expected = !rawValues.isEmpty() ? rawValues.get(0) : rawValue;
            return !Objects.equals(str(fieldValue), str(expected));
        }

        // 逾期天数范围
        if ("range".equals(operator) && type.equals("逾期天数范围")) {
            Integer min = rawValues.size() > 0 ? toInt(rawValues.get(0)) : null;
            Integer max = rawValues.size() > 1 ? toInt(rawValues.get(1)) : null;
            int actual = toInt(fieldValue);
            if (min != null && actual < min) return false;
            if (max != null && actual > max) return false;
            return true;
        }

        if ("contains".equals(operator)) {
            return str(fieldValue).contains(str(rawValue));
        }

        // CRR 评级下降：是 -> ratingDropLevels > 0，否 -> ratingDropLevels == 0 或 null
        if (type.equals("CRR 评级下降")) {
            int dropLevels = toInt(fieldValue);
            return ("是".equals(operator)) ? (dropLevels > 0) : (dropLevels <= 0);
        }

        // 违约标识：是 -> isNpl=="Y" || defaultFlag==true，否 -> 反之
        if (type.equals("违约标识")) {
            boolean isDefault = ("Y".equalsIgnoreCase(str(fieldValue))) || (Boolean.TRUE.equals(fieldValue));
            return ("是".equals(operator)) ? isDefault : !isDefault;
        }

        // 默认精确匹配
        return Objects.equals(str(fieldValue), str(rawValue));
    }

    private static int compareNum(Object actual, Object expected) {
        return Double.compare(toDouble(actual), toDouble(expected));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            log.warn("cannot parse number: {}", value);
            return Double.NaN;
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    @SuppressWarnings("unchecked")
    private static List<?> safeList(Object o) {
        if (o instanceof List<?>) return (List<?>) o;
        return List.of();
    }

    // ── 旧格式方法 ──
    private static boolean evaluateGe(String fieldName, Object conditionValue, Map<String, Object> fieldMap) {
        Object fieldValue = fieldMap.get(fieldName);
        if (fieldValue == null) { return false; }
        return toDouble(fieldValue) >= toDouble(conditionValue);
    }

    private static boolean evaluateLe(String fieldName, Object conditionValue, Map<String, Object> fieldMap) {
        Object fieldValue = fieldMap.get(fieldName);
        if (fieldValue == null) { return false; }
        return toDouble(fieldValue) <= toDouble(conditionValue);
    }

    private static boolean evaluateOr(String fieldName, List<?> arrayValues, Map<String, Object> fieldMap) {
        Object fieldValue = fieldMap.get(fieldName);
        if (fieldValue == null) { return false; }
        String fieldStr = fieldValue.toString();
        for (Object item : arrayValues) {
            if (fieldStr.equals(item.toString())) { return true; }
        }
        return false;
    }

    private static boolean evaluateEquals(String fieldName, Object conditionValue, Map<String, Object> fieldMap) {
        Object fieldValue = fieldMap.get(fieldName);
        if (fieldValue == null) { return false; }
        return fieldValue.toString().equals(conditionValue.toString());
    }
}
