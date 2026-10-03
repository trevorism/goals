Feature: Today
  In order to keep tracking with little effort, a user sees which metrics still need a value
  for the current day, week or month

  Scenario: Today requires authentication
    When I GET "api/today" anonymously
    Then the request is rejected

  Scenario: A metric is due until a value is recorded for the period
    Given a root goal is created
    And a daily number metric is added to the root goal
    Then today lists the metric as due
    When a value is recorded for today
    Then today no longer lists the metric

  Scenario: The date must be a calendar day
    When I ask what is due on "10-03-2026"
    Then the request is rejected
