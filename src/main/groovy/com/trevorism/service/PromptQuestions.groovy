package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.types.FrequencyType
import com.trevorism.model.types.MetricType

import java.time.format.DateTimeFormatter

class PromptQuestions {

    static final String QUESTION_KIND = "question"

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    static final String NUMBER_ANSWER = "number"

    static Map build(GoalMetric metric, Goal goal, CollectionPeriod period, String ownerId) {
        Map question = [
                text                : text(metric, goal, period),
                kind                : QUESTION_KIND,
                targetIdentityId    : ownerId,
                privateQuestion     : true,
                dueDate             : period.endInstant().toString(),
                choices             : choices(metric),
                allowMultipleAnswers: false
        ]
        if (metric.type == MetricType.NUMERIC) {
            question.answerType = NUMBER_ANSWER
            if (metric.unit) {
                question.unit = metric.unit
            }
        }
        return question
    }

    static String text(GoalMetric metric, Goal goal, CollectionPeriod period) {
        String unit = metric.type == MetricType.NUMERIC && metric.unit ? " (${metric.unit})" : ""
        String base = metric.promptText?.trim() ?: "${goal.title}: ${metric.name}${unit}"
        "${base} — ${periodLabel(period)}${instruction(metric)}".toString()
    }

    static String periodLabel(CollectionPeriod period) {
        switch (period.frequency) {
            case FrequencyType.WEEKLY: return "week of ${WEEK.format(period.start)}"
            case FrequencyType.MONTHLY: return MONTH.format(period.start)
            default: return DAY.format(period.start)
        }
    }

    private static String instruction(GoalMetric metric) {
        switch (metric.type) {
            case MetricType.TEXT: return ". Reply in your own words."
            default: return ""
        }
    }

    static List<Map> choices(GoalMetric metric) {
        switch (metric.type) {
            case MetricType.BOOLEAN:
            case MetricType.CHOICE:
                return (metric.choices ?: []).collect { [value: it.value, label: it.label] }
            case MetricType.SCALE:
                return ((metric.scaleMin as int)..(metric.scaleMax as int)).collect { [value: String.valueOf(it), label: String.valueOf(it)] }
            default:
                return []
        }
    }
}
