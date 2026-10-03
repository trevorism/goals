Feature: Collection through prompt
  In order to collect values without data entry, goals asks questions in prompt on a daily tick
  and records the answers it can verify with prompt

  Scenario: Collection routes require authentication
    When I POST "api/collect/tick" anonymously
    Then the request is rejected
    When I POST "api/collect/provision" anonymously
    Then the request is rejected
    When I POST "api/event/questionAnswered" anonymously
    Then the request is rejected
    When I GET "api/profile" anonymously
    Then the request is rejected

  Scenario: Answers to questions goals didn't ask are ignored
    When an answered event arrives for a question goals never asked
    Then the event is ignored

  Scenario: The profile stores a valid timezone
    When I read my profile
    Then the profile has a timezone
    When I set my timezone to "America/New_York"
    Then my timezone is "America/New_York"
    When I try to set my timezone to "Mars/Olympus"
    Then the request is rejected
