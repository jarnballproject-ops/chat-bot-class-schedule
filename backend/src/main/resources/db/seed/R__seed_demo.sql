-- ไฟล์นี้ generate จาก dataset/schedule.json อย่าแก้ด้วยมือ
-- repeatable migration: รันหลัง versioned เสมอ และรันซ้ำเมื่อไฟล์เปลี่ยน
-- โหลดเฉพาะโปรไฟล์ dev (ดู spring.flyway.locations ใน application-dev.yml)

insert into teacher (code, prefix, first_name, last_name, department) values
    ('T001', 'นาง', 'สจี', 'พรหมมาศ', 'เทคโนโลยีสารสนเทศ')
on conflict (code) do update set prefix = excluded.prefix, first_name = excluded.first_name,
    last_name = excluded.last_name, department = excluded.department, updated_at = now();

insert into teacher_alias (alias_text, teacher_id)
select 'สจี', id from teacher where code = 'T001'
on conflict (alias_text) do nothing;

insert into subject (code, name, theory, practice, credits) values
    ('31900-1003', 'การสร้างสื่อดิจิทัล', 1, 4, 3),
    ('31908-2005', 'การเขียนโปรแกรมเชิงโครงสร้าง', 1, 4, 3),
    ('31901-2009', 'การพัฒนาซอฟต์แวร์สำหรับอุปกรณ์เคลื่อนที่', 1, 4, 3),
    ('20000-2001', 'กิจกรรมลูกเสือวิสามัญ 1', 0, 2, 0)
on conflict (code) do update set name = excluded.name, theory = excluded.theory,
    practice = excluded.practice, credits = excluded.credits;

insert into room (code, name) values
    ('COM404', 'COM404'),
    ('สถานประกอบการ', 'สถานประกอบการ'),
    ('สนามลูกเสือเนตรนารี', 'สนามลูกเสือเนตรนารี'),
    ('ออนไลน์', 'ออนไลน์')
on conflict (code) do nothing;

-- กลุ่มรวมต้องมาก่อนกลุ่มย่อยเพราะ parent_code เป็น FK ชี้ตัวเอง
insert into class_group (code, name, headcount, parent_code) values
    ('สท.5/1-2', 'สท.5/1-2', 44, null),
    ('คภ.5/1-2', 'คภ.5/1-2', 44, null),
    ('สท.4/1-2', 'สท.4/1-2', 39, null),
    ('สท.1/1-2', 'สท.1/1-2', 40, null),
    ('คภ.4/1-2', 'คภ.4/1-2', 35, null),
    ('สท.4/1', 'สท.4/1', 20, 'สท.4/1-2'),
    ('สท.4/2', 'สท.4/2', 19, 'สท.4/1-2'),
    ('คภ.4/1', 'คภ.4/1', 20, 'คภ.4/1-2'),
    ('คภ.4/2', 'คภ.4/2', 15, 'คภ.4/1-2')
on conflict (code) do update set headcount = excluded.headcount, parent_code = excluded.parent_code;

-- ล้างคาบของภาคเรียนนี้ก่อนใส่ใหม่ ให้รันซ้ำได้โดยไม่เกิดแถวซ้ำ
delete from session_teacher st using session s where st.session_id = s.id and s.semester = '1/2569';
delete from session where semester = '1/2569';

with slot (day_of_week, start_time, end_time, kind, subject_code, room_code, group_code) as (values
    (1, time '10:00', time '12:00', 'ปฏิบัติ', '31901-2009', 'สถานประกอบการ', 'สท.5/1-2'),
    (1, time '14:00', time '16:00', 'ปฏิบัติ', '31900-1003', 'สถานประกอบการ', 'คภ.5/1-2'),
    (1, time '18:00', time '19:00', 'ทฤษฎี', '31901-2009', 'ออนไลน์', 'สท.5/1-2'),
    (2, time '10:00', time '12:00', 'ปฏิบัติ', '31900-1003', 'COM404', 'สท.4/1'),
    (2, time '12:00', time '14:00', 'ปฏิบัติ', '31900-1003', 'COM404', 'สท.4/1'),
    (2, time '18:00', time '19:00', 'ทฤษฎี', '31900-1003', 'ออนไลน์', 'คภ.5/1-2'),
    (3, time '08:00', time '10:00', 'ทฤษฎี', '31900-1003', 'COM404', 'สท.4/1-2'),
    (3, time '10:00', time '12:00', 'ปฏิบัติ', '31900-1003', 'COM404', 'สท.4/2'),
    (3, time '12:00', time '14:00', 'ปฏิบัติ', '31900-1003', 'COM404', 'สท.4/2'),
    (3, time '14:00', time '16:00', 'ปฏิบัติ', '20000-2001', 'สนามลูกเสือเนตรนารี', 'สท.1/1-2'),
    (4, time '08:00', time '10:00', 'ทฤษฎี', '31908-2005', 'COM404', 'คภ.4/1-2'),
    (4, time '10:00', time '12:00', 'ปฏิบัติ', '31908-2005', 'COM404', 'คภ.4/1'),
    (4, time '12:00', time '14:00', 'ปฏิบัติ', '31908-2005', 'COM404', 'คภ.4/1'),
    (5, time '10:00', time '12:00', 'ปฏิบัติ', '31908-2005', 'COM404', 'คภ.4/2'),
    (5, time '12:00', time '14:00', 'ปฏิบัติ', '31908-2005', 'COM404', 'คภ.4/2')
),
ins as (
    insert into session (semester, day_of_week, start_time, end_time, kind, subject_code, room_code, group_code)
    select '1/2569', day_of_week, start_time, end_time, kind, subject_code, room_code, group_code from slot
    returning id
)
insert into session_teacher (session_id, ordinal, alias_text, teacher_id)
select ins.id, 1, 'สจี', (select id from teacher where code = 'T001') from ins;
