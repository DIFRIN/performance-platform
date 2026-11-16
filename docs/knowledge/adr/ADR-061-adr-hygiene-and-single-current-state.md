# ADR-061 - ADR hygiene and single current-state document

**Status** : ACCEPTED (2026-11-15)

## Context

- ADR numbers are not unique: two `ADR-015`, three `ADR-022`.
- Superseded decisions are still referenced as current. `CLAUDE.md` lists
  Spring Modulith as a non-negotiable stack element and names the
  "Modulith modules communicate via events only" rule, while ADR-035 (ACCEPTED)
  drops Spring Modulith in favour of a bytecode boundary test. ADR-001 is still
  titled "java21" while the stack is Java 25.
- Recent ADRs carry no status line; older ones do.
- Understanding the current design requires reading 60 ADRs in order.

## Decision

- **Unique numbers.** Duplicates are renumbered to the next free numbers; the
  old file name is kept as a one-line stub pointing to the new one for one
  release, then deleted. All references are updated in the same change.
- **Mandatory header** on every ADR: `Status` (`PROPOSED`, `ACCEPTED`,
  `SUPERSEDED BY ADR-xxx`, `REJECTED`), date, and `Supersedes` when relevant.
- **An ADR is immutable once ACCEPTED**, except for its status line and an
  implementation note. A change of decision is a new ADR.
- **`docs/knowledge/architecture.md` is the single description of the current
  state.** ADRs explain why; they are not required reading to know what the
  system is. An ADR is only ACCEPTED together with the corresponding update of
  `architecture.md`.
- **`CLAUDE.md` follows accepted ADRs.** Sections 4, 5 and 8 are corrected:
  Spring Modulith is removed from the stack and the rules table (replaced by the
  bytecode boundary test of ADR-035), and the routing table lists only
  non-superseded ADRs.
- A check script fails when two ADR files share a number or an ADR lacks a
  status line.

## Consequences

Agents and contributors read one current-state document and stop applying
rules that were superseded. The routing table in `CLAUDE.md` shrinks. The
renumbering touches many cross-references once. `CLAUDE.md` may only be changed
by explicit Architect decision: accepting this ADR is that decision; until
then it is left untouched.
