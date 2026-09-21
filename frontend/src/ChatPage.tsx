import { useEffect, useRef, useState } from "react";
import { askChat, type ChatReply, type Role } from "./api";

/** asked = คำถามที่ทำให้เกิดคำตอบนี้ ต้องเก็บไว้เพื่อส่งซ้ำตอนผู้ใช้เลือกตัวคนจากปุ่มถามกลับ */
type Message = { from: "user" | "bot"; text: string; reply?: ChatReply; asked?: string };

const EXAMPLES = [
  "อ.สจี วันจันทร์สอนอะไร",
  "อ.สจี วันพุธว่างไหม",
  "การสร้างสื่อดิจิทัล เรียนที่ไหน",
];

export default function ChatPage({ role }: { role: Role }) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const conversation = useRef<string | null>(null);
  const end = useRef<HTMLDivElement>(null);

  // ต้องเป็น block body: ถ้า return ค่ากลับไป React จะเอาไปเรียกเป็นฟังก์ชัน cleanup แล้วพัง
  useEffect(() => {
    end.current?.scrollIntoView({ block: "end" });
  }, [messages]);

  async function send(question: string, teacherId?: number, shownAs?: string) {
    const asked = question.trim();
    if (!asked || busy) return;
    setText("");
    setMessages((list) => [...list, { from: "user", text: shownAs ?? asked }]);
    setBusy(true);
    try {
      const reply = await askChat(asked, conversation.current, role, teacherId);
      conversation.current = reply.conversationId;
      setMessages((list) => [...list, { from: "bot", text: reply.answer, reply, asked }]);
    } catch (e) {
      setMessages((list) => [...list, { from: "bot", text: (e as Error).message }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <div className="blueprint chat">
        <i className="corner tl" />
        <i className="corner tr" />
        <i className="corner bl" />
        <i className="corner br" />

        <div className="chat__log" role="log" aria-live="polite" aria-label="บทสนทนา">
          {messages.length === 0 && (
            <div className="detail__empty">
              ถามเป็นภาษาพูดได้เลย ระบบตอบจากตารางที่เผยแพร่แล้วเท่านั้น
            </div>
          )}
          {messages.map((message, i) => (
            <div key={i} className={`bubble bubble--${message.from}`}>
              {message.text.split("\n").map((line, j) => (
                <div key={j}>{line}</div>
              ))}
              {message.reply && <ReplyFoot reply={message.reply} />}
              {message.reply?.clarify && (
                <div className="bubble__options">
                  {message.reply.clarify.options.map((option) => (
                    <button
                      key={option.teacherId}
                      className="btn btn-secondary"
                      onClick={() => send(message.asked ?? option.label, option.teacherId, option.label)}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
          <div ref={end} />
        </div>

        <form
          className="chat__composer"
          onSubmit={(e) => {
            e.preventDefault();
            send(text);
          }}
        >
          <input
            className="input"
            aria-label="คำถามเกี่ยวกับตารางสอน"
            placeholder="เช่น อ.สจี พรุ่งนี้สอนอะไร"
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <button className="btn btn-primary" disabled={busy || !text.trim()}>
            {busy ? "กำลังค้น…" : "ถาม"}
          </button>
        </form>

        <div className="chat__examples">
          {EXAMPLES.map((example) => (
            <button key={example} className="btn btn-ghost" onClick={() => send(example)}>
              {example}
            </button>
          ))}
        </div>

        <p className="notice" style={{ color: "var(--color-neutral-700)" }}>
          ทุกคำตอบบอกที่มาเสมอ · คำตอบจากโมเดลต้องผ่านการเทียบกับตารางจริงก่อน ไม่ผ่านระบบจะใช้คำตอบจาก SQL แทน
        </p>
      </div>
    </div>
  );
}

/**
 * ท้ายทุกฟองต้องตอบได้ว่าคำตอบนี้มาจากไหน (กฎข้อ 1 ของชั้นแชท)
 * verification.checked = เคยส่งให้โมเดลตอบ · passed=false = โมเดลตอบผิด ระบบเปลี่ยนไปใช้ SQL แทน
 */
function ReplyFoot({ reply }: { reply: ChatReply }) {
  const source =
    reply.source === "model"
      ? `โมเดล ${reply.modelVersion ?? ""} · ตรวจกับตารางจริงแล้ว`
      : "ค้นจากฐานข้อมูลตาราง";
  return (
    <div className="bubble__foot">
      ที่มา: {source}
      {reply.citations.length > 0 && ` · อ้างอิง ${reply.citations.length} คาบ`}
      {` · ${reply.latencyMs} มิลลิวินาที`}
      {reply.verification?.checked && !reply.verification.passed && (
        <div className="notice notice--bad">
          คำตอบของโมเดลไม่ตรงกับตาราง ({reply.verification.mismatched.join(", ")}) จึงใช้คำตอบจากฐานข้อมูลแทน
        </div>
      )}
    </div>
  );
}
