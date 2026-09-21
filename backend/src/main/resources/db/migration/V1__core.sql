-- Slice 0: ทะเบียนครู + ข้อมูลอ้างอิง + คาบเรียน
-- ตาราง timetable_doc / ocr_job / field_correction / app_user / chat_message
-- ยังไม่สร้างใน slice นี้ ดูเหตุผลใน docs/แผนงาน.md ข้อ 5

create table teacher (
    id          bigserial primary key,
    code        text        not null unique,
    prefix      text,
    first_name  text        not null,
    last_name   text        not null,
    department  text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- alias_text ต้อง unique: ชื่อต้นหนึ่งชี้ครูได้คนเดียวเท่านั้น
-- ถ้าซ้ำต้องให้คนตัดสินก่อน ห้ามระบบเดา (docs/แผนงาน.md ข้อ 5 "หัวใจของระบบ")
create table teacher_alias (
    id            bigserial primary key,
    alias_text    text   not null unique,
    teacher_id    bigint not null references teacher (id),
    source_doc_id bigint
);

create table subject (
    code     text primary key,
    name     text not null,
    theory   smallint,
    practice smallint,
    credits  smallint
);

-- parent_code ใช้แทน combinedGroups: สท.4/1 และ สท.4/2 มี parent เป็น สท.4/1-2
create table class_group (
    code        text primary key,
    name        text,
    headcount   int,
    parent_code text references class_group (code)
);

create table room (
    code     text primary key,
    name     text,
    verified boolean not null default false
);

-- source_doc_id ยังไม่ผูก FK เพราะ timetable_doc จะมาใน migration ของสาย OCR
create table session (
    id            bigserial primary key,
    semester      text     not null,
    day_of_week   smallint not null check (day_of_week between 1 and 7),
    start_time    time     not null,
    end_time      time     not null,
    kind          text     not null,
    subject_code  text references subject (code),
    room_code     text references room (code),
    group_code    text references class_group (code),
    status        text     not null default 'published',
    conf          jsonb,
    bbox          jsonb,
    source_doc_id bigint,
    check (end_time > start_time)
);

-- teacher_id เป็น null ได้ระหว่างที่ยังจับคู่ชื่อต้นกับทะเบียนครูไม่ได้
create table session_teacher (
    session_id bigint   not null references session (id) on delete cascade,
    ordinal    smallint not null,
    alias_text text,
    teacher_id bigint references teacher (id),
    primary key (session_id, ordinal)
);

create index idx_session_day on session (day_of_week, start_time);
create index idx_session_teacher_teacher on session_teacher (teacher_id);

-- ตารางอาจารย์เป็น view ไม่ใช่ตารางที่เก็บซ้ำ (ADR-06)
-- derive จาก session JOIN session_teacher ตารางกลุ่มกับตารางอาจารย์จึงไม่มีทางไม่ตรงกัน
create view v_teacher_schedule as
select st.teacher_id,
       t.prefix || t.first_name || ' ' || t.last_name as teacher_name,
       st.alias_text,
       s.id                                           as session_id,
       s.semester,
       s.day_of_week,
       s.start_time,
       s.end_time,
       s.kind,
       s.subject_code,
       sub.name                                       as subject_name,
       s.room_code,
       s.group_code,
       g.headcount,
       s.status,
       s.source_doc_id
from session s
         join session_teacher st on st.session_id = s.id
         left join teacher t on t.id = st.teacher_id
         left join subject sub on sub.code = s.subject_code
         left join class_group g on g.code = s.group_code;
