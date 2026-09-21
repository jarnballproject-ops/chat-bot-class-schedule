-- ช่วงสัปดาห์ที่คาบนี้ใช้ (มุมขวาบนของตาราง "สัปดาห์ที่ X-Y")
-- ครูคนเดียวมีตารางหลายใบต่อภาคเรียน ใบละช่วงสัปดาห์ ถ้าไม่แยกจะซ้อนกันเป็นสิบคาบในเวลาเดียว
alter table session
    add column week_from smallint,
    add column week_to   smallint,
    add constraint chk_session_week
        check (week_from is null or week_to is null or week_to >= week_from);

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
       s.week_to
from session s
         join session_teacher st on st.session_id = s.id
         left join teacher t on t.id = st.teacher_id
         left join subject sub on sub.code = s.subject_code
         left join class_group g on g.code = s.group_code;
