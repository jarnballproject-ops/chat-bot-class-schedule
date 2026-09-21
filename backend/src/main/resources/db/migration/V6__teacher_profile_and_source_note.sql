-- ข้อมูลหัวตารางที่ชุด schedules_*_1.json เพิ่มเข้ามา (college / education / specialDuty / note)
-- เก็บที่ teacher เพราะสามค่าแรกเป็นของคน ไม่ใช่ของคาบ ส่วน note เป็นข้อสังเกตของรูปต้นฉบับใบนั้น
alter table teacher
    add column college      text,
    add column education    text,
    add column special_duty text;

-- note มาจากตัวสกัด เช่น "ตำแหน่งหน้าที่พิเศษในภาพต้นฉบับถูกตัดขอบ อ่านได้เพียงบางส่วน"
-- อยู่ที่ session เพราะผูกกับรูปใบเดียวกับ source_image คนตรวจทานต้องเห็นคู่กับรูป
alter table session
    add column source_note text;
