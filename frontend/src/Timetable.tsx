import { useState } from "react";
import type { Session } from "./api";
import { hhmm, hour } from "./api";

const DAYS = ["จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์"];
const FIRST_HOUR = 8;
const LAST_HOUR = 19;
const HOURS = Array.from({ length: LAST_HOUR - FIRST_HOUR }, (_, i) => FIRST_HOUR + i);

/** คาบที่ 1 เริ่ม 08:00 ตามตารางอาชีวะ จึงนับคาบจากชั่วโมงได้ตรง ๆ */
const periodOf = (h: number) => h - FIRST_HOUR + 1;

/** ท. / ป. นำหน้ารหัสวิชา เป็นวิธีเดียวกับที่ตารางต้นฉบับใช้แยกประเภทคาบ */
export const codeWithKind = (s: Session) => `${s.kind === "ทฤษฎี" ? "ท." : "ป."}${s.subjectCode}`;

/** คาบที่เวลาทับกันต้องแยกเป็นแถวย่อยของวันเดียวกัน ไม่งั้นการ์ดจะทับกัน */
function toLanes(sessions: Session[]): Session[][] {
  const lanes: Session[][] = [];
  for (const session of [...sessions].sort((a, b) => hour(a.startTime) - hour(b.startTime))) {
    const lane = lanes.find((l) => l.every((s) => hour(s.endTime) <= hour(session.startTime)));
    if (lane) lane.push(session);
    else lanes.push([session]);
  }
  return lanes.length ? lanes : [[]];
}

/** แปลงหนึ่งแถวเป็นลำดับช่อง ช่องที่มีคาบจะกินความกว้างเท่าจำนวนชั่วโมงของคาบนั้น */
function toSegments(lane: Session[]) {
  const segments: { session: Session | null; span: number; startHour: number }[] = [];
  let h = FIRST_HOUR;
  while (h < LAST_HOUR) {
    const session = lane.find((s) => hour(s.startTime) === h);
    const span = session ? hour(session.endTime) - hour(session.startTime) : 1;
    segments.push({ session: session ?? null, span, startHour: h });
    h += span;
  }
  return segments;
}

/** การ์ดอยู่ในแถวของวันซึ่ง screen reader ไม่เห็น จึงต้องพูดวัน เวลา และคาบออกมาเอง */
const label = (s: Session, span: number) =>
  `${DAYS[s.dayOfWeek - 1]} คาบ ${periodOf(hour(s.startTime))}` +
  (span > 1 ? ` ถึง ${periodOf(hour(s.startTime)) + span - 1}` : "") +
  ` ${hhmm(s.startTime)} ถึง ${hhmm(s.endTime)} ${s.subjectName} ${s.kind} ห้อง ${s.roomCode} กลุ่ม ${s.groupCode}`;

type Props = {
  sessions: Session[];
  selected: Session | null;
  onSelect: (s: Session) => void;
  /** ไม่ส่ง = ตารางอ่านอย่างเดียว การ์ดจะลากไม่ได้ */
  onMove?: (s: Session, dayOfWeek: number, startHour: number) => void;
};

export default function Timetable({ sessions, selected, onSelect, onMove }: Props) {
  const [dragging, setDragging] = useState<Session | null>(null);
  const [over, setOver] = useState<string | null>(null);

  /** ลากไปวางแล้วคาบต้องไม่ล้นท้ายวัน ไม่งั้นคาบ 3 ชั่วโมงจะถูกดันออกนอกตาราง */
  const fits = (s: Session, startHour: number) =>
    startHour + hour(s.endTime) - hour(s.startTime) <= LAST_HOUR;

  function drop(day: number, startHour: number) {
    setOver(null);
    if (!dragging || !onMove) return;
    const unchanged = dragging.dayOfWeek === day && hour(dragging.startTime) === startHour;
    if (!unchanged && fits(dragging, startHour)) onMove(dragging, day, startHour);
    setDragging(null);
  }

  return (
    <div className="grid" role="group" aria-label="ตารางสอนรายสัปดาห์ จันทร์ถึงศุกร์ คาบ 1 ถึง 11">
      <div className="grid__ruler">
        <div className="grid__gutter" />
        {HOURS.map((h) => (
          <div key={h} className="grid__tick">
            {h}:00
            <span>{periodOf(h)}</span>
          </div>
        ))}
      </div>

      {DAYS.map((day, index) =>
        toLanes(sessions.filter((s) => s.dayOfWeek === index + 1)).map((lane, laneIndex) => (
          <div className="grid__row" key={`${day}-${laneIndex}`}>
            <div className="grid__day">{laneIndex === 0 ? day : ""}</div>
            <div className="grid__slots">
              {toSegments(lane).map(({ session, span, startHour }) => {
                const key = `${index + 1}-${startHour}-${laneIndex}`;
                const droppable = !!onMove && !!dragging && !session && fits(dragging, startHour);
                return (
                  <div
                    key={key}
                    className={`slot${over === key ? " slot--over" : ""}`}
                    style={{ flexGrow: span }}
                    onDragOver={(e) => {
                      if (!droppable) return;
                      e.preventDefault();
                      setOver(key);
                    }}
                    onDragLeave={() => setOver((k) => (k === key ? null : k))}
                    onDrop={() => droppable && drop(index + 1, startHour)}
                  >
                    {session && (
                      <button
                        className={`card card--${session.kind === "ทฤษฎี" ? "theory" : "practice"}`}
                        aria-pressed={selected?.sessionId === session.sessionId}
                        aria-label={label(session, span)}
                        data-span={span}
                        draggable={!!onMove}
                        onDragStart={() => setDragging(session)}
                        onDragEnd={() => {
                          setDragging(null);
                          setOver(null);
                        }}
                        onClick={() => onSelect(session)}
                      >
                        <div className="card__code">{codeWithKind(session)}</div>
                        <div className="card__name">{session.subjectName}</div>
                        <div className="card__meta">
                          {session.roomCode} · {session.groupCode}
                        </div>
                        <div className="card__meta">
                          {hhmm(session.startTime)}–{hhmm(session.endTime)}
                        </div>
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )),
      )}
    </div>
  );
}

export { DAYS, FIRST_HOUR, LAST_HOUR, periodOf };
