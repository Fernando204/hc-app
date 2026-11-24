import { api, check, done, user } from './harness.mjs';

const a = await user('Ana', 'employer');
const b = await user('Bruno', 'professional');
const c = await user('Carla', 'professional');

const conv = (await api('/conversations', { method: 'POST', token: a.token, body: { peer_id: b.id } })).data.id;
await api(`/conversations/${conv}/messages`, { method: 'POST', token: b.token, body: { body: 'oi' } });

check('B aparece na busca de A antes do bloqueio', (await api('/professionals?q=Bruno', { token: a.token })).data.some((p) => p.id === b.id));
check('não bloqueia a si mesmo (400)', (await api(`/blocks/${a.id}`, { method: 'PUT', token: a.token })).status === 400);
check('bloquear inexistente (404)', (await api('/blocks/000000000000000000000000', { method: 'PUT', token: a.token })).status === 404);
check('A bloqueia B (204)', (await api(`/blocks/${b.id}`, { method: 'PUT', token: a.token })).status === 204);
check('bloquear de novo é idempotente (204)', (await api(`/blocks/${b.id}`, { method: 'PUT', token: a.token })).status === 204);
const list = await api('/me/blocks', { token: a.token });
check('lista de bloqueados de A traz B com nome', list.data.length === 1 && list.data[0].blocked_id === b.id && list.data[0].first_name === 'Bruno');

check('B some da busca de A', !(await api('/professionals?q=Bruno', { token: a.token })).data.some((p) => p.id === b.id));
check('vale nos dois sentidos: B não envia mensagem para A (403 blocked)', (await api(`/conversations/${conv}/messages`, { method: 'POST', token: b.token, body: { body: 'ei' } })).data?.message === 'blocked');
check('A também não envia', (await api(`/conversations/${conv}/messages`, { method: 'POST', token: a.token, body: { body: 'x' } })).status === 403);
check('a conversa some da lista de A e de B', (await api('/conversations', { token: a.token })).data.length === 0 && (await api('/conversations', { token: b.token })).data.length === 0);
check('B não abre nova conversa com A', (await api('/conversations', { method: 'POST', token: b.token, body: { peer_id: a.id } })).status === 403);

check('A desbloqueia (204)', (await api(`/blocks/${b.id}`, { method: 'DELETE', token: a.token })).status === 204);
check('depois do desbloqueio a conversa volta e B envia', (await api(`/conversations/${conv}/messages`, { method: 'POST', token: b.token, body: { body: 'voltei' } })).status === 201 && (await api('/conversations', { token: a.token })).data.length === 1);

// denúncias
const rep = (tok, body) => api('/reports', { method: 'POST', token: tok, body });
check('denúncia válida (204)', (await rep(c.token, { target_type: 'profile', target_id: b.id, reason: 'spam', details: 'mensagens repetidas' })).status === 204);
check('denúncia repetida também é sucesso (204)', (await rep(c.token, { target_type: 'profile', target_id: b.id, reason: 'spam' })).status === 204);
check('tipo de alvo inválido (400)', (await rep(c.token, { target_type: 'planeta', target_id: 'x', reason: 'spam' })).status === 400);
check('sem motivo (400)', (await rep(c.token, { target_type: 'profile', target_id: b.id })).status === 400);
check('sem token (401)', (await api('/reports', { method: 'POST', body: { target_type: 'profile', target_id: b.id, reason: 'spam' } })).status === 401);
done();
