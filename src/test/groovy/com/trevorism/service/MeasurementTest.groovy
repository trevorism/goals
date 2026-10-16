package com.trevorism.service

import com.trevorism.https.SecureHttpClient
import com.trevorism.secure.Roles
import groovy.json.JsonOutput
import org.junit.jupiter.api.Test

class MeasurementTest {

    private final MeasurementCatalog catalog = new MeasurementCatalog()

    @Test
    void testTheCatalogOnlyOffersMeasurementsTheRolesAllow() {
        assert catalog.availableTo([Roles.ADMIN])*.id == [MeasurementCatalog.BILLING_PAYING_CUSTOMERS]
        assert catalog.availableTo([Roles.USER, Roles.SYSTEM]).isEmpty()
        assert catalog.availableTo(null).isEmpty()
        assert catalog.find("unknown") == null
    }

    @Test
    void testPayingCustomersCountsDistinctOwnersOfConfirmedRealMoneyReceives() {
        List<Map> transactions = [
                [type: "RECEIVE", status: "CONFIRMED", provider: "stripe", ownerId: "c1"],
                [type: "RECEIVE", status: "CONFIRMED", provider: "stripe", ownerId: "c1"],
                [type: "RECEIVE", status: "CONFIRMED", provider: "xrp-mainnet", ownerId: "c2"],
                [type: "RECEIVE", status: "CONFIRMED", provider: "cardano-preprod", ownerId: "c3"],
                [type: "RECEIVE", status: "PENDING", provider: "stripe", ownerId: "c4"],
                [type: "SEND", status: "CONFIRMED", provider: "cardano-mainnet", ownerId: "v1"],
                [type: "RECEIVE", status: "CONFIRMED", provider: "stripe"]
        ]

        assert HttpMeasurementReader.payingCustomers(transactions) == 2d
    }

    @Test
    void testTheReaderGetsBillingPaymentsWithTheAppClient() {
        List<String> urls = []
        SecureHttpClient client = [get: { String url ->
            urls << url
            JsonOutput.toJson([[type: "RECEIVE", status: "CONFIRMED", provider: "stripe", ownerId: "c1"]])
        }] as SecureHttpClient

        Double value = new HttpMeasurementReader(client).read(MeasurementCatalog.BILLING_PAYING_CUSTOMERS)

        assert value == 1d
        assert urls == ["https://billing.trade.trevorism.com/api/payment"]
    }

    @Test
    void testTheReaderRejectsUnknownMeasurements() {
        try {
            new HttpMeasurementReader(null).read("github.anything")
            assert false
        } catch (IllegalArgumentException expected) {
            assert expected.message.contains("github.anything")
        }
    }
}
