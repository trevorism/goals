import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
    plugins: [tailwindcss(), vue()],
    server: {
        host: "localhost",
        proxy: {
            '/api': {
                target: 'http://127.0.0.1:8080/',
                changeOrigin: false,
                secure: false,
                xfwd: true,
            }
        }
    }
})
