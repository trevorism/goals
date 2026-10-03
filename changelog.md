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