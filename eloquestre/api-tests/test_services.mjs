import { api, check, city, done, user } from './harness.mjs';

const vet = await user('Vet', 'professional', 'campinas', { occupation_id: 'veterinario' });
const ferr = await user('Ferrador', 'professional', 'sp', { occupation_id: 'ferrador' });
const emp = await user('Haras', 'employer', 'sp');

const body = { title: 'Atendimento veterinário equino', description: 'Clínica, vacinação e exames a campo.', occupation_id: 'veterinario', price_from: 250, price_unit: 'servico', radius_km: 100, ...city('campinas') };
check('contratante não oferece serviço (403)', (await api('/services', { method: 'POST', token: emp.token, body })).status === 403);
check('raio fora da lista (400)', (await api('/services', { method: 'POST', token: vet.token, body: { ...body, radius_km: 75 } })).status === 400);
const s1 = await api('/services', { method: 'POST', token: vet.token, body });
check('descrição do serviço é opcional', (await api('/services', { method: 'POST', token: vet.token, body: { ...body, description: undefined, active: false } })).status === 201);
check('prestador cria serviço (201)', s1.status === 201 && s1.data.active === true && s1.data.radius_km === 100);
const s2 = await api('/services', { method: 'POST', token: ferr.token, body: { title: 'Casqueamento e ferrageamento', description: 'Atendo haras da capital.', occupation_id: 'ferrador', price_from: 120, price_unit: 'servico', radius_km: 20, ...city('sp') } });

// busca a partir de São Paulo
const sp = await api('/services?lat=-23.55&lng=-46.63', { token: emp.token });
check('de SP: vê o ferrador (0 km, raio 20) e o vet (≈85 km, raio 100)', sp.data.length === 2 && sp.data[0].id === s2.data.id, sp.data.map((s) => s.title).join(' | '));
check('card traz o prestador', sp.data[0].provider_first_name === 'Ferrador' && sp.data[0].provider_rating_avg === 0);
const rio = await api('/services?lat=-22.91&lng=-43.17', { token: emp.token });
check('do Rio: ninguém atende (fora dos raios)', rio.data.length === 0);
check('filtro por ocupação', (await api('/services?occupation=veterinario', { token: emp.token })).data.every((s) => s.occupation_id === 'veterinario'));

// edição, inativação, perfil
check('outro não edita (404)', (await api(`/services/${s1.data.id}`, { method: 'PUT', token: ferr.token, body: { title: 'x' } })).status === 404);
check('dono edita o raio para 20 e sai do alcance de SP', (await api(`/services/${s1.data.id}`, { method: 'PUT', token: vet.token, body: { radius_km: 20 } })).data.radius_km === 20
  && !(await api('/services?lat=-23.55&lng=-46.63', { token: emp.token })).data.some((s) => s.id === s1.data.id));
await api(`/services/${s2.data.id}`, { method: 'PUT', token: ferr.token, body: { active: false } });
check('inativo some da busca', !(await api('/services', { token: emp.token })).data.some((s) => s.id === s2.data.id));
check('no perfil: outros não veem o inativo; o dono vê', (await api(`/profiles/${ferr.id}/services`, { token: emp.token })).data.length === 0 && (await api(`/profiles/${ferr.id}/services`, { token: ferr.token })).data.length === 1);
check('GET /services/{id} traz o card', (await api(`/services/${s1.data.id}`, { token: emp.token })).data.provider_first_name === 'Vet');

// bloqueio
await api(`/blocks/${vet.id}`, { method: 'PUT', token: emp.token });
check('bloqueado some da busca e do perfil', !(await api('/services', { token: emp.token })).data.some((s) => s.provider_id === vet.id) && (await api(`/profiles/${vet.id}/services`, { token: emp.token })).data.length === 0);
check('excluir (204) e sumir (404)', (await api(`/services/${s1.data.id}`, { method: 'DELETE', token: vet.token })).status === 204 && (await api(`/services/${s1.data.id}`, { token: vet.token })).status === 404);
done();
