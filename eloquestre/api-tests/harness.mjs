// Utilitário de testes da API (Mongo local). Importado pelos test_*.mjs.
export const B = process.argv[2] ?? 'http://localhost:8099';
export const S = Date.now().toString(36);
let ok = 0, bad = 0;
export const check = (name, cond, extra = '') => { cond ? ok++ : bad++; console.log(`${cond ? '✔' : '✘'} ${name}${extra ? '  ' + extra : ''}`); };
export const done = () => { console.log(`\n${ok} ok, ${bad} falha(s)`); process.exit(bad ? 1 : 0); };
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function api(path, { method = 'GET', body, token, raw } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  let payload;
  if (raw) payload = raw; else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const r = await fetch(B + path, { method, headers, body: payload });
  const text = await r.text();
  let data = null; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: r.status, data };
}

const CITIES = {
  sp: { city_ibge: 3550308, city_name: 'São Paulo', state: 'SP', lat: -23.55, lng: -46.63 },
  campinas: { city_ibge: 3509502, city_name: 'Campinas', state: 'SP', lat: -22.90, lng: -47.06 },
  rio: { city_ibge: 3304557, city_name: 'Rio de Janeiro', state: 'RJ', lat: -22.91, lng: -43.17 },
};
export const city = (k) => CITIES[k];

/** Cria conta + perfil concluído. type: professional | employer */
export async function user(name, type = 'professional', where = 'sp', extra = {}) {
  const r = await api('/auth/register', { method: 'POST', body: { email: `${name}.${S}@teste.local`, password: 'senha12345' } });
  const token = r.data.access_token, id = r.data.user.id;
  await api('/me', { method: 'PATCH', token, body: { account_type: type, first_name: name, last_name: 'Teste', username: `${name.toLowerCase()}_${S}`.slice(0, 30), occupation_id: 'tratador', ...city(where), onboarded: true, ...extra } });
  return { token, id, name };
}
