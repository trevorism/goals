package com.trevorism.model.collection

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class CollectionResult {

    int measured
    int alreadyRecorded
    int failed
}
