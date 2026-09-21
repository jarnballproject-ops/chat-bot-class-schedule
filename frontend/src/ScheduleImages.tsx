import { useEffect, useState } from "react";
import { fetchImage, listImages, type Role, type ScheduleImage } from "./api";

/** week = ช่วงสัปดาห์ที่กริดกำลังแสดง ("" = ทุกสัปดาห์) ต้นฉบับต้องเป็นใบเดียวกับที่เทียบอยู่ */
type Props = { teacherId: number; role: Role; week: string };

type Loaded = ScheduleImage & { url: string | null };

const weekLabel = (image: ScheduleImage) =>
  image.weekFrom == null
    ? "ไม่ระบุช่วงสัปดาห์"
    : image.weekTo == null || image.weekTo === image.weekFrom
      ? `สัปดาห์ที่ ${image.weekFrom}`
      : `สัปดาห์ที่ ${image.weekFrom}–${image.weekTo}`;

/**
 * รูปตารางต้นฉบับที่ระบบใช้สกัดข้อมูล ไว้ให้ผู้ใช้เทียบกับคาบในตารางด้านบนได้ว่าอ่านถูกไหม
 * object URL ต้อง revoke เองตอนเปลี่ยนครูหรือออกจากหน้า ไม่งั้นรูปหลักร้อย KB ค้างในหน่วยความจำ
 */
const imageKey = (image: ScheduleImage) =>
  image.weekFrom == null ? "" : `${image.weekFrom}-${image.weekTo ?? image.weekFrom}`;

export default function ScheduleImages({ teacherId, role, week }: Props) {
  const [images, setImages] = useState<Loaded[] | null>(null);
  const [failed, setFailed] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const urls: string[] = [];

    setImages(null);
    setFailed(null);

    listImages(teacherId, role)
      .then(async (list) => {
        const loaded = await Promise.all(
          list.map(async (image) => {
            try {
              const url = await fetchImage(image.path, role);
              urls.push(url);
              return { ...image, url };
            } catch {
              // รูปหายไปจากโฟลเดอร์ไม่ควรทำให้ทั้งบล็อกหาย ยังบอกได้ว่ามีใบนี้อยู่
              return { ...image, url: null };
            }
          }),
        );
        if (alive) setImages(loaded);
      })
      .catch((e: Error) => {
        if (alive) setFailed(e.message);
      });

    return () => {
      alive = false;
      urls.forEach(URL.revokeObjectURL);
    };
  }, [teacherId, role]);

  if (failed) {
    return (
      <p className="notice notice--bad" role="alert">
        {failed}
      </p>
    );
  }

  if (images === null) {
    return (
      <p className="state__body" role="status">
        กำลังโหลดรูปตารางต้นฉบับ…
      </p>
    );
  }

  if (images.length === 0) {
    return null;
  }

  const shown = week ? images.filter((image) => imageKey(image) === week) : images;

  return (
    <section className="sources" aria-label="ตารางต้นฉบับ">
      <div className="sources__head">
        ตารางต้นฉบับ {shown.length} ใบ · คลิกที่รูปเพื่อเปิดขนาดเต็ม
      </div>
      {/* ใบต้นฉบับของช่วงนี้อาจไม่ได้เก็บไว้ ต้องบอกให้รู้ ไม่ใช่ปล่อยคอลัมน์ซ้ายว่างเฉย ๆ */}
      {shown.length === 0 && (
        <p className="state__body">ไม่มีรูปต้นฉบับของสัปดาห์ {week} ในระบบ</p>
      )}
      {shown.map((image) => (
        <figure className="source" key={image.path}>
          {image.url ? (
            <a href={image.url} target="_blank" rel="noreferrer">
              <img className="source__img" src={image.url} alt={`ตารางต้นฉบับ ${weekLabel(image)}`} />
            </a>
          ) : (
            <div className="state__body">ไม่พบไฟล์รูปใบนี้ในเครื่อง ({image.path})</div>
          )}
          <figcaption className="source__cap">
            {weekLabel(image)} · สกัดได้ {image.sessions} คาบ
            {/* ข้อสังเกตของตัวสกัด เช่น หัวตารางถูกตัดขอบ คนตรวจทานต้องเห็นก่อนเชื่อตัวเลขในใบนี้ */}
            {image.note && <span className="source__note">{image.note}</span>}
          </figcaption>
        </figure>
      ))}
    </section>
  );
}
