## 0.9.0

- Delegation: on the new Settings page, give another identity (such as the Claude MCP `agent`) read or read-and-edit access to your goals. It acts for you by adding `?onBehalfOf=<your id>` to its requests. Delegates can never delete, and you can revoke access at any time.
- Goals and values a delegate creates show who added them.
- New routes: `GET/POST /api/delegate`, `DELETE /api/delegate/{id}`, `GET /api/delegate/granted`.

## 0.8.1

- Errors now show the real reason instead of "Bad Request", e.g. "A sub-goal's dates must fall within its parent's: Oct 3, 2026 – Nov 7, 2026".
- The sub-goal form shows the parent's date range and checks it before saving.
- Rejected requests are logged with their reason.

## 0.8.0

- The home page is now a quiet dashboard. Each goal shows its status, progress against the plan, time elapsed, and one headline, such as the projected end value of its most concerning metric or the share of Yes answers for a habit.
- A "Needs you" strip appears only when something is stuck: an answer goals couldn't record, three or more unanswered prompt questions in a row, or an active goal past its end date.
- New route `GET /api/dashboard`.

## 0.7.0

- Numeric metrics are asked in prompt as number questions with their unit, so you answer in a number input instead of free text. Answers use prompt's numeric value. Needs prompt 1.3.0.

## 0.6.1

- Fix provisioning: the base URL lost its `https:` because Micronaut reads every `:` in an inline placeholder default as another fallback. The URL is now a constant in code.

## 0.6.0

- Metrics can be collected through prompt: choose "Ask me in prompt" and goals sends you one private question per day, week or month, with Yes/No, scale or choice buttons, or a text reply for numbers.
- Answers are recorded automatically after goals checks them with prompt. Unanswered periods become missed, and unreadable answers are set aside.
- A daily tick (`POST /api/collect/tick`) asks due questions; `POST /api/collect/provision` sets up the tick and the answer subscription.
- Your timezone is saved from the browser, so periods and question due times are local.
- Deleting a goal or metric also removes its open questions.

## 0.5.0

- Today page: every metric that still needs a value for its current day, week or month, grouped by goal, with one-tap entry. It uses your local date.
- Adjustments now show on the charts as labelled markers, and each metric compares before and after: the weekly rate of change for numbers and scales, the share of Yes answers for habits.
- Progress reloads when you add or delete an adjustment.
- Metrics remember the date of their latest value (`lastObservedAt`, replacing `lastCollectedAt`). Metrics recorded before this release show as due until their next value.

## 0.4.0

- Progress: each goal shows its status (ahead, on track, at risk, behind, no data), outcome and effort meters with a marker for where the plan says it should be, and how much of its time has passed. The tree shows each sub-goal's progress.
- Metrics show their score, status and trend: a projection to the end date and when the target will be reached for numbers and scales, adherence and streaks for yes/no habits.
- Charts for every metric type except text: values with the plan, target and trend lines, a rolling adherence line for yes/no habits, and answers on a worst-to-best axis for choices.
- New route `GET /api/goal/{id}/progress` computes progress for a goal's whole subtree.
- Local `gradle run` rebuilds the UI when its source changes.

## 0.3.0

- Goals UI: a goal list, a goal page with a collapsible tree of sub-goals, editing, status changes (complete, abandon, reopen) and delete.
- Metrics UI: add and edit metrics of all five types, and record values with a control for each type (number, Yes/No, scale points, choices, text), with an optional date and note.
- Adjustments UI: log behavior changes on a goal and link them to metrics.
- The goal tree response always includes its metrics and children lists, even when they're empty.

## 0.2.0

- Goal trees: create root goals, add sub-goals and steps, and load a whole subtree with its metrics.
- Metrics of five types (numeric, yes/no, scale, choice, text) with manual observations, plus adjustments that record behavior changes.
- Every goal, metric, observation and adjustment is private to its owner; other users' ids return 404.
- Design doc in docs/design.md.

## 0.1.0

Initial release