Feature: Measured metrics
  In order to collect values without anyone answering, goals reads catalogued measurements
  on its daily tick, and only callers with a measurement's role can attach it to a metric

  Scenario: The measurement catalog requires authentication
    When I GET "api/measurement" anonymously
    Then the request is rejected

  Scenario: Billing measurements are not offered to non-admin callers
    When I list the measurements I can use
    Then "billing.paying-customers" is not offered
    Given a root goal is created
    When I add a metric measured by "billing.paying-customers"
    Then the request is rejected
