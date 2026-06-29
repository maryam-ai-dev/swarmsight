package com.swarmsight.authority.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * A guard is data. It raises the required level when its condition holds. The
 * condition is a list of clauses that must all hold. Each clause names an input
 * key and an operator.
 *
 * @param when     all clauses must hold for the guard to trigger
 * @param raiseTo  the level the required level is raised to when triggered
 * @param source   provenance: where this rule comes from, for the audit trail
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Guard(
        String name,
        List<Clause> when,
        Level raiseTo,
        String reasonCode,
        String brief,
        String source) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clause(String key, Op op) {

        public enum Op {
            /** The input is present and truthy. */
            IS_TRUE,
            /** The input is absent, null, or blank. */
            IS_ABSENT
        }

        public boolean holds(Map<String, Object> inputs) {
            Object v = inputs.get(key);
            return switch (op) {
                case IS_TRUE -> isTruthy(v);
                case IS_ABSENT -> isAbsent(v);
            };
        }

        private static boolean isTruthy(Object v) {
            if (v instanceof Boolean b) {
                return b;
            }
            return v != null && "true".equalsIgnoreCase(v.toString());
        }

        private static boolean isAbsent(Object v) {
            if (v == null) {
                return true;
            }
            if (v instanceof String s) {
                return s.isBlank();
            }
            if (v instanceof Boolean b) {
                return !b;
            }
            return false;
        }
    }

    /** True when every clause holds for the given inputs. */
    public boolean triggers(Map<String, Object> inputs) {
        return when != null && when.stream().allMatch(c -> c.holds(inputs));
    }
}
