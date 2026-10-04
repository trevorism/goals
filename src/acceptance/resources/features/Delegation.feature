Feature: Delegation
  In order to let an assistant record goals for them, an owner gives another identity access to their goals

  Scenario: Delegate routes require authentication
    When I GET "api/delegate" anonymously
    Then the request is rejected

  Scenario: An owner gives and revokes access
    When I give "1234567890123" edit access to my goals
    Then "1234567890123" is listed as a delegate with edit access
    When I give "1234567890123" read access to my goals
    Then "1234567890123" is listed as a delegate with read access
    When I revoke that access
    Then "1234567890123" is no longer a delegate

  Scenario: Acting for an owner who never granted access is rejected
    When I list goals on behalf of "1234567890123"
    Then the request is rejected

  Scenario: A delegate can see who granted it access
    When I list the owners who granted me access
    Then the request succeeds
