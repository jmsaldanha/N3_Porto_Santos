package org.example.model;

import java.util.OptionalInt;

/**
 * Conflito de agendamento (RN001 sobreposição / RN002 maré baixa).
 * Carrega, quando existir, o próximo dia de chegada livre sugerido (RF011 - E1).
 */
public class ScheduleConflictException extends BusinessRuleException {

    private final OptionalInt suggestedStartDay;

    public ScheduleConflictException(String rule, String message, OptionalInt suggestedStartDay) {
        super(rule, message);
        this.suggestedStartDay = suggestedStartDay == null ? OptionalInt.empty() : suggestedStartDay;
    }

    public OptionalInt getSuggestedStartDay() {
        return suggestedStartDay;
    }
}
