import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // ล็อกพอร์ตไว้เพื่อให้ตรงกับ app.cors.allowed-origins ฝั่ง backend
    // strictPort กันไม่ให้ Vite แอบย้ายพอร์ตเองแล้ว CORS พังเงียบ ๆ
    port: 5273,
    strictPort: true,
  },
});
