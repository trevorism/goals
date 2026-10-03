package com.trevorism.service

import com.trevorism.model.Choice

import static com.trevorism.service.Validation.require

class MetricChoices {

    static final String YES = "yes"
    static final String NO = "no"
    static final List<Choice> YES_NO = [new Choice(value: YES, label: "Yes"), new Choice(value: NO, label: "No")]

    private static final int MINIMUM_CHOICES = 2

    static List<Choice> yesNo() {
        YES_NO.collect { new Choice(value: it.value, label: it.label) }
    }

    static List<Choice> normalize(List<Choice> choices) {
        if (!choices) {
            return []
        }
        List<String> assignedValues = []
        choices.collect { Choice choice ->
            String label = choice?.label?.trim()
            String value = choice?.value?.trim() ?: deriveValue(label)
            String uniqueValue = makeUnique(value, assignedValues)
            assignedValues << uniqueValue
            new Choice(value: uniqueValue, label: label)
        }
    }

    static void validate(List<Choice> choices) {
        require(choices.size() >= MINIMUM_CHOICES, "a choice metric requires at least ${MINIMUM_CHOICES} choices")
        require(choices.every { it.label }, "every choice requires a label")
        require(choices*.value.toSet().size() == choices.size(), "choice values must be unique")
    }

    private static String deriveValue(String label) {
        String slug = label?.toLowerCase()?.replaceAll(/[^a-z0-9]+/, "-")?.replaceAll(/(^-+)|(-+$)/, "")
        slug ?: "choice"
    }

    private static String makeUnique(String value, List<String> assignedValues) {
        if (!assignedValues.contains(value)) {
            return value
        }
        int suffix = 2
        while (assignedValues.contains("${value}-${suffix}".toString())) {
            suffix++
        }
        "${value}-${suffix}"
    }
}
