package org.example.model;

/** Violação de uma regra de negócio do DERS (RN001, RN002, ...). */
public class BusinessRuleException extends RuntimeException {

    private final String rule;

    public BusinessRuleException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    /** Identificador da regra violada, ex.: "RN001". */
    public String getRule() {
        return rule;
    }
}
