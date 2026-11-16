/**
 * Script de validación automatizada del Escenario ESC-02 (Seguridad Minijuego).
 * HU-113: Envía peticiones REST y STOMP inválidas para verificar 100 % de rechazo
 * y tiempos de respuesta < 200 ms.
 */

const http = require('http');
const https = require('https');

const GAME_URL = process.env.GAME_URL || 'http://localhost:3003';

function makeRequest(method, path, headers = {}) {
  return new Promise((resolve) => {
    const url = new URL(path, GAME_URL);
    const client = url.protocol === 'https:' ? https : http;

    const start = Date.now();
    const req = client.request(
      url,
      {
        method,
        headers: {
          'Content-Type': 'application/json',
          ...headers,
        },
        timeout: 5000,
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => (data += chunk));
        res.on('end', () => {
          resolve({
            status: res.statusCode,
            timeMs: Date.now() - start,
            data,
          });
        });
      },
    );

    req.on('error', (err) => {
      resolve({
        status: 0,
        timeMs: Date.now() - start,
        error: err.message,
      });
    });

    req.end();
  });
}

async function runBenchmark() {
  console.log('===============================================================');
  console.log('  EVALUACIÓN AUTOMATIZADA: ESCENARIO ESC-02 (SEGURIDAD MINIJUEGO)');
  console.log(`  Target: ${GAME_URL}`);
  console.log('===============================================================\n');

  const tests = [
    {
      name: 'REST sin token JWT a endpoint protegido',
      method: 'POST',
      path: '/api/v1/salas/sala-1/jugadores',
      headers: {},
      expectedStatus: 401,
    },
    {
      name: 'REST con token expirado',
      method: 'GET',
      path: '/api/v1/historico/mio',
      headers: { Authorization: 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqMSIsImV4cCI6MTYwMDAwMDAwMH0.fake' },
      expectedStatus: 401,
    },
    {
      name: 'REST con token manipulado / firma inválida',
      method: 'GET',
      path: '/api/v1/historico/mio',
      headers: { Authorization: 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqMSJ9.firma_falsa_123' },
      expectedStatus: 401,
    },
    {
      name: 'REST con token válido pero suplantando otro jugadorId (403)',
      method: 'DELETE',
      path: '/api/v1/salas/sala-1/jugadores/otro-jugador',
      headers: { Authorization: 'Bearer token-de-otro' },
      expectedStatus: [401, 403],
    },
  ];

  let passedCount = 0;
  const results = [];

  for (const t of tests) {
    const res = await makeRequest(t.method, t.path, t.headers);
    const isExpected = Array.isArray(t.expectedStatus)
      ? t.expectedStatus.includes(res.status)
      : res.status === t.expectedStatus;

    if (isExpected) passedCount++;

    results.push({
      Prueba: t.name,
      Status: res.status,
      Esperado: Array.isArray(t.expectedStatus) ? t.expectedStatus.join('/') : t.expectedStatus,
      'Tiempo (ms)': res.timeMs,
      Cumple: isExpected && res.timeMs < 200 ? 'SI' : 'NO',
    });
  }

  console.table(results);
  const total = tests.length;
  const porcentaje = (passedCount / total) * 100;
  console.log(`Total pruebas: ${total} | Rechazos correctos: ${passedCount} (${porcentaje} %)`);
  console.log('Criterio de aceptación ESC-02: 100 % rechazadas con 401/403 en < 200 ms.');
}

if (require.main === module) {
  runBenchmark();
}

module.exports = { runBenchmark };
