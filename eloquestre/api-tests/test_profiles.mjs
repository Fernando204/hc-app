import { api, check, done, S, user } from './harness.mjs';

// área isolada só deste teste (o banco de teste acumula dados de outras execuções)
const lat0 = -10 - Math.random() * 10, lng0 = -38 - Math.random() * 3;
const at = (dLat, dLng, extra = {}) => ({ city_ibge: null, city_name: 'Cidade Teste', state: 'SP', lat: +(lat0 + dLat).toFixed(5), lng: +(lng0 + dLng).toFixed(5), ...extra });

const viewer = await user('Contratante', 'employer', 'sp', at(0, 0));
const perto = await user('Perto', 'professional', 'sp', at(0.12, 0, { work_radius_km: 50 }));       // ~13 km
const raioCurto = await user('RaioCurto', 'professional', 'sp', at(0.15, 0, { work_radius_km: 10 })); // ~17 km, aceita só 10
const longe = await user('Longe', 'professional', 'sp', at(4, 0, { work_radius_km: 50 }));          // ~445 km
const muda = await user('Muda', 'professional', 'sp', at(4, 0.1, { willing_to_relocate: true }));
const q = `lat=${lat0}&lng=${lng0}&radius=100`;

check('sem token → 401', (await api('/me')).status === 401);
const me = await api('/me', { token: viewer.token });
check('GET /me em snake_case com onboarded e plano', me.data.onboarded === true && me.data.plan === 'free' && me.data.account_type === 'employer');
check('username inválido → 400', (await api('/me', { method: 'PATCH', token: viewer.token, body: { username: 'Nome Com Espaço' } })).status === 400);
check('username de outro → 409', (await api('/me', { method: 'PATCH', token: viewer.token, body: { username: `perto_${S}` } })).status === 409);
check('campos do servidor ignorados (plan, rating)', (await api('/me', { method: 'PATCH', token: viewer.token, body: { plan: 'standard', rating_avg: 5 } })).data.plan === 'free');

const ids = (r) => r.data.map((p) => p.id);
const home = await api(`/professionals?${q}&includeRelocate=false`, { token: viewer.token });
check('perto entra; raio do profissional menor que a distância → fora; longe → fora', ids(home).includes(perto.id) && !ids(home).includes(raioCurto.id) && !ids(home).includes(longe.id) && !ids(home).includes(muda.id));
check('com includeRelocate, quem aceita mudar entra', ids(await api(`/professionals?${q}&includeRelocate=true`, { token: viewer.token })).includes(muda.id));
check('card público sem onboarded/plan', home.data[0] && !('plan' in home.data[0]) && !('onboarded' in home.data[0]));
check('busca por nome', ids(await api('/professionals?q=RaioCurto', { token: viewer.token })).includes(raioCurto.id));
check('perfil público e por username', (await api(`/profiles/${perto.id}`, { token: viewer.token })).status === 200 && (await api(`/profiles/by-username/perto_${S}`, { token: viewer.token })).status === 200);
check('username disponível: o próprio sim, o de outro não', (await api(`/usernames/contratante_${S}/available`, { token: viewer.token })).data.available === true && (await api(`/usernames/perto_${S}/available`, { token: viewer.token })).data.available === false);
done();
