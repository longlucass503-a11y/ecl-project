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

/**
 * Evaluates JSON conditions against a field value map.
 * <p>
 * Supported condition formats:
 * <ul>
 *   <li>{@code {"industry_codes": ["J","K"]}} — OR semantics: field value matches any array element</li>
 *   <li>{@code {"overdue_days_ge": 90}} — numeric greater-or-equal check</li>
 *   <li>{@code {"overdue_days_le": 180}} — numeric less-or-equal check</li>
 *   <li>{@code {"product_type": "LC"}} — exact equality</li>
 * </ul>
 * All conditions are combined with AND logic.
 */
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String SUFFIX_GE = "_ge";
    private static final String SUFFIX_LE = "_le";

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

    private static boolean evaluateGe(String fieldName, Object conditionValue, Map<String, Object> fieldMap) {
        Object fieldValue = fieldMap.get(fieldName);
        if (fieldValue == null) {
            return false;
        }
        double cond = toDouble(conditionValue);
        double actual = toDouble(fieldValue);
        return actual >= cond;
    }

    private static boolean evaluateLe(String fieldName, Object conditionValue, Map<String, Object> fieldMap) {
        Object fieldValue = fieldMap.get(fieldName);
        if (fieldValue == null) {
            return false;
        }
        double cond = toDouble(conditionValue);
        double actual = toDouble(fieldValue);
        return actual <= cond;
    }

    private static boolean evaluateOr(String fieldName, List<?> arrayValues, Map<String, Object> fieldMap) {
        Object fieldValue = fieldMap.get(fieldName);
        if (fieldValue == null) {
            return false;
        }
        String fieldStr = fieldValue.toString();
        for (Object item : arrayValues) {
            if (fieldStr.equals(item.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateEquals(String fieldName, Object conditionValue, Map<String, Object> fieldMap) {
        Object fieldValue = fieldMap.get(fieldName);
        if (fieldValue == null) {
            return false;
        }
        return fieldValue.toString().equals(conditionValue.toString());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse numeric value: {}", value);
            return Double.NaN;
        }
    }
}
