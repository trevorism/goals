# Goals: design

Status: draft, 2026-10-03. This is the design for the `goals` service on the Trevorism platform. When the
repo exists, it moves to `goals/docs/design.md`.

## 1. Vision

Goals turns long-term intentions into outcomes. Each goal goes through a loop:

1. **Define** the goal: a long-term outcome, a timeframe, and how success is measured.
2. **Decompose** it into sub-goals, and decompose those until each leaf is a concrete step that can
   be done within about a week.
3. **Measure** progress at every level, with numeric and qualitative metrics. Measurements are
   collected by hand, by asking the user through `prompt`, or automatically.
4. **Learn:** compare progress with the plan, record adjustments (behavior changes), and see which
   changes moved the trend.
5. **Automate** steps one level at a time, from "I do this" toward "the platform does this". A goal
   whose steps are all automated is satisfied by the system rather than by deliberate work.

The central model is a **goal tree whose leaves can execute**. Tracking exists to show whether the
tree is working.

### Guiding principles

- **Lead and lag measures.** The metrics on a node measure its *outcome* (lag). Its children's rolled-up
  progress measures the *effort* that should cause the outcome (lead). If effort rises while the
  outcome stays flat, the decomposition is wrong. The UI shows both numbers.
- **The human approves structure and spend.** The system can propose sub-goals and actions. Nothing
  is created, sent or paid for without approval, unless the user has explicitly automated that step.
- **Use the platform.** Use the `prompt`, `schedule`, `event`, `email`, `chat` and datastore services
  rather than building parallel mechanisms.
- **Start as one service, and keep the split possible.** Collection and measurement sit behind
  interfaces, so a later `measure` service can be extracted by moving code.
- **Goals are private.** Every read and write is scoped to the owner. Public or shared goals come
  later.

## 2. First goals (dogfooding)

These three goals are test cases for every phase. Each one exercises a different part of the system.

### 2.1 Build the goals application (outcome, ends 2027-03-31)
- Lag metrics:
  - `phases shipped`: numeric, 0 → 5, collected by hand.
  - `days I used goals this week`: numeric, target ≥ 5, asked weekly through prompt.
- Children:
  - Phase 1 goal tree (milestone). Its steps are PRs 1a–1d (section 12). Step metric: done.
  - Phase 2 collection (milestone).
  - ... one milestone per phase.
- Automation:
  - `PRs merged this week` is measured automatically: an HTTP pull from the `github` service, or a
    count of release events.
  - The "deploy" step is already automated by CI. It is the first example of a leaf the platform
    satisfies.

### 2.2 Improve healthspan (outcome, 12 months)
- Lag metrics:
  - `resting heart rate`: numeric, decrease, weekly.
  - `body weight`: numeric, decrease, daily.
  - `energy`: scale 1–5, daily.
- Children:
  - **Cardio base** (milestone)
    - Step: walk 30 minutes per day (`boolean`, target rate 6/7, asked daily through prompt).
    - Step: zone 2 training 3×/week (`boolean`, weekly target 3).
  - **Sleep** (milestone)
    - Metric: `sleep quality` (scale 1–5).
    - Step: no screens after 22:00 (`boolean`).
  - **Nutrition** (milestone)
    - Metric: `meal quality` (choice: poor / ok / good, scored 0 / 0.5 / 1).
- Adjustments, for example "started walking after lunch" or "moved caffeine before noon". These are
  the test cases for segmented fits.
- Automation: health metrics start at the `reminded` level. A future health data source (e.g. a
  health-dash feed) moves them to `measured`.

### 2.3 Get paying customers on the Trevorism platform (outcome, ends 2027-06-30)
- Lag metrics:
  - `paying customers`: numeric, 0 → N, **measured** by an HTTP pull from `billing`
    (`GET /api/payment`, counting distinct customers with confirmed receives).
  - `MRR`: numeric, from `stripe` or `billing`.
- Children:
  - Choose a first offering (milestone).
  - Pricing and checkout (milestone). Its steps reuse the `stripe` subscription session work that
    already exists.
  - Landing page on `homepage` (milestone).
  - Outreach (milestone). Step: contact 5 prospects per week (numeric, weekly). The `approved`
    automation drafts outreach emails through `chat`, and the user approves each one through a
    prompt approval before `email` sends it.
- This goal is the main test case for the automation ladder and its guardrails.

## 3. Architecture

```
                       ┌──────────────────────────── goals (Micronaut + Vue) ───────────────────────────┐
 browser ── /api/auth ─┤ ui-auth session │ goal │ measurement │ analysis │ decompose │ automation │ notify │
                       └──────┬──────────────┬───────────┬───────────┬──────────┬──────────┬─────────┘
                              │              │           │           │          │          │
                       datastore svc     prompt svc   event svc   chat svc   schedule   email svc
                       (all entities)   (ask/approve) (subscribe) (decompose) (daily tick)
```

- **One service, `goals`:**
  - The Vue 3 + Vite frontend is in `src/app`. It uses Vuestic, Tailwind, `@trevorism/ui-header-bar`,
    `@trevorism/ui-auth`, chart.js, vue-chartjs and `chartjs-plugin-annotation`.
  - The backend uses Micronaut + Groovy, with `micronaut-ui-auth` for `/api/auth/*`.
- **Persistence:** Datastore, through `FastDatastoreRepository`, using goals' own app credentials
  (`AppClientSecureHttpClient`). Ownership is enforced inside goals (section 9). At the expected volume
  (hundreds of nodes, and daily observations for each metric), filtering by `rootid` or `metricid` and
  computing in memory is enough. BigQuery isn't needed.
- **Package layout:** the template's layer packages (`com.trevorism.{controller, service, model,
  config}`). Collection and measurement code is reached only through service interfaces, so a later
  `measure` service can take those classes with it.
- **Hosting:** App Engine, F1 to start. The only background work is one daily schedule tick.
  Hourly jobs cost about 0.8 instance-hours per clock hour, so we avoid them.

## 4. Domain model

Code uses camelCase field names (`ownerId`, `rootId`); datastore stores them lowercased, and
datastore-client matches them case-insensitively. The tables below use the stored lowercase names.

- **Kinds:** a Datastore kind is the class name lowercased, and kinds share a namespace with other
  apps. The classes are therefore `Goal`, `GoalMetric`, `GoalObservation` and `GoalAdjustment`.
- No entity has a field named `key`, because datastore rejects it with a 400.
- Enums are persisted as strings.
- Every entity has `ownerid`. That is the only ownership boundary, so there's no `tenantid` until
  shared goals need one.
- **Root goals:** a root has `rootid` empty, and its tree root is its own id. Descendants carry the
  root's id, so one filter loads a whole tree.
- **Updates are patches:** datastore ignores null fields on update, so a field can't be cleared once
  it's set.
- **Unknown ids:** datastore returns a 500 for an unknown id, so `OwnedRepository` turns it into a 404.

Every entity is **flat**: fields are strings, numbers, dates and booleans. The only lists are
`GoalMetric.choices`, which follows prompt's `Choice {value, label}` convention, and
`GoalAdjustment.metricIds`, a list of strings. Every string indicator is a plain `String` field whose
allowed values are listed in a values class (constants plus an `ALL` list), checked on create and update.
Values classes live in `com.trevorism.model.types` and end in `Type`, so they're easy to tell apart
from stored entities:

| Values class | Field | Values |
|---|---|---|
| `GoalStatusType` | `Goal.status` | `active` \| `completed` \| `missed` \| `abandoned` |
| `MetricType` | `GoalMetric.type` | `numeric` \| `boolean` \| `scale` \| `choice` \| `text` |
| `MetricMeasuresType` | `GoalMetric.measures` | `outcome` (lag) \| `effort` (lead) |
| `MetricDirectionType` | `GoalMetric.direction` | `increase` \| `decrease` \| `maintain` |
| `FrequencyType` | `GoalMetric.frequency` | `daily` \| `weekly` \| `monthly` |
| `MetricSourceType` | `GoalMetric.source`, `GoalObservation.source` | `manual` \| `prompt` \| `http` \| `event` \| `aggregation` |
| `AdjustmentCategoryType` | `GoalAdjustment.category` | `habit` \| `tool` \| `environment` \| `plan` \| `other` |

### 4.1 Goal (the tree node)
| Field | Notes |
|---|---|
| id, ownerid | |
| parentid, rootid | `rootid` is empty on a root and holds the root id on descendants. One filter on `rootid` loads a whole tree. |
| title, description | |
| status | `GoalStatusType`; `active` by default. Leaving `active` sets `completeddate`. |
| startdate, enddate | Children inherit the parent's dates by default and must fit inside them. |
| definitionofdone | Optional. Describes when a concrete step counts as done. |
| createddate, completeddate | |

- There is no stored type, depth, order, weight or automation setting. A node is a root when
  `parentid` is empty; children are ordered by `startdate`, then `createddate`; rollups weigh
  children equally.
- A node with a definition of done and a short timeframe is a concrete step. Breakdown (phase 3) and
  automation (phase 5) add flat fields when they need them.

### 4.2 Metric
| Field | Notes |
|---|---|
| id, ownerid, goalid, rootid | `rootid` is copied from the goal so a whole tree's metrics load with one filter |
| name, unit, description | |
| type | `MetricType`; `numeric` by default; can't change after creation |
| measures | `MetricMeasuresType`: whether the metric tracks the result (`outcome`) or the work toward it (`effort`); `outcome` by default |
| direction | `MetricDirectionType`; numeric and scale only; `increase` by default |
| baseline, target, tolerance | For a boolean metric, `target` is the required share of yes answers, e.g. 0.857 (6/7), in (0, 1]. `tolerance` is required for `maintain`. |
| scalemin, scalemax | scale only |
| choices | `[{value, label}]`, as in prompt. A missing value is derived from the label (`"Just OK"` → `just-ok`) and made unique. Boolean metrics always get `yes`/`no`. |
| frequency | `FrequencyType`; `daily` by default |
| source | `MetricSourceType`; `manual` by default |
| nextdueat, lastcollectedat, enabled | |

### 4.3 Observation
| Field | Notes |
|---|---|
| id, ownerid, metricid | |
| observedat | The date the value applies to. For a daily habit this is the day, not when it was answered. |
| value | Number: the numeric value, 1/0 for boolean, the scale point, or the choice's position (for charting). Empty for text. |
| choice | Boolean and choice metrics: the selected choice's value, matching prompt's `selectedChoices` |
| label | The selected choice's label, or the text answer |
| note | |
| metricsource, sourceref | `metricsource` is how the value was collected (`MetricSourceType`); `sourceref` is the id of the record it came from, e.g. `prompt` + answerId. Manual entries have no `sourceref`. |
| missed | `true` for a period nobody answered |

### 4.4 Adjustment
`id, ownerid, goalid, metricids[] (empty = all metrics in the subtree), effectivedate, title,
description, category (AdjustmentCategoryType; other by default)`

### 4.5 PendingAsk
`id, ownerid, metricid, questionid, periodstart, periodend, status (open|answered|missed|invalid),
createddate`

### 4.6 AutomationRun
`id, ownerid, goalid, metricid?, actionid, startedat, finishedat,
status (pending_approval|approved|rejected|running|succeeded|failed), approvalquestionid,
requestsummary, resultsummary, error`

### 4.7 Profile
`id, ownerid, email, username, timezone, digestday, digestenabled, offtrackemails`

- `email` and `username` are copied from auth `GET /user/me` the first time the user loads the app,
  and refreshed on later logins.
- The daily tick runs with no user session, so it reads email addresses from Profile rather than
  calling auth.

## 5. Measurement types and scoring

Every observation is stored as `value` plus `label`, so one pipeline handles all metric types. Each
scored metric produces a **progress score p ∈ [0, 1]**.

| Type | Collected as | Score p | Chart |
|---|---|---|---|
| numeric | number | `clamp((current − baseline) / (target − baseline))`, where `current` is the fitted value at the latest observation. For `maintain`, the share of the last N observations within `tolerance`. | Scatter, best fit, plan line, target band |
| boolean | Yes/No choices → 1/0 | `min(adherence / target, 1)` over a rolling window (default 4 periods). Missed periods count as 0. | Calendar heatmap, rolling adherence line, current and best streak |
| scale | choices `scalemin..scalemax` | Rolling mean, normalized between baseline and target | Line with best fit |
| choice | choices, ordered worst to best | Rolling mean of position ÷ (choices − 1) | Stacked frequency per period |
| text | free text | Not scored | Journal timeline under the chart |

### Rollup
- **Effort progress:** the mean of the children's progress. A concrete step counts as 1 when it is
  `completed`; otherwise it uses its effort metrics, or 0 if it has none.
- **Outcome progress:** the mean of the node's own outcome metrics.
- Each node shows both numbers. A node's headline number is its outcome progress if it has outcome
  metrics, otherwise its effort progress.

### Status against the plan
- The plan is linear: `expected(t) = (t − start) / (end − start)`.
- **On track:** `p` is within ±0.05 of `expected`. **Ahead:** above that band. **Behind:** below it.
- For numeric and scale metrics, the projection is the best-fit value at `enddate`.
  **At risk:** the projected p at the end date is below 0.9.

## 6. Collection

### 6.1 Daily tick
- A schedule task named `goals_daily_tick` runs daily at 05:13 UTC. It sends `POST /api/collect/tick`,
  which is secured with `@Secure(value = Roles.SYSTEM, allowInternal = true)`.
- The tick does the following, idempotently (re-running it is safe):
  1. For each enabled metric with `nextdueat <= now`, run its source (6.2), then move `nextdueat` to
     the next period in the metric's timezone.
  2. Mark any `PendingAsk` past `periodend` and still `open` as `missed`, and record a `missed`
     observation.
  3. Run any automations that are due (section 8).
  4. Send notifications that are due (section 10).
- The schedule service only offers `immediate`, `hourly`, `daily` and `weekly`. One daily tick
  computing what's due supports weekly, monthly and every-N-days frequencies without creating one
  task per metric.
- Times of day are rounded to the daily tick. That is acceptable for long-term goals.

### 6.2 Sources
| Source | Behavior |
|---|---|
| `manual` | Does nothing. The UI shows a "due" badge, and the Today view (section 11) lists it. |
| `prompt` | Creates a prompt question: `targetIdentityId = owner`, `dueDate = periodend`, `choices` derived from the metric type (Yes/No, scale points, or the choice labels), `text = config.question` or a generated question. Saves a `PendingAsk`. |
| `http` | Reads data from a **catalogued Trevorism service** (section 8.3) with goals' app token. Extracts a value with `config.path` and an optional reducer (`count`, `sum`, `distinct:<field>`, `latest`). |
| `aggregation` | `POST data /aggregation` with a stored request, then extracts the result value. |
| `event` | Push-based. Goals subscribes once per topic. `POST /api/event/topic/{topic}` routes the event to every metric whose `config.topic` matches, extracting `config.valuepath`. A `config.match` map can filter events. |

### 6.3 Prompt integration
- At startup, goals idempotently subscribes `questionAnswered` → `POST /api/event/questionAnswered`,
  `questionOverdue` → `/api/event/questionOverdue` and `approvalDecided` → `/api/event/approvalDecided`.
  It uses `ChannelClient.createSubscription`, the same way prompt creates its topics.
- Event receivers use `@Secure(value = Roles.USER, allowInternal = true)`, because event forwards the
  publisher's token.
- **The event payload is never trusted.** `POST /event/{topic}` is unsecured, so anyone can publish.
  When an answer event arrives:
  1. Look up the `PendingAsk` by `questionId`. Ignore the event if there's no match or the ask isn't
     `open`.
  2. Fetch the answer from prompt with goals' token (`GET /api/answer/{answerId}`), and check that the
     answer's `questionId` and answerer match.
  3. Parse the answer: selected choices for boolean, scale and choice; a number from the text for
     numeric. Record the observation, or mark the ask `invalid` and show it in the Today view.
- **Prompt change (phase 2, a PR to `prompt`):** add `answerType: text|number`, `unit`, `min` and `max`
  to Question, so the prompt UI shows a numeric input and validates it. Boolean, scale and choice
  metrics already work with prompt's existing `choices`, so no change is needed for them.

## 7. Analysis

`GET /api/goal/{id}/progress` returns everything the chart and the emails need. It is computed on the
server, so the UI and the emails always agree.

```
{ goal, effortprogress, outcomeprogress, status, children:[{id,title,progress,status}],
  metrics:[{ metric, observations, plan:{start,end,baseline,target},
             fit:{slope,intercept,r2,n,projectedend,projectedtargetdate},
             segments:[{from,to,adjustmentid,slope,n}], adherence?, streak?, score, status }],
  adjustments }
```

- **Fit:** ordinary least squares of value against days since start. It needs at least 3 observations
  and reports `r2` and `n`. Boolean metrics are fitted on rolling adherence rather than raw 0/1
  values. `projectedtargetdate` solves the fit line for `target`, and is empty if the line never
  reaches it before twice the remaining time.
- **Segmented fit:** the observations are split at each adjustment that applies to the metric. Each
  segment with at least 5 points gets its own slope. The UI labels the slope change, e.g. "−0.2 →
  −0.6 lb/wk after *Started walking daily*", and states that it shows correlation, not causation.
- **Chart (chart.js):**
  - The x axis is fixed from `startdate` to `enddate`, with a "today" marker.
  - Plan: a dashed line from baseline to target. Target: a horizontal band.
  - Observations are points. Missed observations are hollow markers.
  - The fit is a solid line through the data, then dashed to the end date.
  - Adjustments are vertical annotation lines with labels.
  - Boolean metrics get a calendar heatmap and a rolling adherence line instead.
  - Load the `dataviz` skill before writing any chart code.

## 8. Automation

### 8.1 The automation ladder
Phase 5 adds flat automation fields to `Goal` (for example `automationlevel`, `automationaction`), with an
`AutomationLevelType` values class.

| Level | Meaning | How it works |
|---|---|---|
| `manual` | The user does the work and records it. | UI |
| `reminded` | The user is asked or reminded on a schedule. | tick → prompt question |
| `measured` | Progress data arrives automatically; the user still does the work. | `http`, `event` and `aggregation` sources |
| `approved` | The system prepares an action and the user approves each run. | prompt question with `kind=approval`, then `approvalDecided` → execute |
| `automated` | The system does the work and records the result. | tick or event → action → observation |

- **Automated coverage** of a subtree is the weighted share of its leaf steps at the `automated`
  level. The UI shows it, e.g. "62% of this goal's work is automated."
- A goal whose leaves are all automated and whose outcome metrics are on track is satisfied by the
  system.

### 8.2 Execution
1. The tick (or an event) finds a due automation.
2. If `requiresapproval`:
   - Create an `AutomationRun` with status `pending_approval`.
   - Create a prompt approval question targeted at the owner, containing a preview of the request.
   - `approvalDecided` → re-fetch the decision from prompt (never trust the event payload) → move the
     run to `approved` or `rejected`.
3. Execute the catalogued action with goals' app token, then record `resultsummary`, plus an
   observation if the action produces one.
4. On failure, increment `consecutivefailures`. After 3 failures, drop the node to `reminded` and
   email the owner, so a broken automation never stalls silently.

### 8.3 Action catalog
Actions are **defined in code**, not as URLs the user types in. That prevents goals' token from being
pointed at arbitrary hosts (SSRF and confused-deputy risks). Hosts resolve through the platform
service list.

| actionid | Kind | Default approval | Example |
|---|---|---|---|
| `read.trevorism` | Measure: GET from a catalogued service and path | no | Paying customers from `billing /api/payment` |
| `read.aggregation` | Measure: `data /aggregation` | no | Count objects of a kind |
| `ask.prompt` | Ask someone a question | no | Ask an accountability partner for a check-in |
| `email.self` | Email the owner | no | Weekly reminder |
| `email.send` | Email other people (outward-facing) | **always** | Send an outreach email |
| `draft.chat` | Generate content with `chat` (e.g. an email draft) | no; output feeds `email.send` | Draft outreach |
| `run.github.workflow` | `github POST /repo/{name}/workflow` | **always** | Kick off a release |
| `post.trevorism` | POST to a catalogued service and path | **always** | Future write integrations |

**Guardrails:**
- Anything outward-facing, costing money, or writing to another service always requires approval.
  The UI can't turn that off in v1.
- Every run is logged in `AutomationRun`.
- There are per-node and per-user daily run limits.
- Actions run with goals' app identity, so each action checks that its parameters only touch the
  owner's resources.

## 9. Security

- `/api/auth/*` comes from `micronaut-ui-auth`. The frontend uses `@trevorism/ui-auth`
  (`app.use(TrevorismAuth, { router })`, with `meta.requiresAuth` on every route except splash).
- **Every** route has an explicit `@Secure`, because `@Secure` is fail-open:
  - User routes: `@Secure(Roles.USER)`.
  - Tick: `@Secure(value = Roles.SYSTEM, allowInternal = true)`.
  - Event receivers: `@Secure(value = Roles.USER, allowInternal = true)`, and they re-verify the data
    against its source.
- **Ownership:** all persistence goes through `OwnedRepository<T>`, which sets `ownerid` from the
  token on create, filters lists by `ownerid`, and returns 404 (not 403) for other users' ids on
  get, update and delete.
- **Tests:** unit tests check that a second owner can't read, change or delete the first owner's
  goals, metrics, observations or adjustments. The acceptance suite has only one app identity, so
  it covers anonymous rejection and unknown ids. `RouteSecurityTest` fails the build if any data
  route lacks `@Secure(Roles.USER)`.
- **Deletes:** the normal flow is `status=abandoned`, which keeps the history. A hard delete cascades
  through the subtree. It needs only `Roles.USER`, because user tokens carry `CRE` and no `D`;
  ownership is what protects the data.

## 10. Notifications

- Notifications are sent with `email POST /mail {subject, recipients:[profile.email], body}`.
- **Weekly digest** (on `profile.digestday`): one email per user. The format follows your email
  style: ranked lines you can act on, with no explanatory prose.
  1. Items due or missed.
  2. Goals at risk or behind, each with its projection.
  3. Approvals waiting.
  4. Wins: steps done and streaks.
- **Off-track email:** sent once when a goal's status changes to `behind` or `at risk`, and not again
  until the status changes.
- **Events published** with `event-client`: `goalCreated`, `goalStatusChanged`, `goalAchieved`,
  `stepCompleted` and `automationRun`. Other apps can use them, and they can feed event-sourced
  metrics in other goals.

## 11. API and UI

### 11.1 API (all under `/api`)
| Method and path | Purpose |
|---|---|
| `GET/POST /goal` | List root goals; create a root goal |
| `GET/PUT/DELETE /goal/{id}` | Read, update or delete a node |
| `GET /goal/{id}/tree` | The whole subtree with progress summaries |
| `POST /goal/{id}/child` | Add a child |
| `PUT /goal/{id}/move` | Change the parent or the sort order |
| `GET /goal/{id}/progress` | Analysis (section 7) |
| `GET/POST /goal/{id}/metric`, `GET/PUT/DELETE /metric/{id}` | Metrics |
| `GET/POST /metric/{id}/observation`, `PUT/DELETE /observation/{id}` | Observations |
| `GET/POST /goal/{id}/adjustment`, `PUT/DELETE /adjustment/{id}` | Adjustments |
| `POST /goal/{id}/decompose` | Propose children (section 11.3); persists nothing |
| `POST /goal/{id}/decompose/accept` | Create the accepted children |
| `PUT /goal/{id}/automation`, `GET /goal/{id}/automation/run` | Automation settings and run log |
| `GET /today` | What's due, missed, invalid or awaiting approval for the current user |
| `GET/PUT /profile` | Email, timezone and digest settings |
| `POST /collect/tick` | SYSTEM |
| `POST /event/questionAnswered`, `/event/questionOverdue`, `/event/approvalDecided`, `/event/topic/{topic}` | Event receivers |

### 11.2 UI screens
- **Today:** what's due now (one-tap boolean, scale and choice entry), missed and invalid answers,
  and approvals waiting. This is the daily-use screen.
- **Goals:** root goal cards with outcome and effort progress, status, end date and automated
  coverage.
- **Goal detail:**
  - Left: a collapsible tree, where each node shows status, progress, kind and automation level.
  - Right: the charts for the selected node, the adjustment log and the children summary.
  - Actions: add child, add metric, record value, add adjustment, decompose, automate.
- **Decompose dialog:** proposed children as editable cards. Accept, edit or reject each one.
- **Automation panel:** the node's level, its action and parameters, the approval setting, and the
  run log.
- **Profile:** timezone, digest day and notification toggles.

### 11.3 Assisted decomposition (phase 3)
- `POST /goal/{id}/decompose {guidance?}` builds the context: the node, its ancestors, its existing
  children and siblings, its metrics with their current status, and its adjustments.
- It calls `chat POST /api/chat` with a Claude model, asking for JSON in a fixed schema:
  `[{title, description, kind, definitionofdone, startdate, enddate, weight, metrics:[...],
  suggestedautomation:{level, actionid?}}]`.
- The server validates the response against the schema and the parent's date range, then returns it.
  It persists nothing until the user accepts.
- **Concreteness check:** a child that can be done within about 7 days, has a definition of done,
  and has a metric or a done state is proposed as a `step`. Otherwise it is a `milestone` and gets a
  "Decompose further" button.
- Repeating this on each child is how goals get broken down iteratively: outcome → milestones →
  steps.

## 12. Delivery plan

Each phase ships as small PRs against `master`. Every PR passes `gradle build` and the acceptance
tests in its PR environment.

**Phase 1: goal tree and manual tracking**
- 1a. Backend: Goal, Metric, Observation and Adjustment entities; `OwnedRepository`; CRUD routes;
  the ownership acceptance scenario.
- 1b. UI: Goals list and goal detail tree; editing nodes and metrics; manual entry for all five
  metric types.
- 1c. Analysis: scoring, rollup, status, fits; the `/progress` route; charts.
- 1d. Adjustments in the UI and on the charts; segmented fits; the Today view (manual items only).
- **Done when** the three dogfood goals are entered and you're recording values by hand.

**Phase 2: collection**
- 2a. Profile (captured from `/user/me`); the daily tick; the frequency engine; the `prompt` source;
  PendingAsk; event subscriptions; missed handling.
- 2b. A PR to `prompt` for the numeric answer type, and goals uses it.
- **Done when** healthspan habits are answered through prompt and appear on the charts without being
  entered by hand.

**Phase 3: assisted decomposition** through `chat`.
- **Done when** "Get paying customers" has been broken down to steps with assistance.

**Phase 4: notifications**: the weekly digest, off-track emails and goal events.

**Phase 5: automation ladder**
- 5a. `measured`: the `http`, `aggregation` and `event` sources, and the action catalog's read
  actions. The paying-customers metric comes from billing.
- 5b. `approved`: prompt approvals and AutomationRun. Outreach is drafted with `chat`, approved, then
  sent with `email`.
- 5c. `automated`: unattended actions, failure fallback and automated-coverage reporting.

**Later:**
- Public and shared goals, with accountability partners as prompt targets.
- Extract `measure` as a separate service.
- Health data integrations.
- A weekly reflection written by Claude.
- Goal templates.

## 13. Things to check during implementation

1. Can goals' app token create a prompt question targeted at a user, and does it appear in that
   user's prompt lists? The question's asker will be goals' app identity.
2. Can goals' app token read the answer (`GET /api/answer/{id}`) and approval decisions?
3. Does `GET /user/me` return `email`?
4. Which model names does `chat` accept, e.g. `claude-sonnet-5-5`?
5. Does goals' app identity have permission to create event subscriptions? The subscription route
   allows the USER role or internal tokens.
6. Which tenant does datastore use for entities written with goals' app credentials? Confirm that
   `ownerid` filtering is the only ownership boundary we rely on.

## 14. Risks

| Risk | Mitigation |
|---|---|
| Too many prompt questions makes users stop answering | Group a period's due asks into one choice question where possible; show answer rate in the digest |
| Misleading fits on sparse or noisy data | Minimum n, show r² and n, label correlation vs causation |
| Automations misusing goals' identity | Code-defined catalog, approvals for outward or write actions, run limits, audit log |
| The decomposition tree grows too deep | Concreteness check; warn past depth 4 |
| Datastore field-name and enum quirks | Lowercase field names; enums stored as strings; no `key` field |
