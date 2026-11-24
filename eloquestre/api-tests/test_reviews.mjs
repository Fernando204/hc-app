import { api, check, done, user } from './harness.mjs';

const emp = await user('Haras', 'employer');
const pro = await user('Tratador');
const other = await user('Outro', 'employer');

check('sem contato: não pode avaliar', (await api(`/profiles/${pro.id}/can-review`, { token: emp.token })).data.can_review === false);
check('salvar sem contato → 403', (await api(`/profiles/${pro.id}/reviews`, { method: 'PUT', token: emp.token, body: { rating: 5 } })).status === 403);
check('não avalia a si mesmo', (await api(`/profiles/${emp.id}/can-review`, { token: emp.token })).data.can_review === false);

const conv = (await api('/conversations', { method: 'POST', token: emp.token, body: { peer_id: pro.id } })).data.id;
await api(`/conversations/${conv}/messages`, { method: 'POST', token: emp.token, body: { body: 'Olá, pode vir amanhã?' } });
check('só eu ter enviado mensagem não basta', (await api(`/profiles/${pro.id}/can-review`, { token: emp.token })).data.can_review === false);
await api(`/conversations/${conv}/messages`, { method: 'POST', token: pro.token, body: { body: 'Posso sim!' } });
check('depois que ele respondeu, posso avaliar', (await api(`/profiles/${pro.id}/can-review`, { token: emp.token })).data.can_review === true);
check('e ele também pode me avaliar (recebeu mensagem minha)', (await api(`/profiles/${emp.id}/can-review`, { token: pro.token })).data.can_review === true);

check('nota fora de 1–5 → 400', (await api(`/profiles/${pro.id}/reviews`, { method: 'PUT', token: emp.token, body: { rating: 6 } })).status === 400);
check('avaliar 4 (204)', (await api(`/profiles/${pro.id}/reviews`, { method: 'PUT', token: emp.token, body: { rating: 4, comment: 'Pontual e cuidadoso.' } })).status === 204);
let p = (await api(`/profiles/${pro.id}`, { token: emp.token })).data;
check('nota recalculada: 4.0 (1 avaliação)', p.rating_avg === 4 && p.rating_count === 1);
check('minha avaliação', (await api(`/profiles/${pro.id}/reviews/mine`, { token: emp.token })).data?.rating === 4);
check('sem avaliação minha → 204 (null)', (await api(`/profiles/${pro.id}/reviews/mine`, { token: other.token })).status === 204);
check('editar substitui (não duplica): 5', (await api(`/profiles/${pro.id}/reviews`, { method: 'PUT', token: emp.token, body: { rating: 5, comment: 'Excelente' } })).status === 204);
const list = await api(`/profiles/${pro.id}/reviews`, { token: other.token });
p = (await api(`/profiles/${pro.id}`, { token: emp.token })).data;
check('lista com avaliador e nota atualizada', list.data.length === 1 && list.data[0].rating === 5 && list.data[0].reviewer_first_name === 'Haras' && p.rating_avg === 5 && p.rating_count === 1);
check('cliente não altera a nota pelo /me', (await api('/me', { method: 'PATCH', token: pro.token, body: { rating_avg: 1, rating_count: 99 } })).data.rating_avg === 5);

await api(`/blocks/${pro.id}`, { method: 'PUT', token: emp.token });
check('com bloqueio não pode avaliar', (await api(`/profiles/${emp.id}/can-review`, { token: pro.token })).data.can_review === false);
done();
