import { api, check, city, done, user } from './harness.mjs';

const emp = await user('Haras', 'employer', 'sp');
const emp2 = await user('Hipica', 'employer', 'rio');
const pro = await user('Joao', 'professional', 'campinas');
const pro2 = await user('Maria', 'professional', 'sp');

// estabelecimentos
const est = await api('/establishments', { method: 'POST', token: emp.token, body: { name: 'Haras Boa Vista', type: 'haras', description: 'Quarto de Milha', ...city('sp') } });
check('cria estabelecimento (201)', est.status === 201 && est.data.owner_id === emp.id && est.data.city_name === 'São Paulo');
check('tipo inválido → 400', (await api('/establishments', { method: 'POST', token: emp.token, body: { name: 'X', type: 'castelo', ...city('sp') } })).status === 400);
check('outro dono não edita (404)', (await api(`/establishments/${est.data.id}`, { method: 'PUT', token: emp2.token, body: { name: 'Hack' } })).status === 404);
const upd = await api(`/establishments/${est.data.id}`, { method: 'PUT', token: emp.token, body: { description: 'Quarto de Milha e Mangalarga' } });
check('dono edita parcialmente (mantém nome e cidade)', upd.data.name === 'Haras Boa Vista' && upd.data.description.includes('Mangalarga') && upd.data.lat === -23.55);
check('lista só os meus', (await api('/establishments', { token: emp.token })).data.length === 1 && (await api('/establishments', { token: emp2.token })).data.length === 0);

// vagas
const jobBody = { title: 'Tratador de cavalos', description: 'Cuidar de 20 animais, alimentação e limpeza das baias.', occupation_id: 'tratador', contract_type: 'clt', salary_min: 2500, salary_max: 3200, salary_period: 'mes', housing: true, meals: true, vacancies: 2, duties: ['Alimentação', 'Limpeza'], establishment_id: est.data.id, ...city('sp') };
check('profissional não publica vaga (403)', (await api('/jobs', { method: 'POST', token: pro.token, body: jobBody })).status === 403);
check('salário mínimo > máximo → 400', (await api('/jobs', { method: 'POST', token: emp.token, body: { ...jobBody, salary_min: 5000 } })).status === 400);
check('estabelecimento de outro → 404', (await api('/jobs', { method: 'POST', token: emp2.token, body: { ...jobBody, ...city('rio') } })).status === 404);
const job = await api('/jobs', { method: 'POST', token: emp.token, body: jobBody });
check('contratante publica vaga (201)', job.status === 201 && job.data.status === 'open' && job.data.applications_count === 0 && job.data.duties.length === 2);
const jobRio = await api('/jobs', { method: 'POST', token: emp2.token, body: { title: 'Cavaleiro para provas', description: 'Hípica no Rio busca cavaleiro de salto.', occupation_id: 'cavaleiro', contract_type: 'pj', ...city('rio') } });
const semDescricao = await api('/jobs', { method: 'POST', token: emp.token, body: { title: 'Sem descrição', occupation_id: 'tratador', contract_type: 'clt', ...city('sp') } });
check('descrição é opcional (como no app)', semDescricao.status === 201 && semDescricao.data.description === '');
check('vaga sem estabelecimento também é aceita', jobRio.status === 201 && jobRio.data.establishment_id === null);

// busca
const near = await api(`/jobs?lat=-22.90&lng=-47.06&radius=100`, { token: pro.token });
check('busca por raio (Campinas, 100 km) acha a de SP e não a do Rio', near.data.length >= 1 && near.data.some((j) => j.id === job.data.id) && !near.data.some((j) => j.id === jobRio.data.id));
const card = near.data.find((j) => j.id === job.data.id);
check('card traz estabelecimento e contratante', card?.establishment_name === 'Haras Boa Vista' && card?.employer_first_name === 'Haras' && card?.applied_by_me === false);
check('busca por texto sem acento ("tratador", "baias")', (await api('/jobs?q=BAIAS', { token: pro.token })).data.some((j) => j.id === job.data.id));
check('busca por estabelecimento ("boa vista")', (await api('/jobs?q=boa%20vista', { token: pro.token })).data.some((j) => j.id === job.data.id));
check('filtro por contrato (pj) só traz PJ', (await api('/jobs?contract=pj', { token: pro.token })).data.every((j) => j.contract_type === 'pj'));
check('GET /jobs/{id} inexistente → 404', (await api('/jobs/000000000000000000000000', { token: pro.token })).status === 404);

// candidatura
const ap = await api(`/jobs/${job.data.id}/applications`, { method: 'POST', token: pro.token, body: { message: 'Tenho 5 anos de experiência.' } });
check('candidatura devolve a conversa (201)', ap.status === 201 && /^[a-f0-9]{24}$/.test(ap.data.conversation_id));
const msgs = await api(`/conversations/${ap.data.conversation_id}/messages`, { token: emp.token });
check('mensagem de interesse chegou ao contratante', msgs.data[0]?.body.startsWith('Olá! Tenho interesse na vaga "Tratador de cavalos".') && msgs.data[0]?.body.includes('5 anos'));
const ap2 = await api(`/jobs/${job.data.id}/applications`, { method: 'POST', token: pro.token, body: { message: 'de novo' } });
check('candidatar de novo é idempotente (não duplica mensagem)', ap2.data.conversation_id === ap.data.conversation_id && (await api(`/conversations/${ap.data.conversation_id}/messages`, { token: emp.token })).data.length === 1);
check('não se candidata à própria vaga', (await api(`/jobs/${job.data.id}/applications`, { method: 'POST', token: emp.token, body: {} })).status === 400);
check('applied_by_me = true depois de candidatar', (await api(`/jobs/${job.data.id}`, { token: pro.token })).data.applied_by_me === true);
await api(`/jobs/${job.data.id}/applications`, { method: 'POST', token: pro2.token, body: {} });

const apps = await api(`/jobs/${job.data.id}/applications`, { token: emp.token });
check('dono vê os 2 candidatos com nome e cidade', apps.data.length === 2 && apps.data.some((a) => a.applicant_first_name === 'Joao' && a.applicant_city_name === 'Campinas'));
check('outro não vê os candidatos (404)', (await api(`/jobs/${job.data.id}/applications`, { token: emp2.token })).status === 404);
const appId = apps.data.find((a) => a.applicant_id === pro.id).id;
check('candidato não muda o status (404)', (await api(`/applications/${appId}`, { method: 'PATCH', token: pro.token, body: { status: 'hired' } })).status === 404);
check('dono marca "shortlisted" (204)', (await api(`/applications/${appId}`, { method: 'PATCH', token: emp.token, body: { status: 'shortlisted' } })).status === 204);
const mine = await api('/me/applications', { token: pro.token });
check('candidato vê a candidatura com o novo status', mine.data[0]?.status === 'shortlisted' && mine.data[0]?.job_title === 'Tratador de cavalos');
check('contador de candidaturas = 2', (await api(`/jobs/${job.data.id}`, { token: emp.token })).data.applications_count === 2);

// status e exclusão
check('pausar a vaga (204)', (await api(`/jobs/${job.data.id}`, { method: 'PATCH', token: emp.token, body: { status: 'paused' } })).status === 204);
check('vaga pausada some da busca', !(await api('/jobs?q=tratador', { token: pro.token })).data.some((j) => j.id === job.data.id));
check('candidatar-se a vaga pausada → job_not_open (409)', (await api(`/jobs/${job.data.id}/applications`, { method: 'POST', token: pro2.token, body: {} })).data?.message === 'job_not_open');
check('/me/jobs mostra a vaga pausada ao dono', (await api('/me/jobs', { token: emp.token })).data.some((j) => j.id === job.data.id && j.status === 'paused'));
check('editar título mantém o resto', (await api(`/jobs/${job.data.id}`, { method: 'PUT', token: emp.token, body: { title: 'Tratador(a) de cavalos' } })).data.salary_max === 3200);

await api(`/establishments/${est.data.id}`, { method: 'DELETE', token: emp.token });
check('excluir estabelecimento deixa a vaga sem estabelecimento', (await api(`/jobs/${job.data.id}`, { token: emp.token })).data.establishment_id === null);
check('excluir vaga (204) apaga candidaturas', (await api(`/jobs/${job.data.id}`, { method: 'DELETE', token: emp.token })).status === 204 && (await api('/me/applications', { token: pro.token })).data.length === 0);
check('pode publicar vaga (paywall desligado)', (await api('/me/can-post-jobs', { token: emp.token })).data.can_post_jobs === true);
done();
