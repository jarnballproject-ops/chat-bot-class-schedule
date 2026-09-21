-- Slice 1: ไฟล์ตารางที่เจ้าหน้าที่อัปโหลด (ยังไม่มีคิว OCR ในสไลซ์นี้)
-- ocr_job / field_correction จะมาพร้อมสาย OCR ดู docs/แผนงาน.md ข้อ 6.2

create table timetable_doc (
    id           bigserial primary key,
    teacher_id   bigint references teacher (id),
    kind         text        not null default 'teacher',
    semester     text        not null,
    filename     text        not null,
    content_type text,
    size_bytes   bigint      not null,
    -- sha256 unique กันอัปไฟล์เดิมซ้ำ (docs/แผนงาน.md ข้อ 5)
    sha256       text        not null unique,
    status       text        not null default 'uploaded',
    uploaded_at  timestamptz not null default now()
);

create index idx_doc_teacher on timetable_doc (teacher_id, uploaded_at desc);
