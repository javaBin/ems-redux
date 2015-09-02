CREATE TABLE event (
    id              uuid PRIMARY KEY,
    name            varchar(256) not null,
    venue           varchar(512) not null,
    slug            varchar(256) not null UNIQUE,
    lastModified    timestamptz DEFAULT current_timestamp
);

CREATE TABLE slot (
    id              uuid not null UNIQUE,
    eventId         uuid not null,
    parentId        uuid null,
    start           timestamptz not null,
    duration        int not null,
    lastModified    timestamptz DEFAULT current_timestamp,
    CONSTRAINT SLOT_PK   PRIMARY KEY (id, eventId),
    CONSTRAINT SLOT_EID  FOREIGN KEY (eventId) REFERENCES event(id),
    CONSTRAINT SLOT_PID  FOREIGN KEY (parentId) REFERENCES slot(id)
);

CREATE TABLE room (
    id              uuid not null UNIQUE,
    eventId         uuid not null,
    name            varchar(256) not null,
    lastModified    timestamptz DEFAULT current_timestamp,
    CONSTRAINT ROOM_PK   PRIMARY KEY (id, eventId),
    CONSTRAINT ROOM_EID  FOREIGN KEY (eventId) REFERENCES event(id)
);

CREATE TABLE session (
    id              uuid not null UNIQUE,
    eventId         uuid not null,
    slug            varchar(256) not null UNIQUE,
    abstract        jsonb not null,
    state           varchar(20) not null,
    published       boolean DEFAULT FALSE,
    roomId          uuid,
    slotId          uuid,
    lastModified    timestamptz DEFAULT current_timestamp,
    CONSTRAINT SESSION_PK   PRIMARY KEY (id, eventId),
    CONSTRAINT SESSION_EID  FOREIGN KEY(eventId) REFERENCES event(id),
    CONSTRAINT SESSION_RID  FOREIGN KEY(roomId) REFERENCES room(id),
    CONSTRAINT SESSION_SID  FOREIGN KEY(slotId) REFERENCES slot(id)
);

CREATE TABLE speaker (
    id              uuid not null,
    sessionId       uuid not null,
    email           varchar(512) not null,
    attributes      jsonb not null,
    photo           varchar(1024),
    lastModified    timestamptz DEFAULT current_timestamp,
    CONSTRAINT SPEAKER_PK PRIMARY KEY (id, sessionId)
);