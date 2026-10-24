Feature: Collection and profile
  In order to record measured metrics without data entry, goals runs a daily tick,
  and it keeps each owner's timezone so periods are local

  Scenario: Collection routes require authentication
    When I POST "api/collect/tick" anonymously
    Then the request is rejected
    When I POST "api/collect/provision" anonymously
    Then the request is rejected
    When I GET "api/profile" anonymously
    Then the request is rejected

  Scenario: The profile stores a valid timezone
    When I read my profile
    Then the profile has a timezone
    When I set my timezone to "America/New_York"
    Then my timezone is "America/New_York"
    When I try to set my timezone to "Mars/Olympus"
    Then the request is rejected
