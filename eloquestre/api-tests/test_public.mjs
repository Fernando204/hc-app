import { api, B, check, city, done, user } from './harness.mjs';

const emp = await user('Publico', 'employer', 'sp', { bio: 'Haras de teste' });
const job = await api('/jobs', { method: 'POST', token: emp.token, body: { title: 'Vaga pública', description: 'd', occupation_id: 'tratador', contract_type: 'clt', salary_min: 2500, salary_period: 'mes', ...city('sp') } });
const post = await api('/posts', { method: 'POST', token: emp.token, body: { caption: 'Olá mundo' } });
const draft = (await api('/auth/register', { method: 'POST', body: { email: `rasc.${Date.now()}@teste.local`, password: 'senha12345' } })).data.user.id;

const pub = (p) => fetch(B + p).then(async (r) => ({ status: r.status, data: r.ok ? await r.json() : null, cache: r.headers.get('cache-control') }));
const pj = await pub(`/public/jobs/${job.data.id}`);
check('vaga pública sem login', pj.status === 200 && pj.data.title === 'Vaga pública' && pj.data.city_name === 'São Paulo');
check('não expõe dados internos (coordenadas, contratante)', !('lat' in pj.data) && !('employer_id' in pj.data));
check('com cache público de 5 min', (pj.cache ?? '').includes('max-age=300'));
const pp = await pub(`/public/profiles/${emp.id}`);
check('perfil público sem e-mail nem plano', pp.status === 200 && pp.data.first_name === 'Publico' && !('email' in pp.data) && !('plan' in pp.data));
check('perfil por username', (await pub(`/public/profiles/by-username/${pp.data ? (await api('/me', { token: emp.token })).data.username : 'x'}`)).status === 200);
check('perfil sem cadastro concluído → 404', (await pub(`/public/profiles/${draft}`)).status === 404);
check('post público', (await pub(`/public/posts/${post.data.id}`)).data?.caption === 'Olá mundo');
await api(`/jobs/${job.data.id}`, { method: 'PATCH', token: emp.token, body: { status: 'closed' } });
check('vaga encerrada → 404', (await pub(`/public/jobs/${job.data.id}`)).status === 404);

// share-web de ponta a ponta (handler da Vercel chamando esta API)
process.env.API_URL = B;
process.env.PUBLIC_BASE_URL = 'https://elo.horsecontrol.com.br';
const { default: handler } = await import('file:///C:/eloquestre/elo/share-web/api/share.js');
const render = (kind, id) => new Promise((resolve) => {
  const res = { headers: {}, code: 200, setHeader(k, v) { this.headers[k] = v; }, status(c) { this.code = c; return this; }, send(b) { resolve({ code: this.code, body: b }); } };
  handler({ query: { kind, id }, url: `/${kind}/${id}?x` }, res);
});
const html = await render('profile', emp.id);
check('share-web: página do perfil com og:title e deep link', html.code === 200 && html.body.includes('<meta property="og:title" content="Publico Teste">') && html.body.includes('eloequestre://profile/'));
check('share-web: vaga encerrada mostra "indisponível" (404)', (await render('job', job.data.id)).code === 404);
check('share-web: id inválido não consulta a API (404)', (await render('post', '../etc')).code === 404);
done();
