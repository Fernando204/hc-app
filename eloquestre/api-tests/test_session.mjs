import { readFileSync } from 'node:fs';
import { api, check, done, S, sleep } from './harness.mjs';

const LOG = new URL('./backend_test.log', import.meta.url);
const lastCode = (email) => {
  const lines = readFileSync(LOG, 'utf8').split(/\r?\n/);
  let code = null, mine = false;
  for (const l of lines) {
    if (l.includes('Para: ')) mine = l.includes(email);
    const m = l.match(/auth\/callback\?code=([A-Za-z0-9_-]+)/);
    if (m && mine) code = m[1];
  }
  return code;
};

const email = `sessao.${S}@teste.local`;
const reg = await api('/auth/register', { method: 'POST', body: { email, password: 'senha12345' } });
const t1 = reg.data.access_token;
await sleep(300);
const confirm = lastCode(email);
check('cadastro gera e-mail de confirmação (no console, sem SMTP)', !!confirm);

// confirmar e-mail
const ex = await api('/auth/exchange', { method: 'POST', body: { code: confirm } });
check('link de confirmação devolve sessão', ex.status === 200 && !!ex.data.access_token);
check('o mesmo link não funciona 2 vezes', (await api('/auth/exchange', { method: 'POST', body: { code: confirm } })).status === 400);
check('código inventado → 400', (await api('/auth/exchange', { method: 'POST', body: { code: 'abc' } })).status === 400);

// esqueci a senha
check('forgot-password para e-mail inexistente também é 204', (await api('/auth/forgot-password', { method: 'POST', body: { email: 'ninguem@x.com' } })).status === 204);
check('forgot-password (204)', (await api('/auth/forgot-password', { method: 'POST', body: { email } })).status === 204);
await sleep(300);
const reset = lastCode(email);
check('e-mail traz link com next=/reset-password', !!reset && readFileSync(LOG, 'utf8').includes(`code=${reset}&next=/reset-password`));
const sess = await api('/auth/exchange', { method: 'POST', body: { code: reset } });
const t2 = sess.data.access_token;
check('senha curta → 400', (await api('/auth/reset-password', { method: 'POST', token: t2, body: { password: '123' } })).status === 400);
check('reset-password sem token → 401', (await api('/auth/reset-password', { method: 'POST', body: { password: 'novasenha123' } })).status === 401);
const np = await api('/auth/reset-password', { method: 'POST', token: t2, body: { password: 'novasenha123' } });
check('nova senha salva e devolve token novo', np.status === 200 && !!np.data.access_token);
const t3 = np.data.access_token;
check('tokens antigos deixam de valer (outros aparelhos saem)', (await api('/me', { token: t1 })).status === 401 && (await api('/me', { token: t2 })).status === 401);
check('o token novo vale', (await api('/me', { token: t3 })).status === 200);
check('senha antiga não entra mais', (await api('/auth/login', { method: 'POST', body: { email, password: 'senha12345' } })).status === 401);
const login = await api('/auth/login', { method: 'POST', body: { email, password: 'novasenha123' } });
check('nova senha entra', login.status === 200);

// sair
const t4 = login.data.access_token;
check('logout simples (204) não derruba os outros aparelhos', (await api('/auth/logout', { method: 'POST', token: t4 })).status === 204 && (await api('/me', { token: t3 })).status === 200);
check('logout de todos os aparelhos', (await api('/auth/logout', { method: 'POST', token: t3, body: { all: true } })).status === 204
  && (await api('/me', { token: t3 })).status === 401 && (await api('/me', { token: t4 })).status === 401);
check('logout sem token → 401', (await api('/auth/logout', { method: 'POST' })).status === 401);

// token de conta excluída não vale em nenhuma rota
const del = (await api('/auth/login', { method: 'POST', body: { email, password: 'novasenha123' } })).data.access_token;
await api('/me', { method: 'DELETE', token: del });
check('conta excluída: token recusado em qualquer rota (401)', (await api('/posts', { method: 'POST', token: del, body: { caption: 'x' } })).status === 401 && (await api('/jobs', { token: del })).status === 401);
done();
