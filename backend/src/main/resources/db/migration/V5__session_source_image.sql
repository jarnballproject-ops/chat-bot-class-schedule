-- รูปตารางต้นฉบับของคาบนี้ (path สัมพัทธ์ใต้ app.schedule-images.dir)
-- เก็บที่ session เพราะครูคนเดียวมีตารางหลายใบ ใบละช่วงสัปดาห์ คนละรูปกัน
alter table session
    add column source_image text;

create or replace view v_teacher_schedule as
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
       s.source_doc_id,
       s.week_from,
       s.week_to,
       s.source_image
from session s
         join session_teacher st on st.session_id = s.id
         left join teacher t on t.id = st.teacher_id
         left join subject sub on sub.code = s.subject_code
         left join class_group g on g.code = s.group_code;
