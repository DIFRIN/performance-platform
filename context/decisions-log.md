# Decisions Log

> Journal de toutes les micro-décisions prises pendant l'implémentation.
> Distincte des ADRs (décisions architecturales majeures) : ici ce sont les
> décisions de détail d'implémentation qui ne méritent pas un ADR complet
> mais qui doivent être traçables pour éviter de les re-discuter.
>
> Mis à jour par le Developer à chaque décision non-évidente.
> Lu par le Reviewer pour comprendre les choix, par l'Architect pour détecter des patterns.

---

## Format

```
[YYYY-MM-DD] [MODULE] DÉCISION : [ce qui a été décidé]
CONTEXTE : [pourquoi cette question s'est posée]
RAISON : [pourquoi ce choix et pas un autre]
IMPACT : [quels fichiers / comportements sont affectés]
```

---

## Log

_Aucune décision enregistrée pour l'instant. Les premières entrées apparaîtront
dès le début de la Phase 1._

---

## Décisions Attendues (À Ne Pas Oublier)

Ces points sont connus comme source de micro-décisions à prendre lors de l'implémentation.
Documenter la décision prise ici quand elle est tranchée.

| Sujet | Phase | Question |
|---|---|---|
| `ExecutionContext` serialization | Phase 1 | Comment sérialiser les `Object` du store pour le transport ? |
| DAG level computation | Phase 2 | BFS ou tri topologique ? Quelle lib ? |
| Gatling in-process isolation | Phase 4 | Comment isoler le classloader Gatling du reste de l'app ? |
| Agent heartbeat storage | Phase 7 | In-memory (perte sur restart) ou PostgreSQL ? |
| Report HTML templating | Phase 6 | Thymeleaf ou template string pur ? |
| TaskExecutionRequest serialization | Phase 7 | Jackson (décidé : format Anthropic-compatible JSON) |
