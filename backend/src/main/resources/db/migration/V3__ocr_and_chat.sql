-- Slice 2: คิว OCR + ที่มาของ field_correction + ผู้ใช้/บทสนทนา
-- ตอนนี้ timetable_doc มีแล้ว (V2) ผูก FK ที่ V1/V2 เว้นไว้ให้ครบ

alter table teacher_alias
    add constraint fk_teacher_alias_doc foreign key (source_doc_id) references timetable_doc (id);

alter table session
    add constraint fk_session_doc foreign key (source_doc_id) references timetable_doc (id);

-- attempts/state ให้ worker ใช้ SELECT ... FOR UPDATE SKIP LOCKED เป็นคิว (docs/แผนงาน.md ข้อ 4, 6.2)
create table ocr_job (
    id           bigserial primary key,
    doc_id       bigint      not null references timetable_doc (id),
    state        text        not null default 'queued',
    engine       text        not null default 'typhoon',
    page_done    int         not null default 0,
    page_total   int         not null default 0,
    attempts     int         not null default 0,
    error        text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

create index idx_ocr_job_state on ocr_job (state, created_at);

-- ทุกครั้งที่คนแก้ในหน้าตรวจทาน = ground truth หนึ่งจุด ใช้คำนวณ % ความแม่นย้อนหลังได้ (docs/แผนงาน.md ข้อ 5)
create table field_correction (
    id           bigserial primary key,
    session_id   bigint      not null references session (id) on delete cascade,
    field_name   text        not null,
    old_value    text,
    new_value    text,
    corrected_by text        not null,
    corrected_at timestamptz not null default now()
);

create index idx_field_correction_session on field_correction (session_id);

create table app_user (
    id         bigserial primary key,
    role       text        not null,
    display_name text,
    created_at timestamptz not null default now()
);

-- tool_calls/latency_ms เก็บไว้ตอบกรรมการว่าตอบจากข้อมูลไหน ใช้เวลาเท่าไร (docs/แผนงาน.md ข้อ 5, 6.4)
create table chat_message (
    id              bigserial primary key,
    conversation_id uuid        not null,
    user_id         bigint references app_user (id),
    role            text        not null,
    text            text        not null,
    tool_calls      jsonb,
    latency_ms      int,
    created_at      timestamptz not null default now()
);

create index idx_chat_message_conversation on chat_message (conversation_id, created_at);
