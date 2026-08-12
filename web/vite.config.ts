import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import path from "path";

export default defineConfig({
  define: {
    global: "globalThis"
  },

  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: [""],
      // The app has grown past the 2 MiB default; raise the precache limit
      // so the production build doesn't fail while trying to build the
      // service worker (Workbox refuses to precache oversized bundles).
      workbox: {
        maximumFileSizeToCacheInBytes: 6 * 1024 * 1024,
        /**
         * Opening a stored file in its own tab is a navigation, and the offline
         * fallback answered every navigation with index.html — so a task-chat
         * image or a payslip opened in a new tab arrived as the app, which then
         * had no route for it and showed its own 404. The file was on the server
         * the whole time. Anything the server owns is left to the server.
         */
        navigateFallbackDenylist: [/^\/api\//, /^\/ws\b/, /^\/uploads\//]
      },
      manifest: {
        name: "Pixous HR Portal",
        short_name: "HR Portal",
        description: "Employee & HR management for IT and field operations",
        theme_color: "#4F46E5",
        background_color: "#0F172A",
        display: "standalone",
        start_url: "/",
        icons: [
          { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" }
        ]
      }
    })
  ],

  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },

  server: {
    host: "0.0.0.0",
    port: 5174,
    strictPort: true,
    fs: {
      allow: [
        "../",
        "C:/Users/balas/Downloads"
      ]
    },
    proxy: {
      "/api": {
        target: "http://localhost:7060",
        changeOrigin: true,
        secure: false
      },
      "/ws": {
        target: "http://localhost:7060",
        changeOrigin: true,
        secure: false,
        ws: true
      }
    }
  }
});