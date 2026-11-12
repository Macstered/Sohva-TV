PRAGMA page_size=4096;
PRAGMA foreign_keys=ON;
CREATE TABLE identity (uuid TEXT PRIMARY KEY, sport TEXT NOT NULL, kind TEXT NOT NULL, category TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE binding (provider TEXT NOT NULL, product TEXT NOT NULL, kind TEXT NOT NULL, external_id TEXT NOT NULL,
    uuid TEXT NOT NULL REFERENCES identity(uuid), PRIMARY KEY(provider,product,kind,external_id)) WITHOUT ROWID;
CREATE TABLE alias (uuid TEXT NOT NULL REFERENCES identity(uuid), normalised TEXT NOT NULL,
    valid_from INTEGER NOT NULL, valid_to INTEGER NOT NULL, PRIMARY KEY(uuid,normalised,valid_from,valid_to)) WITHOUT ROWID;
CREATE TABLE collision (sport TEXT NOT NULL, kind TEXT NOT NULL, normalised TEXT NOT NULL,
    PRIMARY KEY(sport,kind,normalised)) WITHOUT ROWID;
CREATE TABLE guard (sport TEXT NOT NULL, kind TEXT NOT NULL, base TEXT NOT NULL, qualified TEXT NOT NULL,
    valid_from INTEGER NOT NULL, valid_to INTEGER NOT NULL, PRIMARY KEY(sport,kind,base,qualified,valid_from,valid_to)) WITHOUT ROWID;
CREATE TABLE membership (participant TEXT NOT NULL REFERENCES identity(uuid), competition TEXT NOT NULL REFERENCES identity(uuid),
    season TEXT NOT NULL, valid_from INTEGER NOT NULL, valid_to INTEGER NOT NULL,
    PRIMARY KEY(competition,season,participant,valid_from,valid_to)) WITHOUT ROWID;
CREATE INDEX alias_exact ON alias(normalised,uuid,valid_from,valid_to);
CREATE INDEX membership_participant ON membership(participant,competition,season,valid_from,valid_to);
PRAGMA user_version=1;
