import { useEffect, useState } from "react";
import {
  getChatQuality,
  getOcrAccuracy,
  type ChatQuality,
  type OcrAccuracy,
  type Role,
} from "./api";

/** ชื่อฟิลด์ในฐานเป็นอังกฤษ รายงานส่งอาจารย์จึงต้องแปลงเป็นคำที่คนอ่านตารางใช้จริง */
const FIELD_LABEL: Record<string, string> = {
  day_of_week: "วัน",
  start_time: "เวลาเริ่ม",
  end_time: "เวลาจบ",
  kind: "ประเภทคาบ",
  subject_code: "รหัสวิชา",
  subject_name: "ชื่อวิชา",
  room_code: "ห้อง",
  group_code: "กลุ่มเรียน",
};

const pct = (n: number) => `${Math.round(n * 100)}%`;

const ms = (n: number | null) => (n === null ? "—" : `${n} มิลลิวินาที`);

export default function ReportsPage({ role }: { role: Role }) {
  const [ocr, setOcr] = useState<OcrAccuracy | null>(null);
  const [chat, setChat] = useState<ChatQuality | null>(null);
  const [failed, setFailed] = useState<string | null>(null);

  useEffect(() => {
    if (role === "student") return;
    Promise.all([getOcrAccuracy(role), getChatQuality(role)])
      .then(([accuracy, quality]) => {
        setOcr(accuracy);
        setChat(quality);
        setFailed(null);
      })
      .catch((e: Error) => setFailed(e.message));
  }, [role]);

  if (role === "student") {
    return (
      <div className="state">
        <div className="state__title">หน้านี้สำหรับเจ้าหน้าที่และอาจารย์</div>
        <p className="state__body">
          สลับบทบาทที่แถบด้านซ้ายเป็น “เจ้าหน้าที่” หรือ “อาจารย์” เพื่อดูรายงานความแม่นของระบบ
        </p>
      </div>
    );
  }

  return (
    <div className="page">
      {failed && (
        <p className="notice notice--bad" role="alert">
          {failed}
        </p>
      )}

      <div className="blueprint upload">
        <div className="pane__head">
          <span>ความแม่นของการอ่านตาราง</span>
          <span>{ocr ? `ตรวจแล้ว ${ocr.totalReviewed} ช่อง` : "กำลังอ่าน…"}</span>
        </div>
        {ocr?.byField.length === 0 && <div className="detail__empty">{ocr.note}</div>}
        {ocr?.byField.map((row) => (
          <div className="docrow" key={row.field}>
            <span className="docrow__name">{FIELD_LABEL[row.field] ?? row.field}</span>
            {/* แถบยาวตามค่าเปอร์เซ็นต์ ช่วยกวาดตาหาฟิลด์ที่แย่ที่สุดโดยไม่ต้องอ่านตัวเลขทุกบรรทัด */}
            <span className="bar" aria-hidden="true">
              <span className="bar__fill" style={{ width: pct(row.accuracy) }} />
            </span>
            <span className="docrow__status">{pct(row.accuracy)}</span>
            <span className="docrow__meta">จาก {row.n} ช่อง</span>
          </div>
        ))}
        {ocr && ocr.byField.length > 0 && (
          <p className="notice" style={{ color: "var(--color-neutral-700)", marginTop: "10px" }}>
            {ocr.note}
          </p>
        )}
      </div>

      <div className="blueprint upload">
        <div className="pane__head">
          <span>คุณภาพคำตอบของแชท</span>
          <span>{chat ? `${chat.answers} คำตอบ` : "กำลังอ่าน…"}</span>
        </div>
        {chat && (
          <>
            <div className="metastrip">
              <Stat label="ตอบช้าสุด 95%" value={ms(chat.p95LatencyMs)} />
              <Stat label="ค่ากลางเวลาตอบ" value={ms(chat.medianLatencyMs)} />
              <Stat label="ตอบไม่ได้" value={pct(chat.unansweredRate)} />
              <Stat label="ถามกลับเพื่อระบุตัวคน" value={pct(chat.clarifyRate)} />
            </div>
            {chat.byTool.map((tool) => (
              <div className="docrow" key={tool.tool}>
                <span className="docrow__name">{tool.tool}</span>
                <span className="docrow__meta">{tool.n} ครั้ง</span>
              </div>
            ))}
            <p className="notice" style={{ color: "var(--color-neutral-700)", marginTop: "10px" }}>
              {chat.note}
            </p>
          </>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="metastrip__item">
      <div className="meta-label">{label}</div>
      <div className="meta-value">{value}</div>
    </div>
  );
}
