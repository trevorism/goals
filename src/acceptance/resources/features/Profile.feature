Feature: Profile
  In order to judge progress by my local days, goals keeps my timezone

  Scenario: The profile requires authentication
    When I GET "api/profile" anonymously
    Then the request is rejected

  Scenario: The profile stores a valid timezone
    When I read my profile
    Then the profile has a timezone
    When I set my timezone to "America/New_York"
    Then my timezone is "America/New_York"
    When I try to set my timezone to "Mars/Olympus"
    Then the request is rejected
