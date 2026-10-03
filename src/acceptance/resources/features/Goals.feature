Feature: Goal trees
  In order to track long-term goals, a user manages a private tree of goals, metrics,
  observations and adjustments

  Scenario: Goal routes require authentication
    When I GET "api/goal" anonymously
    Then the request is rejected
    When I create a goal anonymously
    Then the request is rejected

  Scenario: Build, measure and delete a goal tree
    Given a root goal is created
    Then the root goal is active and owned by the caller
    And the root goal is listed
    When a step is added under the root goal
    And a yes/no metric is added to the step
    And "yes" is recorded for the metric
    And an adjustment targeting the metric is recorded
    Then the tree shows the step with its metric
    And the observation is labelled "Yes"
    And the adjustment is attached to the root goal
    When the root goal is deleted
    Then the step and its metric are gone

  Scenario: Unknown goals are not found
    When I request the goal "1"
    Then the request is rejected

  Scenario: A child goal must fit within its parent's dates
    Given a root goal is created
    When I add a child that ends after the root goal
    Then the request is rejected
