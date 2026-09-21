import { useEffect, useRef, useState } from "react";
import {
  atHour,
  deleteSession,
  fullName,
  getSchedule,
  hhmm,
  hour,
  importTeachers,
  listTeachers,
  patchSession,
  type ImportResult,
  type Role,
  type Session,
  type SessionPatch,
  type Teacher,
} from "./api";
import Timetable, { DAYS, FIRST_HOUR, LAST_HOUR, codeWithKind, periodOf } from "./Timetable";
import UploadPage from "./UploadPage";
import ChatPage from "./ChatPage";
import ReportsPage from "./ReportsPage";
import ScheduleImages from "./ScheduleImages";

const ROLE_LABEL: Record<Role, string> = {
  student: "นักศึกษา",
  staff: "เจ้าหน้าที่",
  teacher: "อาจารย์",
};

type Page = "schedule" | "upload" | "chat" | "reports";

/** ชื่อเต็มใช้เป็นหัวเรื่องของหน้า ชื่อสั้นใช้บนแถบนำทางซ้ายที่กว้างจำกัด */
const PAGES: Record<Page, { title: string; short: string; step: string }> = {
  schedule: { title: "ตารางสอนรายอาจารย์", short: "ตาราง", step: "ขั้น 5 · ข้อมูลพร้อมใช้" },
  upload: { title: "อัปโหลดไฟล์ตาราง", short: "อัปโหลด", step: "ขั้น 1 · ไฟล์ต้นฉบับ" },
  chat: { title: "แชทถามตาราง", short: "แชท", step: "ขั้น 5 · ถาม-ตอบ" },
  reports: { title: "รายงานความแม่นของระบบ", short: "รายงาน", step: "ขั้น 6 · วัดผล" },
};

const weekKey = (s: Session) =>
  s.weekFrom == null ? "" : `${s.weekFrom}-${s.weekTo ?? s.weekFrom}`;

/** "4-12" ต้องมาหลัง "1-3" แต่ก่อน "13-18" เรียงแบบตัวอักษรจะได้ 1, 13, 4 */
const byWeek = (a: string, b: string) => Number(a.split("-")[0] || 0) - Number(b.split("-")[0] || 0);

/**
 * แยกคาบตามใบตารางต้นฉบับ หนึ่งใบ = หนึ่งกริด
 *
 * <p>ครูคนเดียวมีตารางหลายใบต่อภาคเรียน ถ้ายัดทุกใบลงกริดเดียวคาบคนละใบจะตกวันเดียวกันเวลาเดียวกัน
 * แล้วกลายเป็นแถวซ้อนที่อ่านไม่ออก ซึ่งไม่ตรงกับของจริงด้วย เพราะแต่ละใบใช้คนละช่วงสัปดาห์
 * รูปไม่ระบุก็ยังแยกตามช่วงสัปดาห์ได้ ใบที่ไม่มีทั้งสองอย่างถึงจะกองรวมกันใบเดียว
 */
function toSheets(list: Session[]): [string, Session[]][] {
  const sheets = new Map<string, Session[]>();
  for (const session of list) {
    const key = session.sourceImage ?? weekKey(session);
    const bucket = sheets.get(key);
    if (bucket) bucket.push(session);
    else sheets.set(key, [session]);
  }
  return [...sheets.entries()].sort(
    ([, a], [, b]) => (a[0].weekFrom ?? 99) - (b[0].weekFrom ?? 99),
  );
}

/** หัวกริด: ช่วงสัปดาห์ก่อน เพราะเป็นสิ่งที่คนหาในตารางจริง แล้วตามด้วยชื่อไฟล์รูปไว้จับคู่กับต้นฉบับซ้ายมือ */
function sheetLabel(session: Session, key: string) {
  const range = weekKey(session);
  const weeks = range ? `สัปดาห์ที่ ${range.replace("-", "–")}` : "ไม่ระบุช่วงสัปดาห์";
  const file = session.sourceImage ? key.split("/").pop() : null;
  return file ? `${weeks} · ${file}` : weeks;
}

/** หน้าที่ใช้ทะเบียนครูเป็นบริบทร่วม เลือกครูค้างไว้ข้ามหน้าได้ ไม่ต้องเลือกซ้ำ */
const WITH_REGISTRY: Page[] = ["schedule", "upload"];

export default function App() {
  const [page, setPage] = useState<Page>("schedule");
  const [role, setRole] = useState<Role>("student");
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [selectedTeacher, setSelectedTeacher] = useState<Teacher | null>(null);
  const [sessions, setSessions] = useState<Session[] | null>(null);
  const [selectedSession, setSelectedSession] = useState<Session | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [teacherKey, setTeacherKey] = useState(0);
  const [scheduleKey, setScheduleKey] = useState(0);
  const [query, setQuery] = useState("");
  const [department, setDepartment] = useState("");
  const [week, setWeek] = useState("");
  const [railWide, setRailWide] = useState(true);
  const [registryOpen, setRegistryOpen] = useState(true);
  const [inspectorOpen, setInspectorOpen] = useState(true);
  const [registryWidth, setRegistryWidth] = useState(264);
  const [inspectorWidth, setInspectorWidth] = useState(368);

  // นักศึกษาแก้ตารางไม่ได้ ตรงกับ @RequireRole ของ PATCH /sessions/{id}
  const canEdit = role !== "student";

  useEffect(() => {
    listTeachers(role)
      .then((list) => {
        setTeachers(list);
        setSelectedTeacher((current) => current ?? list[0] ?? null);
        setError(null);
      })
      .catch((e: Error) => setError(e.message));
  }, [role, teacherKey]);

  useEffect(() => {
    if (!selectedTeacher) return;
    getSchedule(selectedTeacher.id, role)
      .then((list) => {
        setSessions(list);
        // คาบที่เลือกอยู่ต้องอัปเดตตามข้อมูลใหม่ ไม่งั้นฟอร์มค้างค่าเก่าหลังบันทึก
        setSelectedSession((current) =>
          current ? (list.find((s) => s.sessionId === current.sessionId) ?? null) : null,
        );
        setError(null);
      })
      .catch((e: Error) => setError(e.message));
  }, [selectedTeacher, role, scheduleKey]);

  async function saveSession(id: number, patch: SessionPatch) {
    try {
      await patchSession(id, patch, role);
      setScheduleKey((k) => k + 1);
      setError(null);
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    }
  }

  /** ลบแล้วคาบนั้นหายจากตาราง แผงรายละเอียดจึงต้องกลับไปว่าง ไม่ใช่ค้างฟอร์มของคาบที่ไม่มีอยู่แล้ว */
  async function removeSession(id: number) {
    try {
      await deleteSession(id, role);
      setSelectedSession(null);
      setScheduleKey((k) => k + 1);
      setError(null);
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    }
  }

  const move = (s: Session, dayOfWeek: number, startHour: number) =>
    saveSession(s.sessionId, {
      dayOfWeek,
      startTime: atHour(startHour),
      endTime: atHour(startHour + hour(s.endTime) - hour(s.startTime)),
    });

  /**
   * ครูคนเดียวมีตารางหลายใบต่อภาคเรียน ใบละช่วงสัปดาห์ คาบคนละใบจึงทับเวลากันได้เป็นเรื่องปกติ
   * กรองตามช่วงสัปดาห์แล้วกริดจะกลับมาเป็นตารางหนึ่งใบเหมือนที่พิมพ์จากงานทะเบียน
   */
  const weekRanges = [...new Set((sessions ?? []).map(weekKey))].filter(Boolean).sort(byWeek);
  // ครูคนใหม่มีช่วงสัปดาห์คนละชุด ค่ากรองที่ไม่มีในชุดนี้ต้องกลายเป็น "ทุกสัปดาห์" เอง
  // ไม่งั้นสลับครูแล้วเจอตารางว่างโดยไม่รู้สาเหตุ
  const activeWeek = weekRanges.includes(week) ? week : "";
  const shownSessions = activeWeek
    ? (sessions ?? []).filter((s) => weekKey(s) === activeWeek)
    : sessions;

  const sheets = toSheets(shownSessions ?? []);

  const hours = shownSessions?.reduce((sum, s) => sum + hour(s.endTime) - hour(s.startTime), 0) ?? 0;

  /** ทะเบียนจริงมีครูหลายร้อยคน การเลื่อนหารายชื่อในแถบแคบ ๆ ไม่ไหว ต้องค้นด้วยชื่อ รหัส หรือภาควิชา */
  const needle = query.trim().toLowerCase();
  /** รายชื่อแผนกมาจากข้อมูลจริงที่โหลดมา เพิ่มแผนกใหม่ในไฟล์ตารางแล้วตัวเลือกขึ้นเองไม่ต้องแก้โค้ด */
  const departments = [...new Set(teachers.map((t) => t.department).filter((d) => d !== null))].sort(
    (a, b) => a.localeCompare(b, "th"),
  );
  const filtered = department
    ? teachers.filter((t) => t.department === department)
    : teachers;
  const shownTeachers = needle
    ? filtered.filter((t) =>
        `${fullName(t)} ${t.code} ${t.department ?? ""}`.toLowerCase().includes(needle),
      )
    : filtered;

  return (
    <div className="shell">
      <nav className={railWide ? "rail" : "rail rail--narrow"} aria-label="ส่วนของระบบ">
        <span className="rail__brand" aria-hidden="true">
          ก
        </span>
        {(Object.keys(PAGES) as Page[]).map((p) => (
          <button
            key={p}
            className="rail__item"
            aria-current={page === p ? "page" : undefined}
            // แถบแคบเหลือตัวอักษรเดียว ชื่อเต็มยังต้องอ่านได้จาก tooltip และ screen reader
            title={PAGES[p].short}
            aria-label={PAGES[p].short}
            onClick={() => setPage(p)}
          >
            {railWide ? PAGES[p].short : PAGES[p].short.charAt(0)}
          </button>
        ))}
        <div className="rail__foot">
          {/* สลับบทบาทต้องใช้ความกว้าง ตอนพับจึงซ่อนไว้ กางแถบแล้วค่อยเลือก */}
          {railWide && (
            <>
              <span className="rail__label" id="role-label">
                บทบาท
              </span>
              <RoleSwitch role={role} onChange={setRole} />
            </>
          )}
          <button
            className="rail__fold"
            aria-expanded={railWide}
            aria-label={railWide ? "พับแถบนำทาง" : "กางแถบนำทาง"}
            title={railWide ? "พับแถบนำทาง" : "กางแถบนำทาง"}
            onClick={() => setRailWide((open) => !open)}
          >
            {railWide ? "«" : "»"}
          </button>
        </div>
      </nav>

      <div className="shell__body">
        <header className="topbar">
          <span className="eyebrow">{PAGES[page].step}</span>
          <h1 className="topbar__title">{PAGES[page].title}</h1>
          <div className="topbar__right">
            {/* ตารางหลายใบต่อภาคเรียนเป็นเรื่องปกติ ใบเดียวจึงต้องเลือกดูได้ ไม่ใช่กองรวมกันหมด */}
            {page === "schedule" && weekRanges.length > 1 && (
              <select
                className="input input--inline"
                aria-label="ช่วงสัปดาห์ของตาราง"
                value={activeWeek}
                onChange={(e) => setWeek(e.target.value)}
              >
                <option value="">ทุกสัปดาห์ ({sessions?.length ?? 0} คาบ)</option>
                {weekRanges.map((range) => (
                  <option key={range} value={range}>
                    สัปดาห์ {range}
                  </option>
                ))}
              </select>
            )}
            {page === "schedule" && sessions && sessions.length > 0 && (
              <span className="topbar__count">
                {activeWeek ? `สัปดาห์ ${activeWeek}` : "ทุกสัปดาห์"} <strong>{shownSessions?.length ?? 0}</strong> คาบ ·{" "}
                {hours} ชั่วโมง
              </span>
            )}
            {/* ตารางที่พิมพ์ออกมาแปะบอร์ดยังเป็นของจริงในวิทยาลัย พิมพ์ผ่านเบราว์เซอร์พอ ไม่ต้องทำ PDF เอง */}
            {page === "schedule" && sessions && sessions.length > 0 && (
              <button className="btn btn-secondary" onClick={() => window.print()}>
                พิมพ์ตาราง
              </button>
            )}
            {role === "staff" && page !== "chat" && page !== "reports" && (
              <ImportButton role={role} onDone={() => setTeacherKey((k) => k + 1)} />
            )}
          </div>
        </header>

        {error && (
          <div className="state state--error" role="alert">
            <div className="state__title">ทำรายการไม่สำเร็จ</div>
            <p className="state__body">{error}</p>
            {/* ไม่มีปุ่มนี้ แบนเนอร์จะค้างจนกว่าจะรีโหลด เพราะ error เคลียร์เฉพาะตอน fetch สำเร็จ */}
            <button
              className="btn btn-secondary"
              onClick={() => {
                setTeacherKey((k) => k + 1);
                setScheduleKey((k) => k + 1);
              }}
            >
              ลองใหม่
            </button>
          </div>
        )}

        <main className="workspace">
          {WITH_REGISTRY.includes(page) && !registryOpen && (
            <button className="pane__tab" onClick={() => setRegistryOpen(true)}>
              ทะเบียนครู
            </button>
          )}

          {WITH_REGISTRY.includes(page) && registryOpen && (
            <aside
              className="pane pane--registry"
              style={{ width: registryWidth }}
              aria-label="ทะเบียนครู"
            >
              <div className="pane__head">
                <span>ทะเบียนครู</span>
                <span>
                  {shownTeachers.length < teachers.length
                    ? `${shownTeachers.length}/${teachers.length}`
                    : teachers.length}{" "}
                  คน
                </span>
                <button
                  className="pane__fold"
                  aria-label="ซ่อนทะเบียนครู"
                  title="ซ่อนทะเบียนครู"
                  onClick={() => setRegistryOpen(false)}
                >
                  «
                </button>
              </div>
              {/* type=search ได้ปุ่มล้างคำค้นของเบราว์เซอร์มาฟรี ไม่ต้องทำปุ่มกากบาทเอง */}
              <div className="search">
                <input
                  type="search"
                  className="input"
                  aria-label="ค้นหาครูจากชื่อ รหัส หรือภาควิชา"
                  placeholder="ค้นชื่อ รหัส หรือภาควิชา"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
              </div>
              <div className="search">
                <select
                  className="input"
                  aria-label="กรองตามแผนก"
                  value={department}
                  onChange={(e) => setDepartment(e.target.value)}
                >
                  <option value="">ทุกแผนก</option>
                  {departments.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </div>
              {(needle || department) && (
                <p className="search__count" role="status">
                  พบ {shownTeachers.length} คน
                </p>
              )}
              <div className="people">
                {(needle || department) && shownTeachers.length === 0 && (
                  <p className="detail__empty" style={{ padding: "10px 12px" }}>
                    ไม่พบครูที่ตรงกับเงื่อนไขนี้
                  </p>
                )}
                {shownTeachers.map((t) => (
                  <button
                    key={t.id}
                    className="person"
                    aria-current={selectedTeacher?.id === t.id ? "true" : undefined}
                    onClick={() => setSelectedTeacher(t)}
                  >
                    <span className="person__name">{fullName(t)}</span>
                    <span className="person__meta">
                      {t.code} · {t.department ?? "ไม่ระบุภาควิชา"}
                    </span>
                  </button>
                ))}
              </div>
            </aside>
          )}
          {WITH_REGISTRY.includes(page) && registryOpen && (
            <Splitter
              label="ปรับความกว้างทะเบียนครู"
              width={registryWidth}
              onResize={setRegistryWidth}
              side="left"
              min={200}
              max={480}
            />
          )}

          <section className="pane pane--work">
            {page === "chat" && <ChatPage role={role} />}

            {page === "reports" && <ReportsPage role={role} />}

            {page === "upload" && (
              <UploadPage role={role} teachers={teachers} teacher={selectedTeacher} />
            )}

            {page === "schedule" && (
              <>
                {selectedTeacher && (
                  <div className="metastrip">
                    <Meta label="ชื่ออาจารย์" value={fullName(selectedTeacher)} />
                    <Meta label="รหัสประจำตัว" value={selectedTeacher.code} />
                    <Meta label="ภาควิชา" value={selectedTeacher.department ?? "ไม่ระบุ"} />
                    <Meta label="ภาคเรียน" value={sessions?.[0]?.semester ?? "1/2569"} />
                    {selectedTeacher.education && (
                      <Meta label="วุฒิการศึกษา" value={selectedTeacher.education} />
                    )}
                    {selectedTeacher.specialDuty && (
                      <Meta label="หน้าที่พิเศษ" value={selectedTeacher.specialDuty} />
                    )}
                    <div className="metastrip__hint">
                      {canEdit
                        ? "ลากการ์ดเพื่อย้ายคาบ หรือคลิกการ์ดแล้วแก้วัน/คาบในแผงขวา (ใช้คีย์บอร์ดได้)"
                        : "คลิกการ์ดวิชาเพื่อดูรายละเอียดคาบในแผงขวา"}
                    </div>
                  </div>
                )}

                <div className="scroller">
                  {/* ไม่มีครูในทะเบียน = ยังไม่มีอะไรให้โหลด ห้ามค้างข้อความ "กำลังอ่าน" ไว้ตลอด */}
                  {!selectedTeacher && (
                    <div className="state">
                      <div className="state__title">ยังไม่มีครูในทะเบียน</div>
                      <p className="state__body">
                        {role === "staff"
                          ? "กดปุ่มนำเข้าทะเบียนครูมุมขวาบน แล้วเลือกไฟล์ CSV ที่มีคอลัมน์ code, prefix, first_name, last_name, department"
                          : "ต้องให้เจ้าหน้าที่นำเข้าทะเบียนครูก่อน ตารางจึงจะแสดงได้"}
                      </p>
                    </div>
                  )}

                  {selectedTeacher && sessions === null && (
                    <p className="state__body" role="status">
                      กำลังอ่านตาราง…
                    </p>
                  )}

                  {selectedTeacher && sessions?.length === 0 && (
                    <div className="state">
                      <div className="state__title">ยังไม่มีตารางของอาจารย์ท่านนี้</div>
                      <p className="state__body">
                        ตารางจะขึ้นที่นี่หลังเจ้าหน้าที่อัปโหลดไฟล์ตารางของภาคเรียนนี้และกดเผยแพร่แล้ว
                      </p>
                    </div>
                  )}

                  {selectedTeacher && shownSessions && shownSessions.length > 0 && (
                    <>
                      {/* ต้นฉบับซ้าย ของที่สกัดได้ขวา ช่วงสัปดาห์เดียวกัน เทียบได้ในจอเดียวว่าอ่านถูกไหม */}
                      <div className="compare">
                        <ScheduleImages teacherId={selectedTeacher.id} role={role} week={activeWeek} />
                        <div className="compare__out">
                          <div className="compare__head">
                            ตารางที่ดึงข้อมูลมาแล้ว · {activeWeek ? `สัปดาห์ ${activeWeek}` : "ทุกสัปดาห์"} ·{" "}
                            {sheets.length} ใบ
                          </div>
                          {/* ใบต้นฉบับหนึ่งใบ = กริดหนึ่งอัน คาบคนละใบจึงไม่ถูกยัดลงวันเดียวกันจนทับกัน */}
                          {sheets.map(([key, list]) => (
                            <section className="sheet" key={key} aria-label={sheetLabel(list[0], key)}>
                              <div className="sheet__head">
                                <span>{sheetLabel(list[0], key)}</span>
                                <span className="sheet__count">{list.length} คาบ</span>
                              </div>
                              <Timetable
                                sessions={list}
                                selected={selectedSession}
                                onSelect={setSelectedSession}
                                onMove={canEdit ? move : undefined}
                              />
                            </section>
                          ))}
                          <div className="legend">
                            <div className="legend__item">
                              <span className="legend__chip legend__chip--practice" />
                              ปฏิบัติ (ป.)
                            </div>
                            <div className="legend__item">
                              <span className="legend__chip" />
                              ทฤษฎี (ท.)
                            </div>
                            <div className="legend__item">
                              <span className="legend__chip legend__chip--selected" />
                              คาบที่เลือกอยู่
                            </div>
                          </div>
                        </div>
                      </div>
                      <WeekCourses sessions={sessions ?? []} week={activeWeek} onPick={setWeek} />
                    </>
                  )}
                </div>
              </>
            )}
          </section>

          {page === "schedule" && !inspectorOpen && (
            <button className="pane__tab" onClick={() => setInspectorOpen(true)}>
              รายละเอียดคาบ
            </button>
          )}
          {page === "schedule" && inspectorOpen && (
            <Splitter
              label="ปรับความกว้างแผงรายละเอียดคาบ"
              width={inspectorWidth}
              onResize={setInspectorWidth}
              side="right"
              min={280}
              max={620}
            />
          )}
          {page === "schedule" && inspectorOpen && (
            <aside
              className="pane pane--inspector"
              style={{ width: inspectorWidth }}
              aria-label="รายละเอียดคาบ"
            >
              <div className="pane__head">
                <span>รายละเอียดคาบ</span>
                <span>{selectedSession ? codeWithKind(selectedSession) : "—"}</span>
                <button
                  className="pane__fold"
                  aria-label="ซ่อนแผงรายละเอียดคาบ"
                  title="ซ่อนแผงรายละเอียดคาบ"
                  onClick={() => setInspectorOpen(false)}
                >
                  »
                </button>
              </div>
              <SessionDetail
                key={selectedSession?.sessionId}
                session={selectedSession}
                editable={canEdit}
                onSave={saveSession}
                onDelete={removeSession}
              />
            </aside>
          )}
        </main>
      </div>
    </div>
  );
}

/**
 * ครูคนเดียวเปลี่ยนวิชาตามหลักสูตรเป็นช่วง ๆ ตารางนี้สรุปว่าช่วงสัปดาห์ไหนสอนวิชาอะไรบ้าง
 * กดแถวแล้วกริดด้านบนสลับไปช่วงนั้น ไม่ต้องไล่เปิดทีละช่วงจากตัวเลือกมุมขวาบน
 */
function WeekCourses({
  sessions,
  week,
  onPick,
}: {
  sessions: Session[];
  week: string;
  onPick: (w: string) => void;
}) {
  const rows = [...new Set(sessions.map(weekKey))].sort(byWeek).map((key) => {
    const inWeek = sessions.filter((s) => weekKey(s) === key);
    return {
      key,
      // วิชาเดียวกันสอนหลายคาบต่อสัปดาห์ แถวนี้ต้องการรายชื่อวิชา ไม่ใช่รายการคาบ
      courses: [...new Map(inWeek.map((s) => [codeWithKind(s), s])).values()],
      periods: inWeek.length,
      hours: inWeek.reduce((sum, s) => sum + hour(s.endTime) - hour(s.startTime), 0),
    };
  });

  if (rows.length < 2) return null;

  return (
    <section className="weeks" aria-label="วิชาแยกตามช่วงสัปดาห์">
      <div className="weeks__head">วิชาตามช่วงสัปดาห์ · {rows.length} ช่วง</div>
      <table className="weeks__table">
        <thead>
          <tr>
            <th scope="col">ช่วงสัปดาห์</th>
            <th scope="col">วิชาที่สอน</th>
            <th scope="col">คาบ</th>
            <th scope="col">ชั่วโมง</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key || "none"} aria-current={week === row.key ? "true" : undefined}>
              <th scope="row">
                <button className="btn btn-ghost" onClick={() => onPick(row.key)}>
                  {row.key ? `สัปดาห์ ${row.key}` : "ไม่ระบุสัปดาห์"}
                </button>
              </th>
              <td>
                {row.courses.map((s) => (
                  <span className="weeks__course" key={s.sessionId}>
                    {codeWithKind(s)} {s.subjectName}
                  </span>
                ))}
              </td>
              <td>{row.periods}</td>
              <td>{row.hours}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

type SplitterProps = {
  label: string;
  width: number;
  onResize: (width: number) => void;
  /** แผงอยู่ซ้ายของเส้น = ลากขวาแล้วกว้างขึ้น แผงอยู่ขวา = ลากซ้ายแล้วกว้างขึ้น */
  side: "left" | "right";
  min: number;
  max: number;
};

const STEP = 16;

/**
 * เส้นลากปรับความกว้างของแผงข้าง ใช้ pointer event ตัวเดียวคุมได้ทั้งเมาส์ ปากกา และนิ้ว
 * ฟังที่ window ระหว่างลาก ไม่งั้นเมาส์วิ่งออกนอกเส้นแล้วการลากหลุดกลางทาง
 * role=separator + ลูกศรซ้ายขวา ทำให้ปรับด้วยคีย์บอร์ดได้โดยไม่ต้องใช้เมาส์เลย
 */
function Splitter({ label, width, onResize, side, min, max }: SplitterProps) {
  const clamp = (value: number) => Math.min(max, Math.max(min, value));
  const grow = side === "left" ? 1 : -1;

  function startDrag(event: React.PointerEvent<HTMLDivElement>) {
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = width;

    const move = (moved: PointerEvent) => onResize(clamp(startWidth + (moved.clientX - startX) * grow));
    const stop = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", stop);
      document.body.classList.remove("dragging");
    };

    // กันไม่ให้เคอร์เซอร์กระพริบเป็น text select ตอนลากผ่านตาราง
    document.body.classList.add("dragging");
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", stop);
  }

  return (
    <div
      className="splitter"
      role="separator"
      aria-orientation="vertical"
      aria-label={label}
      aria-valuenow={width}
      aria-valuemin={min}
      aria-valuemax={max}
      tabIndex={0}
      onPointerDown={startDrag}
      onKeyDown={(e) => {
        if (e.key === "ArrowLeft") onResize(clamp(width - STEP * grow));
        if (e.key === "ArrowRight") onResize(clamp(width + STEP * grow));
      }}
    />
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="metastrip__item">
      <div className="meta-label">{label}</div>
      <div className="meta-value">{value}</div>
    </div>
  );
}

type DetailProps = {
  session: Session | null;
  editable: boolean;
  onSave: (id: number, patch: SessionPatch) => Promise<boolean>;
  onDelete: (id: number) => Promise<boolean>;
};

function SessionDetail({ session, editable, onSave, onDelete }: DetailProps) {
  const [draft, setDraft] = useState(session);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const current = draft ?? session;

  if (!session || !current) {
    return (
      <div className="detail">
        <div className="detail__empty">เลือกการ์ดวิชาในตารางเพื่อดูและแก้รายละเอียดคาบ</div>
      </div>
    );
  }

  const set = (patch: Partial<Session>) => {
    setSaved(false);
    setDraft({ ...current, ...patch });
  };
  const span = hour(current.endTime) - hour(current.startTime);
  const startHour = hour(current.startTime);
  const dirty = JSON.stringify(current) !== JSON.stringify(session);

  function setStart(h: number) {
    set({ startTime: atHour(h), endTime: atHour(Math.min(h + span, LAST_HOUR)) });
  }

  function setSpan(next: number) {
    if (next < 1 || startHour + next > LAST_HOUR) return;
    set({ endTime: atHour(startHour + next) });
  }

  async function save() {
    const value = current;
    if (!value) return;
    setSaving(true);
    const ok = await onSave(value.sessionId, {
      dayOfWeek: value.dayOfWeek,
      startTime: value.startTime,
      endTime: value.endTime,
      kind: value.kind,
      subjectCode: value.subjectCode,
      subjectName: value.subjectName,
      roomCode: value.roomCode,
      groupCode: value.groupCode,
    });
    setSaved(ok);
    setSaving(false);
  }

  return (
    <div className="detail">
      <div className="detail__head">
        <div className="detail__title">
          {DAYS[current.dayOfWeek - 1]} คาบ {periodOf(startHour)}
          {span > 1 && `–${periodOf(startHour) + span - 1}`}
        </div>
        <div className="detail__note">
          {hhmm(current.startTime)}–{hhmm(current.endTime)} · {current.kind}
          {current.weekFrom != null &&
            ` · สัปดาห์ ${current.weekFrom}${current.weekTo && current.weekTo !== current.weekFrom ? `–${current.weekTo}` : ""}`}
        </div>
      </div>

      <div className="detail__row detail__row--pair">
        <div className="field">
          <label htmlFor="d-day">วัน</label>
          <select
            id="d-day"
            className="input"
            disabled={!editable}
            value={current.dayOfWeek}
            onChange={(e) => set({ dayOfWeek: Number(e.target.value) })}
          >
            {DAYS.map((day, i) => (
              <option key={day} value={i + 1}>
                {day}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="d-start">คาบเริ่ม</label>
          <select
            id="d-start"
            className="input"
            disabled={!editable}
            value={startHour}
            onChange={(e) => setStart(Number(e.target.value))}
          >
            {Array.from({ length: LAST_HOUR - FIRST_HOUR }, (_, i) => FIRST_HOUR + i).map((h) => (
              <option key={h} value={h}>
                คาบ {periodOf(h)} · {h}:00
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="detail__row detail__row--pair">
        <div className="field">
          <span className="field-label" id="d-span-label">
            จำนวนคาบต่อเนื่อง
          </span>
          <div className="stepper" role="group" aria-labelledby="d-span-label">
            <button
              className="btn btn-secondary"
              aria-label="ลดจำนวนคาบต่อเนื่อง"
              disabled={!editable}
              onClick={() => setSpan(span - 1)}
            >
              −
            </button>
            <strong aria-live="polite">{span} คาบ</strong>
            <button
              className="btn btn-secondary"
              aria-label="เพิ่มจำนวนคาบต่อเนื่อง"
              disabled={!editable}
              onClick={() => setSpan(span + 1)}
            >
              +
            </button>
          </div>
        </div>
        <div className="field">
          <span className="field-label" id="d-kind-label">
            ประเภทคาบ
          </span>
          <div className="seg" role="radiogroup" aria-labelledby="d-kind-label">
            {["ทฤษฎี", "ปฏิบัติ"].map((kind) => (
              <label className="seg-opt" key={kind}>
                <input
                  type="radio"
                  name="kind"
                  disabled={!editable}
                  checked={current.kind === kind}
                  onChange={() => set({ kind })}
                />
                <span>{kind}</span>
              </label>
            ))}
          </div>
        </div>
      </div>

      <div className="detail__row">
        <div className="field">
          <label htmlFor="d-code">รหัสวิชา</label>
          <input
            id="d-code"
            className="input"
            readOnly={!editable}
            value={current.subjectCode}
            onChange={(e) => set({ subjectCode: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="d-name">ชื่อวิชา</label>
          <input
            id="d-name"
            className="input"
            readOnly={!editable}
            value={current.subjectName ?? ""}
            onChange={(e) => set({ subjectName: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="d-room">ห้อง</label>
          <input
            id="d-room"
            className="input"
            readOnly={!editable}
            value={current.roomCode}
            onChange={(e) => set({ roomCode: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="d-group">กลุ่มเรียน</label>
          <input
            id="d-group"
            className="input"
            readOnly={!editable}
            value={current.groupCode}
            onChange={(e) => set({ groupCode: e.target.value })}
          />
        </div>
      </div>

      <div className="detail__foot">
        {editable ? (
          <>
            <button className="btn btn-primary" disabled={!dirty || saving} onClick={save}>
              {saving ? "กำลังบันทึก…" : "บันทึกคาบนี้"}
            </button>
            <button className="btn btn-ghost" disabled={!dirty} onClick={() => setDraft(session)}>
              คืนค่าเดิม
            </button>
            {/* ลบแล้วกู้คืนไม่ได้ จึงถามยืนยันในที่เดิมแทน confirm() ของเบราว์เซอร์ ซึ่งอ่านด้วย screen reader ยาก */}
            {confirming ? (
              <span className="confirm" role="group" aria-label="ยืนยันการลบคาบ">
                <span className="confirm__text">ลบคาบนี้ออกจากตารางถาวร?</span>
                <button
                  className="btn btn-danger"
                  disabled={saving}
                  onClick={async () => {
                    setSaving(true);
                    await onDelete(current.sessionId);
                    setSaving(false);
                    setConfirming(false);
                  }}
                >
                  ลบถาวร
                </button>
                <button className="btn btn-ghost" onClick={() => setConfirming(false)}>
                  ไม่ลบ
                </button>
              </span>
            ) : (
              <button className="btn btn-ghost btn-danger-ghost" onClick={() => setConfirming(true)}>
                ลบคาบนี้
              </button>
            )}
            {/* คนที่ใช้ screen reader ต้องรู้ว่าบันทึกผ่านแล้ว ไม่ใช่แค่ปุ่มจางลงเฉย ๆ */}
            {saved && !dirty && (
              <p className="notice notice--ok" role="status">
                บันทึกคาบนี้แล้ว
              </p>
            )}
            <p className="notice" style={{ color: "var(--color-neutral-700)" }}>
              {current.headcount ? `กลุ่มนี้ ${current.headcount} คน · ` : ""}
              รหัสวิชา ห้อง หรือกลุ่มที่ยังไม่มีในระบบ จะถูกสร้างให้อัตโนมัติเมื่อบันทึก
            </p>
          </>
        ) : (
          <p className="notice" style={{ color: "var(--color-neutral-700)" }}>
            บทบาทนักศึกษาดูได้อย่างเดียว การแก้คาบต้องใช้บทบาทเจ้าหน้าที่หรืออาจารย์
          </p>
        )}
      </div>
    </div>
  );
}

function RoleSwitch({ role, onChange }: { role: Role; onChange: (r: Role) => void }) {
  return (
    <div className="seg seg--stack" role="radiogroup" aria-labelledby="role-label">
      {(Object.keys(ROLE_LABEL) as Role[]).map((r) => (
        <label className="seg-opt" key={r}>
          <input type="radio" name="role" checked={role === r} onChange={() => onChange(r)} />
          <span>{ROLE_LABEL[r]}</span>
        </label>
      ))}
    </div>
  );
}

function ImportButton({ role, onDone }: { role: Role; onDone: () => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [failed, setFailed] = useState<string | null>(null);

  async function upload(file: File) {
    setResult(null);
    setFailed(null);
    try {
      setResult(await importTeachers(file, role));
      onDone();
    } catch (e) {
      setFailed((e as Error).message);
    }
  }

  return (
    <>
      {/* ปุ่มด้านล่างเป็นตัวสั่งงานจริง input ตัวนี้จึงไม่รับโฟกัส แต่ต้องมีชื่อกำกับไว้ */}
      <input
        ref={input}
        type="file"
        accept=".csv"
        className="sr-only"
        tabIndex={-1}
        aria-label="ไฟล์ CSV ทะเบียนครู"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) upload(file);
          e.target.value = "";
        }}
      />
      <button className="btn btn-secondary" onClick={() => input.current?.click()}>
        นำเข้าทะเบียนครู
      </button>
      {result && (
        <p className="notice notice--ok" role="status">
          เพิ่ม {result.inserted} · ปรับปรุง {result.updated}
          {result.rejected.length > 0 && " · ข้าม " + result.rejected.length}
        </p>
      )}
      {failed && (
        <p className="notice notice--bad" role="alert">
          {failed}
        </p>
      )}
    </>
  );
}
