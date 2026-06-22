# ISSUE-098 — SUT DB schema + seed 10k devices

**PDR** : PDR-023
**Module** : `platform-examples/sut-db/` (fichiers SQL uniquement — aucun code Java)
**Statut** : WAITING
**Priorité** : P0 (bloque ISSUE-096 et ISSUE-097)
**Bloquée par** : aucune
**Estime** : S

---

## Objectif

Créer les scripts SQL partagés par les deux services SUT (`iot-dispatcher` et `device-api`) :
1. `V1__devices_schema.sql` — crée la table `devices` avec les index appropriés
2. `V2__seed_10k_devices.sql` — insère 10 000 devices de test pointant vers WireMock

Ces scripts sont montés comme volumes dans `docker-compose-sut.yaml` via le dossier `docker-entrypoint-initdb.d` de l'image PostgreSQL (exécutés automatiquement au premier démarrage).

---

## Fichiers à Créer

```
platform-examples/sut-db/sql/
  ├── V1__devices_schema.sql
  └── V2__seed_10k_devices.sql
```

---

## SQL à Implémenter

```sql
-- V1__devices_schema.sql
-- Table centrale des devices IoT pour les tests de performance
-- Partagée par iot-dispatcher et device-api

CREATE TABLE IF NOT EXISTS devices (
    id         SERIAL PRIMARY KEY,
    device_id  VARCHAR(50)  NOT NULL,
    device_dns VARCHAR(255) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_devices_device_id UNIQUE (device_id)
);

-- Index pour les lookups par device_id (chemin critique : O(1))
CREATE INDEX IF NOT EXISTS idx_devices_device_id
    ON devices(device_id);

-- Index partiel pour les devices actifs seulement
CREATE INDEX IF NOT EXISTS idx_devices_active
    ON devices(device_id)
    WHERE is_active = true;

COMMENT ON TABLE  devices            IS 'Registre des devices IoT — SUT pour tests de performance';
COMMENT ON COLUMN devices.device_id  IS 'Identifiant unique du device (ex: device-0001)';
COMMENT ON COLUMN devices.device_dns IS 'Adresse DNS/IP du device pour les commandes HTTP';
COMMENT ON COLUMN devices.is_active  IS 'false = device hors service, ignoré par iot-dispatcher';
```

```sql
-- V2__seed_10k_devices.sql
-- Insère 10 000 devices de test
-- device_dns = WireMock (simule les endpoints IoT dans le docker-compose-sut)
-- device-0001 → device-9999 + device-10000

INSERT INTO devices (device_id, device_dns, is_active)
SELECT
    'device-' || LPAD(gs::text, 4, '0'),
    'wiremock:8080',
    true
FROM generate_series(1, 10000) gs
ON CONFLICT (device_id) DO NOTHING;

-- Vérification
DO $$
DECLARE
    device_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO device_count FROM devices WHERE is_active = true;
    IF device_count < 10000 THEN
        RAISE WARNING 'Expected 10000 active devices, found %', device_count;
    ELSE
        RAISE NOTICE 'Seed OK: % active devices', device_count;
    END IF;
END $$;
```

---

## Règles Spécifiques

- **Nommage** : format `Vn__nom.sql` (compatible Flyway si un service décide d'intégrer Flyway plus tard).
- **`docker-entrypoint-initdb.d`** : PostgreSQL exécute les fichiers `.sql` par ordre alphabétique. `V1__` avant `V2__` — l'ordre est garanti.
- **`ON CONFLICT DO NOTHING`** : idempotent si le conteneur redémarre avec le volume existant.
- **`device_dns = 'wiremock:8080'`** : tous les devices pointent vers WireMock dans le docker-compose-sut. Dans un vrai environnement, les DNS seraient réels.
- **Vérification DO $$...$$** : log de validation sans interrompre le démarrage PostgreSQL si < 10 000 (permet de tester avec un sous-ensemble).

---

## Critères de Done

- [ ] `V1__devices_schema.sql` : syntaxe PostgreSQL valide (testable via `psql -f ...`)
- [ ] `V2__seed_10k_devices.sql` : insère exactement 10 000 lignes sur un PostgreSQL vide
- [ ] `V2__seed_10k_devices.sql` : idempotent (2ème exécution → 0 nouvelles lignes)
- [ ] Contrainte `UNIQUE (device_id)` présente
- [ ] Index `idx_devices_device_id` présent
- [ ] Index partiel `idx_devices_active WHERE is_active = true` présent
- [ ] `.claude/progress.md` mis à jour : ISSUE-098 → DONE
