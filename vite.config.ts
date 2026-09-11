import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { defineConfig, Plugin } from 'vite';
import express from 'express';
import dotenv from 'dotenv';
import { handleChatApi, handleAnalyzeChartApi, handleTtsApi } from './src/server/apiRouter.ts';

dotenv.config();

const apiPlugin = (): Plugin => ({
  name: 'api-server-middleware',
  configureServer(server) {
    const apiApp = express();
    apiApp.use(express.json({ limit: '35mb' }));
    apiApp.use(express.urlencoded({ extended: true, limit: '35mb' }));

    apiApp.post('/api/chat', (req, res) => {
      handleChatApi(req, res);
    });

    apiApp.post('/api/analyze-chart', (req, res) => {
      handleAnalyzeChartApi(req, res);
    });

    apiApp.get('/api/tts', (req, res) => {
      handleTtsApi(req, res);
    });

    apiApp.get('/api/health', (req, res) => {
      res.json({ status: 'ok', dev: true, time: new Date().toISOString() });
    });

    server.middlewares.use(apiApp);
  },
});

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss(), apiPlugin()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modifyâfile watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});
