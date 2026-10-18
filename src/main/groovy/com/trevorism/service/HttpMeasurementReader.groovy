package com.trevorism.service

import com.trevorism.https.SecureHttpClient
import groovy.json.JsonSlurper
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class HttpMeasurementReader implements MeasurementReader {

    static final String BILLING_URL = "https://billing.trade.trevorism.com"

    private final SecureHttpClient client

    HttpMeasurementReader(@Named("appClientSecureHttpClient") SecureHttpClient client) {
        this.client = client
    }

    @Override
    Double read(String measurementId) {
        switch (measurementId) {
            case MeasurementCatalog.BILLING_PAYING_CUSTOMERS:
                List<Map> transactions = new JsonSlurper().parseText(client.get("${BILLING_URL}/api/payment".toString())) as List<Map>
                return payingCustomers(transactions)
            default:
                throw new IllegalArgumentException("Unknown measurement ${measurementId}")
        }
    }

    static double payingCustomers(List<Map> transactions) {
        transactions.findAll { it.type == "RECEIVE" && it.status == "CONFIRMED" && isRealMoney(it.provider as String) && it.ownerId }
                .collect { it.ownerId }
                .unique()
                .size()
    }

    private static boolean isRealMoney(String provider) {
        provider == "stripe" || provider?.endsWith("-mainnet")
    }
}
