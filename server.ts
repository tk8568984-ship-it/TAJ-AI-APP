import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import { handleChatApi, handleAnalyzeChartApi, handleTtsApi } from './src/server/apiRouter.ts';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 3000;

// Middleware for parsing JSON with high limit for image screenshots
app.use(express.json({ limit: '35mb' }));
app.use(express.urlencoded({ extended: true, limit: '35mb' }));

// API Routes
app.post('/api/chat', (req, res) => {
  handleChatApi(req, res);
});

app.post('/api/analyze-chart', (req, res) => {
  handleAnalyzeChartApi(req, res);
});

app.get('/api/tts', (req, res) => {
  handleTtsApi(req, res);
});

// Download Source Code ZIP route for APK conversion
app.get('/api/download-zip', (req, res) => {
  const zipPath = path.resolve(process.cwd(), 'public', 'taj-ai-app.zip');
  res.download(zipPath, 'taj-ai-app.zip');
});

app.get('/taj-ai-app.zip', (req, res) => {
  const zipPath = path.resolve(process.cwd(), 'public', 'taj-ai-app.zip');
  res.download(zipPath, 'taj-ai-app.zip');
});

app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', time: new Date().toISOString(), model: 'gemini-3.8-flash' });
});

// Serve static files from Vite build output 'dist'
const distPath = path.resolve(__dirname, 'dist');
app.use(express.static(distPath));

// Fallback to index.html for SPA client-side routing
app.get('*', (req, res) => {
  res.sendFile(path.resolve(distPath, 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Apex AI Server running on port ${PORT}`);
});
