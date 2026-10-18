package com.trevorism.model.measurement

import com.fasterxml.jackson.annotation.JsonIgnore

class Measurement {

    String id
    String label
    String description
    String metricType
    String unit

    @JsonIgnore
    List<String> allowedRoles = []
}
