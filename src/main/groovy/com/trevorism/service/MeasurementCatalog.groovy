package com.trevorism.service

import com.trevorism.model.measurement.Measurement
import com.trevorism.model.types.MetricType
import com.trevorism.secure.Roles
import jakarta.inject.Singleton

@Singleton
class MeasurementCatalog {

    static final String BILLING_PAYING_CUSTOMERS = "billing.paying-customers"

    private static final List<Measurement> MEASUREMENTS = [
            new Measurement(id: BILLING_PAYING_CUSTOMERS, label: "Paying customers (billing)",
                    description: "Distinct customers with a confirmed real-money payment in billing",
                    metricType: MetricType.NUMERIC, unit: "customers", allowedRoles: [Roles.ADMIN])
    ]

    List<Measurement> availableTo(Collection<String> roles) {
        MEASUREMENTS.findAll { isAllowed(it, roles) }
    }

    Measurement find(String id) {
        MEASUREMENTS.find { it.id == id }
    }

    static boolean isAllowed(Measurement measurement, Collection<String> roles) {
        measurement.allowedRoles.any { it in (roles ?: []) }
    }
}
