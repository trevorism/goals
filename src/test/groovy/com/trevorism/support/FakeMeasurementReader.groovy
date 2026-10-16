package com.trevorism.support

import com.trevorism.service.MeasurementReader

class FakeMeasurementReader implements MeasurementReader {

    final Map<String, Double> values = [:]
    final List<String> reads = []
    boolean failing

    @Override
    Double read(String measurementId) {
        reads << measurementId
        if (failing) {
            throw new IllegalStateException("billing is down")
        }
        values[measurementId]
    }
}
