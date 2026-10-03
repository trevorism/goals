package com.trevorism.model.collection

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class CollectionResult {

    int asked
    int alreadyRecorded
    int missed
    int failed
}
