import { api, check, done, user } from './harness.mjs';

// área isolada só deste teste (o banco local acumula dados de outros testes)
const lat0 = -5 - Math.random() * 20, lng0 = -30 - Math.random() * 5;
const at = (dLat = 0, dLng = 0) => ({ city_ibge: null, city_name: 'Cidade Teste', state: 'SP', lat: +(lat0 + dLat).toFixed(5), lng: +(lng0 + dLng).toFixed(5) });
const box = `south=${lat0 - 1}&west=${lng0 - 1}&north=${lat0 + 1}&east=${lng0 + 1}`;

const viewer = await user('Viewer', 'employer', 'sp');
const pessoa = await user('Pessoa', 'professional', 'sp', at(0.1, 0.1));
const indisponivel = await user('Ocupado', 'professional', 'sp', { ...at(0.2, 0.2), available: false });
const prest = await user('Prestador', 'professional', 'sp', { ...at(0.3, 0.3), occupation_id: 'ferrador' });
const longe = await user('Longe', 'professional', 'sp', at(5, 5));
const emp = await user('Empresa', 'employer', 'sp', at(0.4, 0.4));

await api('/services', { method: 'POST', token: prest.token, body: { title: 'Ferrageamento antigo', description: 'x', occupation_id: 'ferrador', radius_km: 50, ...at(0.3, 0.3) } });
await new Promise((r) => setTimeout(r, 20));
await api('/services', { method: 'POST', token: prest.token, body: { title: 'Ferrageamento novo', description: 'x', occupation_id: 'ferrador', radius_km: 50, ...at(0.3, 0.3) } });
const est = await api('/establishments', { method: 'POST', token: emp.token, body: { name: 'Haras do Teste', type: 'haras', ...at(0.4, 0.4) } });
const job = (t, contract) => api('/jobs', { method: 'POST', token: emp.token, body: { title: t, description: 'x', occupation_id: 'tratador', contract_type: contract, establishment_id: est.data.id, ...at(0.4, 0.4) } });
await job('Tratador', 'clt');
await job('Diarista', 'diaria');

const all = (await api(`/map/pins?${box}`, { token: viewer.token })).data;
const byUser = (id) => all.filter((p) => p.user_id === id);
check('pessoa disponível aparece como "pessoa"', byUser(pessoa.id).length === 1 && byUser(pessoa.id)[0].kind === 'pessoa');
check('indisponível não aparece', byUser(indisponivel.id).length === 0);
check('fora da área não aparece', byUser(longe.id).length === 0);
const sp = byUser(prest.id);
check('prestador: 1 balão "servico" (o mais recente), não "pessoa"', sp.length === 1 && sp[0].kind === 'servico' && sp[0].headline === 'Ferrageamento novo');
const ep = byUser(emp.id);
check('empresa: 1 balão agrupando 2 vagas, com nome do estabelecimento', ep.length === 1 && ep[0].kind === 'empresa' && ep[0].open_jobs === 2 && ep[0].name === 'Haras do Teste');
check('empresa: tipos de contrato das vagas do grupo', ep[0]?.contract_types.length === 2);
check('coordenadas = centro da cidade', ep[0]?.lat === at(0.4, 0.4).lat);

check('filtro kinds=empresa', (await api(`/map/pins?${box}&kinds=empresa`, { token: viewer.token })).data.every((p) => p.kind === 'empresa'));
check('filtro por ocupação (ferrador)', (await api(`/map/pins?${box}&occupation=ferrador`, { token: viewer.token })).data.every((p) => p.occupation_id === 'ferrador'));
check('não vejo a mim mesmo', !(await api(`/map/pins?${box}`, { token: pessoa.token })).data.some((p) => p.user_id === pessoa.id));
await api(`/blocks/${pessoa.id}`, { method: 'PUT', token: viewer.token });
check('bloqueado some do mapa', !(await api(`/map/pins?${box}`, { token: viewer.token })).data.some((p) => p.user_id === pessoa.id));
check('south > north → 400', (await api(`/map/pins?south=10&west=0&north=0&east=1`, { token: viewer.token })).status === 400);
done();
