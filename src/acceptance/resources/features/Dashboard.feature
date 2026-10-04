Feature: Dashboard
  In order to see at a glance whether long-term goals are on track, a user opens a quiet dashboard

  Scenario: The dashboard requires authentication
    When I GET "api/dashboard" anonymously
    Then the request is rejected

  Scenario: A new root goal appears on the dashboard
    Given a root goal is created
    Then the dashboard shows the root goal with its progress
