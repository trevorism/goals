# Goals: design

Status: draft, 2026-10-03. This is the design for the `goals` service on the Trevorism platform. When the
repo exists, it moves to `goals/docs/design.md`.

## 1. Vision

Goals turns long-term intentions into outcomes. Each goal goes through a loop:

1. **Define** the goal: a long-term outcome, a timeframe, and how success is measured.
2. **Decompose** it into sub-goals, and decompose those until each leaf is a concrete step that can
   be done within about a week.
3. **Measure** progress at every level, with numeric and qualitative metrics. Measurements are
   recorded by hand in goals, which is the one place values are recorded.
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
- **One place to record.** Every value is entered in goals. Goals doesn't send
  questions to another app to be answered there, so a value is never recorded in two places.
- **Use the platform.** Use the `schedule`, `event`, `email`, `chat` and datastore services
  rather than building parallel mechanisms.
- **Honest about missing data.** The owner leaves some periods unrecorded on purpose. An unrecorded
  period is unknown, never a No, and every score says how much data it rests on.
- **Start as one service, and keep the split possible.** Collection and measurement sit behind
  interfaces, so a later `measure` service can be extracted by moving code.
- **Goals are private.** Every read and write is scoped to the owner. Public or shared goals come
  later.

## 2. First goals (dogfooding)

These three goals are test cases for every phase. Each one exercises a different part of the system.

### 2.1 Build the goals application (outcome, ends 2027-03-31)
- Lag metrics:
  - `phases shipped`: numeric, 0 → 5, collected by hand.
  - `days I used goals this week`: numeric, target ≥ 5, recorded weekly.
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
    - Step: walk 30 minutes per day (`boolean`, target rate 6/7, recorded daily).
    - Step: zone 2 training 3×/week (`boolean`, weekly target 3).
  - **Sleep** (milestone)
    - Metric: `sleep quality` (scale 1–5).
    - Step: no screens after 22:00 (`boolean`).
  - **Nutrition** (milestone)
    - Metric: `meal quality` (choice: poor / ok / good, scored 0 / 0.5 / 1).
- Adjustments, for example "started walking after lunch" or "moved caffeine before noon". These are
  the test cases for segmented fits.
- Automation: health metrics start at the `manual` level. A future health data source (e.g. a
  health-dash feed) moves them to `measured`.

### 2.3 Get paying customers on the Trevorism platform (outcome, ends 2027-06-30)
- Lag metrics:
  - `paying customers`: numeric, 0 → N, recorded by hand. (A billing measurement was tried in
    0.10.0 and removed in 0.12.0, section 6.)
  - `MRR`: numeric, from `stripe` or `billing`.
- Children:
  - Choose a first offering (milestone).
  - Pricing and checkout (milestone). Its steps reuse the `stripe` subscription session work that
    already exists.
  - Landing page on `homepage` (milestone).
  - Outreach (milestone). Step: contact 5 prospects per week (numeric, weekly). The `approved`
    automation drafts outreach emails through `chat`, and the user approves each one through a
    prompt approval before `email` sends it (deferred, see section 12).
- This goal is the main test case for the automation ladder and its guardrails, once an offer is
  chosen.

## 3. Architecture

```
                       ┌──────────────────────────── goals (Micronaut + Vue) ───────────────────────────┐
 browser ── /api/auth ─┤ ui-auth session │ goal │ measurement │ analysis │ decompose │ automation │ notify │
                       └──────┬──────────────┬───────────┬───────────┬──────────┬──────────┬─────────┘
                              │              │           │           │          │          │
                       datastore svc                event svc   chat svc              email svc
                       (all entities)               (subscribe) (decompose)
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
- **Hosting:** App Engine, F1 to start. There is no background work; everything is computed on request.
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
`GoalMetric.choices`, a list of `Choice {value, label}`, and
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
| `MetricSourceType` | `GoalMetric.source`, `GoalObservation.source` | `manual` (older observations may say `prompt` or `http`) |
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
| choices | `[{value, label}]`. A missing value is derived from the label (`"Just OK"` → `just-ok`) and made unique. Boolean metrics always get `yes`/`no`. |
| frequency | `FrequencyType`; `daily` by default |
| source | `MetricSourceType`; `manual` by default |
| nextdueat, lastobservedat, enabled | `lastobservedat` is the date of the most recent observation, kept up to date when values are recorded, edited or deleted. The Today view uses it to find what's due without querying observations. Datastore can't clear a field, so deleting every observation leaves the last date in place. `enabled = false` pauses a metric: it leaves Today and its goal's progress, and its card is greyed out. |

### 4.3 Observation
| Field | Notes |
|---|---|
| id, ownerid, metricid | |
| observedat | The date the value applies to. For a daily habit this is the day, not when it was answered. |
| value | Number: the numeric value, 1/0 for boolean, the scale point, or the choice's position (for charting). Empty for text. |
| choice | Boolean and choice metrics: the selected choice's value |
| label | The selected choice's label, or the text answer |
| note | |
| metricsource, sourceref | `metricsource` is how the value was collected (`MetricSourceType`); `sourceref` is the id of the record it came from, e.g. a prompt answer id on values recorded before 0.11.0. Manual entries have no `sourceref`. |
| missed | `true` for a period nobody answered (only from prompt collection before 0.11.0). Treated as unrecorded. |

### 4.4 Adjustment
`id, ownerid, goalid, metricids[] (empty = all metrics in the subtree), effectivedate, title,
description, category (AdjustmentCategoryType; other by default)`

### 4.5 PendingAsk (removed)
Tracked prompt questions until 0.11.0. Existing `goalpendingask` records are no longer read.

### 4.6 AutomationRun
`id, ownerid, goalid, metricid?, actionid, startedat, finishedat,
status (pending_approval|approved|rejected|running|succeeded|failed), approvalquestionid,
requestsummary, resultsummary, error`

### 4.7 Profile
`id, ownerid, timezone, createddate`

- The UI saves the browser's IANA timezone once per session when it differs. Progress uses it to
  decide the owner's local today.

## 5. Measurement types and scoring

Every observation is stored as `value` plus `label`, so one pipeline handles all metric types. Each
scored metric produces a **progress score p ∈ [0, 1]**. `ProgressCalculator` computes it on the server.

| Type | Collected as | Score p | Chart |
|---|---|---|---|
| numeric | number | `clamp((current − baseline) / (target − baseline))`. `current` is the fitted value at the latest observation (the latest value when there are fewer than 3). A missing baseline uses the first value; no target means no score. For `maintain`, the share of the last 7 values within `tolerance` of the target. | Values, trend fit, plan line, target line or tolerance band |
| boolean | Yes/No → 1/0 | `min(adherence / target, 1)`, or adherence when there's no target. Adherence is the share of **recorded** periods answered Yes over the last 28 days (daily) or 4 periods (weekly, monthly), counted from the first answer. Unrecorded periods are left out; `answeredperiods` of `windowperiods` reports the coverage, and the UI marks coverage under half as partial data. No recorded period in the window means no score. The latest answer in a period wins. | Rolling adherence over recorded periods with a target line; current and best streak, where unrecorded periods don't break a streak |
| scale | scale points | Mean of the last 7 values, normalized between baseline and target (defaulting to the scale's ends) | Values, trend fit, plan line, target line |
| choice | choices, ordered worst to best | Mean of position ÷ (choices − 1) over the last 7 answers | Answers on a worst→best axis |
| text | free text | Not scored | None; the journal list under the card |

Missed observations (left from prompt collection) count as unrecorded. Observations dated after the
owner's local today are ignored. "Today" is the owner's local date, from the profile timezone.

### Expected progress
- **Progress metrics** (numeric and scale that increase or decrease) follow a linear plan:
  `expected(t) = clamp((t − start) / (end − start))`, using the metric's goal dates.
- **Steady metrics** (yes/no, choice, and `maintain`) should be at target the whole time, so
  `expected = 1`.
- **Pace** = `p − expected`. **Ahead** when pace > 0.05, **on track** within ±0.05, **behind** below
  that. **No data** when there's no score.
- **At risk:** a metric that is ahead or on track, but whose trend projects below 0.9 of the way to
  its target at the end date.
- **Required and recent rate** (numeric and scale, not `maintain`): `requiredrate` is the change per
  week needed from the latest value to reach the target by the end date. `recentrate` is the fitted
  change per week over the last 14 days, or over the last 5 values when 14 days hold fewer. A trend is
  **stalled** when its recent rate moves toward the target at less than a quarter of the required
  rate. A stalled metric names the required rate instead of the all-data projection, which a long
  history can keep optimistic after progress stops.

### Rollup
- **Outcome progress:** the mean score of the goal's own outcome metrics.
- **Effort progress:** the mean of the goal's own effort metric scores and its sub-goals' progress.
  A completed sub-goal counts as 1. Abandoned sub-goals and sub-goals without data are left out;
  `subgoalswithoutdata` counts the started ones so the UI can say what was left out. Paused metrics
  are left out too. A goal with nothing left to score has no data.
- **Headline:** outcome when the goal has scored outcome metrics, otherwise effort. A goal's
  progress and pace are the means over its headline items, so its status follows the same pace bands.
  A goal is at risk when it would otherwise be ahead or on track but one of its headline items is at
  risk. A completed goal is 100%.
- The UI shows both meters, with a marker on the headline meter at `progress − pace`: where the plan
  says the goal should be by now.

## 6. Recording

Values are recorded in goals: on the Today page, which lists what's due in the current period, or on
the goal page, which also takes past dates. Nothing is collected in the background.

- `prompt` collection was removed in 0.11.0, because the same value could then be recorded in two
  places.
- Measured metrics (the `http` source, the billing paying-customers measurement and the daily tick
  that read it) were removed in 0.12.0. The measurement could never count a real customer, and no
  other metric had a platform source. A future measured source starts from a real metric that needs
  it.

## 7. Analysis

`GET /api/goal/{id}/progress` computes progress for the goal's whole subtree in one call, so the UI
and the emails always agree. The response is two flat lists:

```
{ asof,
  goals:   [{ goalid, parentid, progress, outcomeprogress, effortprogress, expected, pace, status,
              subgoalswithoutdata }],
  metrics: [{ metricid, goalid, score, expected, status, current, observationcount,
              fitslope, fitintercept, fitr2, fitcount, projectedend, projectedprogress,
              projectedtargetdate, requiredrate, recentrate, stalled,
              adherence, answeredperiods, windowperiods, currentstreak, beststreak }],
  segments: [{ metricid, adjustmentid, adjustmenttitle, startdate, enddate, count, slope, adherence }] }
```

`status` values come from `ProgressStatusType`: `ahead`, `on_track`, `at_risk`, `behind`, `no_data`.

- **Fit:** ordinary least squares of value against days since the goal's start date, for numeric and
  scale metrics with at least 3 values. `fitintercept` is the value at the start date and `fitslope`
  the change per day. `projectedend` is the fit at the end date, capped to the scale's range for scale
  metrics. `projectedtargetdate` solves the fit for the target, and is empty when the target is already
  reached, the trend moves away from it, or it lies beyond twice the remaining time.
- **Segments:** a metric's history is split at each adjustment that applies to it and falls after the
  goal's start and up to now. An adjustment applies to the metrics it lists, or, when it lists none,
  to every metric on its goal and the goal's sub-goals. The first segment has no adjustment. A number
  or scale segment with at least 5 values gets its own slope; a yes/no segment with at least 5 periods
  gets its own adherence over its recorded periods. Choice and text metrics aren't
  segmented. The UI compares neighbouring segments, e.g. "−0.4 → −0.5 lb/wk after “Cut evening
  snacks”" or "70% → 90% yes after “Walk right after lunch”", says how much data a new segment still
  needs, and notes that this shows correlation, not causation.
- **Charts (chart.js + chartjs-plugin-annotation):**
  - The x axis is fixed from the goal's start to its end date, with a "Today" marker.
  - Values are blue points (categorical slot 1). The trend fit is orange (slot 2): solid over the
    data, dashed to the end date. The plan is a dashed gray line from baseline to target, and the
    target is a gray hairline, or a shaded band for `maintain`. Dashes mean "plan" or "projection".
  - Yes/no metrics show the rolling adherence line, computed in the UI with the same window as the
    server, against a target line.
  - Choice metrics show answers on a worst→best axis.
  - Status is always an icon plus a label, never color alone. Each value stays readable in the list
    under the chart, not only in tooltips.
  - Each adjustment is a thin solid vertical marker labelled with its title (shortened to 24
    characters).
  - The app is light-only, so there's no dark palette yet. Load the `dataviz` skill before changing
    any chart.

## 8. Automation

### 8.1 The automation ladder
The `approved` and `automated` rungs (sections 8.2 and 8.3's action rows) are deferred until a real
step needs them (section 12). The `measured` rung has no source since 0.12.0. When 5b is picked up, approvals
should follow the one-place principle (section 1) rather than move into prompt as sketched below.

Phase 5 adds flat automation fields to `Goal` (for example `automationlevel`, `automationaction`), with an
`AutomationLevelType` values class.

| Level | Meaning | How it works |
|---|---|---|
| `manual` | The user does the work and records it in goals. The Today page lists what's due. | UI |
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
4. On failure, increment `consecutivefailures`. After 3 failures, drop the node to `manual` and
   email the owner, so a broken automation never stalls silently.

### 8.3 Action catalog
Actions are **defined in code**, not as URLs the user types in. That prevents goals' token from being
pointed at arbitrary hosts (SSRF and confused-deputy risks). Hosts resolve through the platform
service list.

| actionid | Kind | Default approval | Example |
|---|---|---|---|
| `read.trevorism` | Measure: a catalogued measurement (below) | no | Paying customers from `billing /api/payment` |
| `read.aggregation` | Measure: `data /aggregation`, limited to the owner's own data | no | Count objects of a kind |
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
  - Event receivers, when there are any: `@Secure(value = Roles.USER, allowInternal = true)`, and
    they re-verify the data against its source.
- **Ownership:** all persistence goes through `OwnedRepository<T>`, which sets `ownerid` from the
  token on create, filters lists by `ownerid`, and returns 404 (not 403) for other users' ids on
  get, update and delete.
- **Tests:** unit tests check that a second owner can't read, change or delete the first owner's
  goals, metrics, observations or adjustments. The acceptance suite has only one app identity, so
  it covers anonymous rejection and unknown ids. `RouteSecurityTest` fails the build if any data
  route lacks `@Secure(Roles.USER)`.
- **Delegation:** an owner can let another identity act on their goals: a `GoalDelegate` (`ownerid`,
  `delegateid`, `label`, `access` from `DelegateAccessType`: `read` | `edit`), managed on the Settings
  page through `GET/POST /api/delegate` and `DELETE /api/delegate/{id}`.
  - A delegate adds `?onBehalfOf=<ownerId>` to any goal, metric, observation, adjustment, progress,
    Today or dashboard route. `OwnerResolver` checks the grant on every request: reads need any grant,
    creates and updates need `edit`, and **deletes are owner-only**. Anything else is a 403 with a
    reason, logged.
  - It's a query parameter because the platform MCP's API tool can't set headers.
    `GET /api/delegate/granted` tells a delegate whose goals it may act on.
  - Records created on an owner's behalf store the delegate in `createdby`, and the UI shows
    "added by Claude" or "by Claude".
  - Profile and grant routes are owner-only; delegates can't change the timezone or grant access.
  - Typical use: the owner grants the MCP `agent` identity `edit`, so a Claude session can break goals
    down and record values into the owner's own goals.
- **Deletes:** the normal flow is `status=abandoned`, which keeps the history. A hard delete cascades
  through the subtree. It needs only `Roles.USER`, because user tokens carry `CRE` and no `D`;
  ownership is what protects the data.

## 10. Notifications and the dashboard

Goals run quietly in the background: long-term goals are a "nice to know", so there are **no digest or
alert emails**. Status lives on an in-app dashboard that the owner opens when they want to.

- **Dashboard** (the home page, `GET /api/dashboard?date=yyyy-MM-dd`): one row per root goal. Each row
  shows the status, progress with the plan marker, time elapsed, and one headline line.
  - The headline comes from the root's most concerning scored outcome metric (the lowest pace). For
    numbers it's the projected end value, for habits the share of Yes answers. Without a metric, it
    shows effort progress or a hint to add a metric.
  - Active goals come first, ordered by end date. Closed goals are a short list at the bottom.
- **Needs you:** a strip that appears only when something is stuck (`NeedsYouType`):
  - `past_end_date`: an active goal or sub-goal whose end date has passed.

  Closed goals never show up here.
- **Later, opt-in per goal:** an attention setting, quiet (the default) or nudge. Nudge is for
  exceptions such as a blocker, or the one manual step left when everything else is automated; it
  would reach the owner by email. It arrives with the automation ladder (phase 5), when
  that step becomes detectable.
- **Goal events** (`goalStatusChanged`, `goalAchieved`, `stepCompleted`) wait until something consumes
  them.

## 11. API and UI

### 11.1 API (all under `/api`)
| Method and path | Purpose |
|---|---|
| `GET/POST /goal` | List root goals; create a root goal |
| `GET/PUT/DELETE /goal/{id}` | Read, update or delete a node |
| `GET /goal/{id}/tree` | The whole subtree with progress summaries |
| `POST /goal/{id}/child` | Add a child |
| `PUT /goal/{id}/move` | Change the parent or the sort order |
| `GET /goal/{id}/progress` | Progress, status and trend fits for the whole subtree (section 7) |
| `GET/POST /goal/{id}/metric`, `GET/PUT/DELETE /metric/{id}` | Metrics |
| `GET/POST /metric/{id}/observation`, `PUT/DELETE /observation/{id}` | Observations |
| `GET/POST /goal/{id}/adjustment`, `PUT/DELETE /adjustment/{id}` | Adjustments |
| `POST /goal/{id}/decompose` | Propose children (section 11.3); persists nothing |
| `POST /goal/{id}/decompose/accept` | Create the accepted children |
| `PUT /goal/{id}/automation`, `GET /goal/{id}/automation/run` | Automation settings and run log |
| `GET /dashboard?date=yyyy-MM-dd` | Every root goal's status and headline, plus needs-you items |
| `GET /today?date=yyyy-MM-dd` | Metrics with nothing recorded in their current day, week or month, for the caller's local date (manual metrics) |
| `GET/POST /delegate`, `DELETE /delegate/{id}` | The owner's delegates (owner-only) |
| `GET /delegate/granted` | Owners who granted the caller access; then use `?onBehalfOf=<ownerId>` |
| `GET/PUT /profile` | The owner's timezone, which decides the owner's local today for progress |

### 11.2 UI screens
- **Today:** what's due now, with one-tap boolean, scale and choice entry. This is the daily-use
  screen and the one place values are recorded.
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

### 11.3 Breaking goals down (outside the app)

Breakdown happens outside the app, in a Claude session or through the platform MCP tools working
against the goals API: `describe_service goals` lists the routes, and `POST /api/goal/{id}/child` plus
`POST /api/goal/{id}/metric` apply an accepted breakdown. The app has no assisted-decomposition feature.

- The MCP server signs in as its own user (`agent`). Once the owner grants `agent` access on the
  Settings page, a session reads `GET /api/delegate/granted`, then adds `?onBehalfOf=<ownerId>` to its
  requests, so its breakdown lands in the owner's goals and is marked as added by the delegate.

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

**Phase 2: collection** (prompt collection shipped in 0.6.0–0.7.0, removed in 0.11.0)
- 2a. Profile; the daily tick; the frequency engine; the `prompt` source; PendingAsk; event
  subscriptions; missed handling.
- 2b. A PR to `prompt` for the numeric answer type, and goals uses it.
- Removed in 0.11.0: asking in prompt gave every metric two places to be recorded. The profile and
  the frequency engine stay; the daily tick went with measured metrics in 0.12.0.

**Phase 3: dropped.** Breakdown happens outside the app (section 11.3).
- **Done when** "Get paying customers" has been broken down to steps with assistance.

**Phase 4: quiet dashboard** (section 10): the home page shows every goal's status, and a needs-you strip appears only when something is stuck. No emails.

**Phase 5: make recording nearly free**

Revised 2026-10-24. A week after 5a-1 shipped, nothing had been recorded for 7 days, and the
paying-customers metric still read nothing. Recording friction is the main risk to the app, so the
ladder's lower rungs come first. The upper rungs wait for a step that needs them.
- 5a. `measured`, and one place to record, in slices:
  - 5a-1 (shipped in 0.10.0, removed in 0.12.0). The measurement catalog and the `http` source,
    collected by the tick. Removed because billing's payments could never match a real customer.
  - 5a-2 (0.11.0). Remove prompt collection, so every value is recorded in goals (Today and the goal
    page). **Done when** the metrics that used prompt are `manual`, the
    `goals-question-answered` subscription is deleted, and everything due is recorded on Today for
    two weeks.
  - Later: the `aggregation` and `event` sources, each when a real metric needs it.
  - The `Goal` automation fields wait for 5b, when approvals first need them. Until then a node's
    `measured` level follows from its metrics' sources.
- **Deferred:** 5b and 5c, until a real step needs them (as with `aggregation` and `event` above).
  - 5b. `approved`: approvals and AutomationRun. The outreach example waits for a chosen offer:
    "Get in front of people" starts 2027-01-01, and approved cold email is still a reputational risk.
  - 5c. `automated`: unattended actions, failure fallback and automated-coverage reporting. Most
    current goals (weight, family, choosing an offer) can never be satisfied by the platform, so
    automated coverage would mostly measure this app's own goal.

**Phase 6: honest progress** (0.12.0, from a review of three weeks of real use)
- 6a. Missing data: unrecorded periods are unknown rather than No in adherence, streaks and segments;
  sub-goals without data are left out of the rollup and counted; today is the owner's local date.
- 6b. Required and recent rates, with stalled trends named instead of projected.
- 6c. Pausing a metric (`enabled`), and a setup review with the owner: pause metrics that can't mean
  anything yet, and fix targets and baselines that misstate progress.
- Then a feature freeze for about three weeks, and a read-only checkpoint around 2026-11-29: which
  metrics are still recorded, how catch-up happens, and whether the stalled-trend line changed
  anything. The next slice (a week catch-up grid, per-period count targets, or an on-demand
  reflection) is chosen from that evidence.

**Later:**
- Public and shared goals, with accountability partners.
- Extract `measure` as a separate service.
- Health data integrations, the next step for `measured` personal metrics such as weight and
  workouts. No platform service holds personal health data yet (`health-dash` monitors the
  platform), so this needs an external feed.
- A weekly reflection written by Claude.
- Goal templates.

## 13. Things checked during implementation

Checked 2026-10-03 against the code and the live services:

1. **Goals' app token:** role `system`, no permissions claim, not admin. Routes marked
   `@Secure(Roles.USER)` accept any role except `internal`, and permission letters are enforced only
   when the token has a permissions claim. So goals can call every USER and SYSTEM route below.
2. **Prompt** (no longer used since 0.11.0): goals could create a private question targeted at a user
   and read the answers as the asker.
3. **Event:** `POST /subscription` accepts USER. **Schedule:** `POST /api/schedule` accepts USER.
   **Email:** `POST /mail` accepts USER or internal tokens.
4. **Auth:** `GET /user/me` needs a user token with the `R` permission, so phase 4 reads the email
   through the signed-in user's session rather than goals' token.
5. Tokens are HS512 with a shared signing key, so the local `signingKey` must never leave the machine.
6. A composite index (equality plus inequality) doesn't exist in datastore, so queries stay
   equality-only and filter further in memory (section 4, `lastobservedat`).

## 14. Risks

| Risk | Mitigation |
|---|---|
| The owner stops recording | One place to record (Today), one-tap entry for yes/no, scale and choice metrics, and scores that stay honest when periods are left unrecorded |
| Misleading fits on sparse or noisy data | Minimum n, show r² and n, label correlation vs causation |
| Automations misusing goals' identity | Code-defined catalog, approvals for outward or write actions, run limits, audit log |
| The decomposition tree grows too deep | Concreteness check; warn past depth 4 |
| Datastore field-name and enum quirks | Lowercase field names; enums stored as strings; no `key` field |
