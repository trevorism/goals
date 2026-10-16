package com.trevorism.support

import com.trevorism.service.MeasurementReader

class FakeMeasurementReader implements MeasurementReader {

    final Map<String, Double> values = [:]
    final List<String> reads = []
    boolean failing

    @Override
    Double read(String measurementId) {
        if (failing) {
            throw new IllegalStateException("billing is down")
        }
        reads << measurementId
        values[measurementId]
    }
}
