#!/usr/bin/env bash
# โหลดโมเดลตอบแชท (ไม่ commit เข้า git ~1 GB)
# ตัวเล็กกว่า/เร็วกว่าถ้าเครื่องไม่ไหว: เปลี่ยน 1.5B เป็น 0.5B ทั้งสองที่แล้วตั้ง CHAT_MODEL_FILE ให้ตรง
set -euo pipefail
cd "$(dirname "$0")"

REPO=https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main
curl -L -o Qwen2.5-1.5B-Instruct-Q4_K_M.gguf "$REPO/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
