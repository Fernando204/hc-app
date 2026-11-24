// Teste do chat: REST + WebSocket com dois usuários (A e B). Uso: node test_chat.mjs http://localhost:8098
const B = process.argv[2];
const WS = B.replace(/^http/, 'ws');
const S = Date.now();
let ok = 0, bad = 0;
const check = (name, cond, extra = '') => { cond ? ok++ : bad++; console.log(`${cond ? '✔' : '✘'} ${name}${extra ? '  ' + extra : ''}`); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(path, { method = 'GET', body, token } = {}) {
  const r = await fetch(B + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const text = await r.text();
  return { status: r.status, data: text ? JSON.parse(text) : null };
}
async function user(name) {
  const r = await api('/auth/register', { method: 'POST', body: { email: `${name}.${S}@teste.local`, password: 'senha12345' } });
  return { token: r.data.access_token, id: r.data.user.id };
}
function socket(token) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${WS}/ws?token=${encodeURIComponent(token)}`);
    const events = [];
    ws.onmessage = (e) => events.push(JSON.parse(e.data));
    ws.onerror = () => reject(new Error('ws error'));
    ws.onopen = async () => { await sleep(150); resolve({ ws, events, send: (o) => ws.send(JSON.stringify(o)), take: (t) => events.filter((e) => e.type === t) }); };
  });
}

const a = await user('a'), b = await user('b'), c = await user('c');

// conversa
const open1 = await api('/conversations', { method: 'POST', token: a.token, body: { peer_id: b.id } });
const open2 = await api('/conversations', { method: 'POST', token: b.token, body: { peer_id: a.id } });
const conv = open1.data.id;
check('abrir conversa é idempotente por par (A→B e B→A dão o mesmo id)', open1.data.id === open2.data.id && /^[a-f0-9]{24}$/.test(conv));
check('não abre conversa consigo mesmo', (await api('/conversations', { method: 'POST', token: a.token, body: { peer_id: a.id } })).status === 400);
check('terceiro não lê a conversa (404)', (await api(`/conversations/${conv}/messages`, { token: c.token })).status === 404);

// WebSocket
let rejected = false;
try { await socket('token-invalido'); } catch { rejected = true; }
check('WebSocket recusa token inválido', rejected);

const wa = await socket(a.token);
const wb = await socket(b.token);
check('WebSocket conecta e envia "ready"', wa.take('ready').length === 1 && wb.take('ready').length === 1);

// 1) A envia pelo WebSocket com B online → já nasce "recebida"
wa.send({ type: 'send', client_id: 'cli-1', conversation_id: conv, body: 'Olá, tudo bem?' });
await sleep(400);
const echo = wa.take('message').find((e) => e.message.client_id === 'cli-1');
const atB = wb.take('message').find((e) => e.message.body === 'Olá, tudo bem?');
check('A recebe a confirmação com o mesmo client_id', !!echo);
check('B recebe a mensagem em tempo real', !!atB);
check('com B online a mensagem já nasce "delivered" (✓✓)', echo?.message.status === 'delivered', `status=${echo?.message.status}`);

// 2) digitando
wb.send({ type: 'typing', conversation_id: conv });
await sleep(250);
check('A vê "digitando…" de B', wa.take('typing').some((e) => e.user_id === b.id));

// 3) B lê → A recebe "read"
wb.send({ type: 'read', conversation_id: conv });
await sleep(400);
const readEv = wa.take('status').find((e) => e.status === 'read');
check('A recebe status "read" (✓✓ em destaque)', !!readEv && readEv.message_ids.includes(echo?.message.id));

// 4) B offline → A envia pelo REST → "sent"; B volta → "delivered" ao listar; depois lê
wb.ws.close();
await sleep(300);
const r1 = await api(`/conversations/${conv}/messages`, { method: 'POST', token: a.token, body: { body: 'Você viu a vaga?', client_id: 'cli-2' } });
check('com B offline a mensagem fica "sent" (✓)', r1.status === 201 && r1.data.status === 'sent', `status=${r1.data.status}`);
const r2 = await api(`/conversations/${conv}/messages`, { method: 'POST', token: a.token, body: { body: 'Você viu a vaga?', client_id: 'cli-2' } });
check('reenvio com o mesmo client_id não duplica', r2.data.id === r1.data.id);
const vazia = await api(`/conversations/${conv}/messages`, { method: 'POST', token: a.token, body: { body: '   ' } });
check('mensagem vazia é recusada (400)', vazia.status === 400);

const before = wa.take('status').length;
const listB = await api('/conversations', { token: b.token });
await sleep(300);
const deliveredEv = wa.take('status').slice(before).find((e) => e.status === 'delivered');
const convB = listB.data.find((x) => x.conversation_id === conv);
check('B vê 1 não lida na lista de conversas', convB?.unread_count === 1, `unread=${convB?.unread_count}`);
check('ao B abrir o app, A recebe "delivered" em tempo real', !!deliveredEv && deliveredEv.message_ids.includes(r1.data.id));
check('lista traz quem mandou a última e o status dela', convB?.last_message_sender_id === a.id && convB?.last_message_preview === 'Você viu a vaga?');

const readRest = await api(`/conversations/${conv}/read`, { method: 'POST', token: b.token });
await sleep(300);
check('B marca como lida pelo REST (204) e A recebe "read"', readRest.status === 204 && wa.take('status').some((e) => e.status === 'read' && e.message_ids.includes(r1.data.id)));

// 5) histórico salvo no banco com status
const hist = await api(`/conversations/${conv}/messages`, { token: a.token });
check('histórico vem do banco, mais novas primeiro, com status', hist.data.length === 2 && hist.data[0].body === 'Você viu a vaga?' && hist.data.every((m) => m.status === 'read' && m.read_at && m.delivered_at),
  hist.data.map((m) => `${m.body}=${m.status}`).join(' | '));
const listA = await api('/conversations', { token: a.token });
check('na lista de A a última mensagem aparece como "read"', listA.data[0]?.last_message_status === 'read' && listA.data[0]?.unread_count === 0);
const peer = await api(`/conversations/${conv}/peer`, { token: a.token });
check('GET /peer devolve o outro participante', peer.data.peer_id === b.id);

// 6) paginação por before
const older = await api(`/conversations/${conv}/messages?before=${encodeURIComponent(hist.data[0].created_at)}`, { token: a.token });
check('paginação com before devolve só as mais antigas', older.data.length === 1 && older.data[0].body === 'Olá, tudo bem?');

// 7) erro pelo WebSocket volta com client_id
wa.send({ type: 'send', client_id: 'cli-3', conversation_id: '000000000000000000000000', body: 'x' });
await sleep(300);
check('erro pelo WebSocket volta com o client_id', wa.take('error').some((e) => e.client_id === 'cli-3'));

wa.ws.close();
console.log(`\n${ok} ok, ${bad} falha(s)`);
process.exit(bad ? 1 : 0);
