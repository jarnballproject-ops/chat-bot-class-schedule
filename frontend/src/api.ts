// ตั้งผ่าน VITE_API_BASE ได้ เผื่อ backend รันคนละพอร์ต (เช่น รันจาก IDE คู่กับ container)
const BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8081/api/v1";

export type Role = "student" | "staff" | "teacher";

export const TOKENS: Record<Role, string> = {
  student: "dev-student",
  staff: "dev-staff",
  teacher: "dev-teacher",
};

export type Teacher = {
  id: number;
  code: string;
  prefix: string | null;
  firstName: string;
  lastName: string;
  department: string | null;
  /** สามค่านี้มาจากหัวตารางในรูปต้นฉบับ ใบที่ถูกตัดขอบจะไม่มีค่า */
  college: string | null;
  education: string | null;
  specialDuty: string | null;
};

export type Session = {
  sessionId: number;
  semester: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  kind: string;
  subjectCode: string;
  subjectName: string;
  roomCode: string;
  groupCode: string;
  headcount: number | null;
  /** ช่วงสัปดาห์ของตารางใบที่คาบนี้มา คาบต่างใบของครูคนเดียวกันซ้อนเวลากันได้ */
  weekFrom: number | null;
  weekTo: number | null;
  sourceImage: string | null;
};

export type ImportResult = {
  inserted: number;
  updated: number;
  rejected: { line: number; reason: string }[];
};

/** ฟิลด์ที่แก้ได้จากหน้าตาราง ส่งเฉพาะฟิลด์ที่เปลี่ยน ที่เหลือฝั่ง backend คงค่าเดิม */
export type SessionPatch = Partial<{
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  kind: string;
  subjectCode: string;
  subjectName: string;
  roomCode: string;
  groupCode: string;
}>;

export type Doc = {
  id: number;
  teacherId: number | null;
  kind: string;
  semester: string;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  sha256: string;
  status: string;
  uploadedAt: string;
};

export type UploadResult = { docId: number; state: string; duplicate: boolean };

export type ChatReply = {
  conversationId: string;
  answer: string;
  /** model = โมเดลตอบและผ่านการตรวจ · tool = ตอบจาก SQL ตรง (รวมกรณีโมเดลตอบผิดแล้ว fallback) */
  source: string;
  modelVersion: string | null;
  citations: number[];
  clarify: { question: string; options: { teacherId: number; label: string }[] } | null;
  verification: { checked: boolean; passed: boolean; mismatched: string[] };
  latencyMs: number;
};

/** backend ตอบ error เป็น RFC 9457 เสมอ จึงอ่าน detail มาแสดงตรง ๆ ได้ */
async function request<T>(path: string, role: Role, init?: RequestInit): Promise<T> {
  const response = await fetch(BASE + path, {
    ...init,
    headers: { ...init?.headers, Authorization: `Bearer ${TOKENS[role]}` },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail ?? `ติดต่อเซิร์ฟเวอร์ไม่สำเร็จ (${response.status})`);
  }
  return response.json();
}

const json = (body: unknown): RequestInit => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const listTeachers = (role: Role) => request<Teacher[]>("/teachers", role);

export const getSchedule = (id: number, role: Role) =>
  request<Session[]>(`/teachers/${id}/schedule`, role);

export function importTeachers(file: File, role: Role) {
  const body = new FormData();
  body.append("file", file);
  return request<ImportResult>("/teachers/import", role, { method: "POST", body });
}

/** correctionsLogged = จำนวนช่องที่ถูกบันทึกเป็น ground truth ของรายงานความแม่น */
export type PatchResult = { session: Session; correctionsLogged: number };

export const patchSession = (id: number, patch: SessionPatch, role: Role) =>
  request<PatchResult>(`/sessions/${id}`, role, { method: "PATCH", ...json(patch) });

/** ลบคาบ: 204 ไม่มี body จึงใช้ fetch ตรง ๆ แทน request<T> ที่อ่าน json เสมอ */
export async function deleteSession(id: number, role: Role): Promise<void> {
  const response = await fetch(`${BASE}/sessions/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${TOKENS[role]}` },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail ?? `ลบคาบไม่สำเร็จ (${response.status})`);
  }
}

/** รูปตารางต้นฉบับหนึ่งใบ ครูคนเดียวมีได้หลายใบ ใบละช่วงสัปดาห์ */
export type ScheduleImage = {
  path: string;
  weekFrom: number | null;
  weekTo: number | null;
  /** ข้อสังเกตของตัวสกัดเกี่ยวกับรูปใบนี้ เช่น หัวตารางถูกตัดขอบจนอ่านได้ไม่ครบ */
  note: string | null;
  sessions: number;
};

export const listImages = (id: number, role: Role) =>
  request<ScheduleImage[]>(`/teachers/${id}/images`, role);

/**
 * รูปต้องแนบ token เหมือน endpoint อื่น แต่ <img src> แนบ header ไม่ได้
 * จึงดึงเป็น blob แล้วคืน object URL ให้ผู้เรียก revoke เองตอนเลิกใช้
 */
export async function fetchImage(path: string, role: Role): Promise<string> {
  const response = await fetch(`${BASE}/schedule-images?path=${encodeURIComponent(path)}`, {
    headers: { Authorization: `Bearer ${TOKENS[role]}` },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail ?? `โหลดรูปตารางไม่สำเร็จ (${response.status})`);
  }
  return URL.createObjectURL(await response.blob());
}

export const listDocs = (role: Role, teacherId?: number) =>
  request<Doc[]>(teacherId ? `/docs?teacherId=${teacherId}` : "/docs", role);

export function uploadDoc(
  file: File,
  fields: { teacherId?: number; semester: string; kind?: string },
  role: Role,
) {
  const body = new FormData();
  body.append("file", file);
  body.append("semester", fields.semester);
  body.append("kind", fields.kind ?? "teacher");
  if (fields.teacherId) body.append("teacherId", String(fields.teacherId));
  return request<UploadResult>("/docs", role, { method: "POST", body });
}

/** teacherId ส่งไปเมื่อผู้ใช้กดปุ่มเลือกตัวคนตอนระบบถามกลับ พร้อมคำถามเดิมเพื่อให้ยังรู้ว่าถามวันไหน */
export const askChat = (
  text: string,
  conversationId: string | null,
  role: Role,
  teacherId?: number,
) => request<ChatReply>("/chat", role, { method: "POST", ...json({ text, conversationId, teacherId }) });

export const fullName = (t: Teacher) => `${t.prefix ?? ""}${t.firstName} ${t.lastName}`;

export const hour = (time: string) => Number(time.slice(0, 2));

export const hhmm = (time: string) => time.slice(0, 5);

/** เวลาเริ่มคาบเป็นชั่วโมงเต็มเสมอ ทั้ง drag และฟอร์มแก้ไขจึงส่งค่าเป็น "HH:00" */
export const atHour = (h: number) => `${String(h).padStart(2, "0")}:00`;

export type OcrAccuracy = {
  byField: { field: string; accuracy: number; n: number }[];
  totalReviewed: number;
  note: string;
};

export type ChatQuality = {
  answers: number;
  p95LatencyMs: number | null;
  medianLatencyMs: number | null;
  unansweredRate: number;
  clarifyRate: number;
  byTool: { tool: string; n: number }[];
  note: string;
};

export const getOcrAccuracy = (role: Role) => request<OcrAccuracy>("/reports/ocr-accuracy", role);

export const getChatQuality = (role: Role) => request<ChatQuality>("/reports/chat-quality", role);
