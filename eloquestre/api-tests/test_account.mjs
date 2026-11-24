import { api, B, check, city, done, user } from './harness.mjs';

// ---------- push tokens
const a = await user('Ana', 'employer');
check('token inválido → 400', (await api('/me/push-tokens', { method: 'PUT', token: a.token, body: { token: 'abc', platform: 'android' } })).status === 400);
check('registra token Expo (204)', (await api('/me/push-tokens', { method: 'PUT', token: a.token, body: { token: 'ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]', platform: 'android' } })).status === 204);
check('remove tokens (204)', (await api('/me/push-tokens', { method: 'DELETE', token: a.token })).status === 204);

// ---------- assinatura (webhook RevenueCat)
const hook = (auth, event) => fetch(`${B}/webhooks/revenuecat`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: auth } : {}) }, body: JSON.stringify({ event }) });
check('webhook sem segredo → 401', (await hook(null, { type: 'INITIAL_PURCHASE', app_user_id: a.id })).status === 401);
check('webhook com segredo errado → 401', (await hook('Bearer errado', { type: 'INITIAL_PURCHASE', app_user_id: a.id })).status === 401);
const exp = Date.now() + 30 * 86400000;
check('compra → plano standard com validade', (await hook('Bearer segredo-de-teste', { type: 'INITIAL_PURCHASE', app_user_id: a.id, expiration_at_ms: exp })).status === 200
  && (await api('/me', { token: a.token })).data.plan === 'standard');
check('cancelamento não tira o acesso (ignorado)', (await (await hook('Bearer segredo-de-teste', { type: 'CANCELLATION', app_user_id: a.id })).text()).startsWith('ignored') && (await api('/me', { token: a.token })).data.plan === 'standard');
await hook('Bearer segredo-de-teste', { type: 'EXPIRATION', app_user_id: a.id });
check('expiração → volta para free', (await api('/me', { token: a.token })).data.plan === 'free');
check('cliente não grava o plano pelo /me', (await api('/me', { method: 'PATCH', token: a.token, body: { plan: 'standard' } })).data.plan === 'free');

// ---------- excluir conta (cascata)
const x = await user('Excluir', 'employer', 'sp');
const pro = await user('Pro', 'professional', 'sp');
const est = await api('/establishments', { method: 'POST', token: x.token, body: { name: 'Haras X', type: 'haras', ...city('sp') } });
const job = await api('/jobs', { method: 'POST', token: x.token, body: { title: 'Vaga X', description: 'd', occupation_id: 'tratador', contract_type: 'clt', establishment_id: est.data.id, ...city('sp') } });
const conv = (await api(`/jobs/${job.data.id}/applications`, { method: 'POST', token: pro.token, body: { message: 'oi' } })).data.conversation_id;
await api(`/conversations/${conv}/messages`, { method: 'POST', token: x.token, body: { body: 'Olá!' } });
await api(`/profiles/${pro.id}/reviews`, { method: 'PUT', token: x.token, body: { rating: 2 } });
check('antes: Pro tem nota 2 dada por X', (await api(`/profiles/${pro.id}`, { token: pro.token })).data.rating_count === 1);
const post = await api('/posts', { method: 'POST', token: x.token, body: { caption: 'post de X' } });
await api(`/posts/${post.data.id}/like`, { method: 'POST', token: pro.token });
await api(`/blocks/${pro.id}`, { method: 'PUT', token: x.token });
await api(`/blocks/${pro.id}`, { method: 'DELETE', token: x.token });

check('DELETE /me (204)', (await api('/me', { method: 'DELETE', token: x.token })).status === 204);
check('token antigo não serve mais (401 no /me)', (await api('/me', { token: x.token })).status === 401);
check('vaga sumiu', (await api(`/jobs/${job.data.id}`, { token: pro.token })).status === 404);
check('candidatura do Pro sumiu', (await api('/me/applications', { token: pro.token })).data.length === 0);
check('conversa sumiu', (await api('/conversations', { token: pro.token })).data.every((c) => c.conversation_id !== conv) && (await api(`/conversations/${conv}/messages`, { token: pro.token })).status === 404);
check('publicação sumiu', (await api(`/posts/${post.data.id}`, { token: pro.token })).status === 404);
check('avaliação dada por X sumiu e a nota do Pro foi recalculada', (await api(`/profiles/${pro.id}`, { token: pro.token })).data.rating_count === 0);
check('perfil público de X → 404', (await api(`/profiles/${x.id}`, { token: pro.token })).status === 404);
check('pode criar conta nova com o mesmo e-mail', (await api('/auth/register', { method: 'POST', body: { email: `excluir.${(await import('./harness.mjs')).S}@teste.local`, password: 'senha12345' } })).status === 201);
done();
