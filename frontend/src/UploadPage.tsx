import { useEffect, useState } from "react";
import {
  fullName,
  listDocs,
  uploadDoc,
  type Doc,
  type Role,
  type Teacher,
  type UploadResult,
} from "./api";

const KB = 1024;

const sizeText = (bytes: number) =>
  bytes < KB * KB ? `${Math.round(bytes / KB)} KB` : `${(bytes / KB / KB).toFixed(1)} MB`;

const timeText = (iso: string) => new Date(iso).toLocaleString("th-TH", { dateStyle: "short", timeStyle: "short" });

type Props = {
  role: Role;
  teachers: Teacher[];
  /** ครูที่เลือกค้างไว้จากทะเบียนทางซ้าย เป็นเจ้าของไฟล์ที่กำลังจะอัปโหลด */
  teacher: Teacher | null;
};

export default function UploadPage({ role, teachers, teacher }: Props) {
  const [asGroup, setAsGroup] = useState(false);
  const [semester, setSemester] = useState("1/2569");
  const [file, setFile] = useState<File | null>(null);
  const [dropping, setDropping] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const [docs, setDocs] = useState<Doc[]>([]);
  const [reloadKey, setReloadKey] = useState(0);

  const canUpload = role === "staff";

  useEffect(() => {
    if (role === "student") return;
    listDocs(role)
      .then(setDocs)
      .catch((e: Error) => setFailed(e.message));
  }, [role, reloadKey]);

  async function send() {
    if (!file) return;
    setBusy(true);
    setFailed(null);
    setResult(null);
    try {
      setResult(
        await uploadDoc(
          file,
          { teacherId: asGroup ? undefined : (teacher?.id ?? undefined), semester, kind: asGroup ? "class_group" : "teacher" },
          role,
        ),
      );
      setFile(null);
      setReloadKey((k) => k + 1);
    } catch (e) {
      setFailed((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (!canUpload) {
    return (
      <div className="state">
        <div className="state__title">หน้านี้สำหรับเจ้าหน้าที่</div>
        <p className="state__body">
          สลับบทบาทเป็น “เจ้าหน้าที่” ที่แถบด้านบนเพื่ออัปโหลดไฟล์ตารางสอนของอาจารย์
        </p>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="blueprint upload">
        <i className="corner tl" />
        <i className="corner tr" />
        <i className="corner bl" />
        <i className="corner br" />

        <div className="detail__head">
          <div className="detail__title">อัปโหลดไฟล์ตารางสอนรายอาจารย์</div>
          <div className="detail__note">PDF หรือรูปถ่าย/สแกน · ไม่เกิน 5 MB ต่อไฟล์</div>
        </div>

        {/* ครูมาจากทะเบียนทางซ้าย ไม่ทำ dropdown ซ้ำอีกอัน บริบทเดียวใช้ทั้งระบบ */}
        <div className="detail__row detail__row--upload">
          <div className="field">
            <span className="field-label">อาจารย์เจ้าของตาราง</span>
            <div className="owner">
              <strong>{asGroup ? "ไม่ผูกกับอาจารย์" : (teacher ? fullName(teacher) : "ยังไม่ได้เลือก")}</strong>
              <span>
                {asGroup
                  ? "ไฟล์นี้จะถูกบันทึกเป็นตารางของกลุ่มเรียน"
                  : "เลือกคนอื่นได้จากทะเบียนครูทางซ้าย"}
              </span>
            </div>
          </div>
          <div className="field">
            <label htmlFor="u-semester">ภาคเรียน</label>
            <input
              id="u-semester"
              className="input"
              value={semester}
              onChange={(e) => setSemester(e.target.value)}
            />
          </div>
          <div className="field">
            <span className="field-label">ชนิดตาราง</span>
            <label className="check">
              <input type="checkbox" checked={asGroup} onChange={(e) => setAsGroup(e.target.checked)} />
              <span>เป็นตารางกลุ่มเรียน</span>
            </label>
          </div>
        </div>

        {/* ลากไฟล์มาวางได้ และยังกดเลือกไฟล์ผ่าน input ปกติได้ เพื่อให้ใช้คีย์บอร์ดอย่างเดียวก็ทำงานจบ */}
        <label
          className={`dropzone${dropping ? " dropzone--over" : ""}`}
          onDragOver={(e) => {
            e.preventDefault();
            setDropping(true);
          }}
          onDragLeave={() => setDropping(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDropping(false);
            const dropped = e.dataTransfer.files?.[0];
            if (dropped) setFile(dropped);
          }}
        >
          <input
            type="file"
            className="sr-only"
            aria-label="ไฟล์ตารางสอน"
            accept="application/pdf,image/jpeg,image/png,image/webp"
            onChange={(e) => {
              setFile(e.target.files?.[0] ?? null);
              e.target.value = "";
            }}
          />
          <strong>{file ? file.name : "ลากไฟล์มาวางที่นี่ หรือคลิกเพื่อเลือกไฟล์"}</strong>
          <span>{file ? sizeText(file.size) : "รองรับ pdf · jpg · png · webp"}</span>
        </label>

        <div className="detail__foot">
          <button className="btn btn-primary" disabled={!file || busy} onClick={send}>
            {busy ? "กำลังอัปโหลด…" : "อัปโหลดไฟล์นี้"}
          </button>
          {file && (
            <button className="btn btn-ghost" onClick={() => setFile(null)}>
              เอาไฟล์ออก
            </button>
          )}
          {result && (
            <p className="notice notice--ok">
              {result.duplicate
                ? `ไฟล์นี้เคยอัปโหลดแล้ว (เอกสารเลขที่ ${result.docId}) ระบบไม่สร้างซ้ำ`
                : `อัปโหลดแล้ว เอกสารเลขที่ ${result.docId} · สถานะ ${result.state}`}
            </p>
          )}
          {failed && <p className="notice notice--bad">{failed}</p>}
        </div>

        <p className="notice" style={{ color: "var(--color-neutral-700)", marginTop: "10px" }}>
          ยังไม่มีคิว OCR ในสไลซ์นี้ ไฟล์จะค้างสถานะ <code>uploaded</code> จนกว่าตัวอ่านตารางจะเปิดใช้
        </p>
      </div>

      <div className="blueprint upload">
        <div className="pane__head">
          <span>ไฟล์ที่อัปโหลดแล้ว</span>
          <span>{docs.length} ไฟล์</span>
        </div>
        {docs.length === 0 && <div className="detail__empty">ยังไม่มีไฟล์ในระบบ</div>}
        {docs.map((doc) => {
          const owner = teachers.find((t) => t.id === doc.teacherId);
          return (
            <div className="docrow" key={doc.id}>
              <span className="docrow__name">{doc.filename}</span>
              <span className="docrow__meta">{owner ? fullName(owner) : "ไม่ระบุอาจารย์"}</span>
              <span className="docrow__meta">ภาคเรียน {doc.semester}</span>
              <span className="docrow__meta">{sizeText(doc.sizeBytes)}</span>
              <span className="docrow__meta">{timeText(doc.uploadedAt)}</span>
              <span className="docrow__status">{doc.status}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
