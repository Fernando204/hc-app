import { api, check, done, user } from './harness.mjs';

const a = await user('Autor');
const b = await user('Leitor');
const r1 = await user('Den1'), r2 = await user('Den2'), r3 = await user('Den3');

const png = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='), (c) => c.charCodeAt(0));
const f = new FormData(); f.append('file', new Blob([png], { type: 'image/png' }), 'a.png');
const up = (await api('/uploads?bucket=media', { method: 'POST', token: a.token, raw: f })).data.path;

check('publicação vazia → 400', (await api('/posts', { method: 'POST', token: a.token, body: { caption: '  ', media: [] } })).status === 400);
check('mídia de outra pessoa → 400', (await api('/posts', { method: 'POST', token: b.token, body: { caption: 'x', media: [{ path: up, type: 'image' }] } })).status === 400);
check('vídeo > 180 s → 400', (await api('/posts', { method: 'POST', token: a.token, body: { media: [{ path: up.replace('.png', '.mp4'), type: 'video', duration: 300 }] } })).status === 400);
const p1 = await api('/posts', { method: 'POST', token: a.token, body: { caption: 'Potro novo no haras!', media: [{ path: up, type: 'image', width: 1, height: 1 }] } });
check('cria publicação com foto (201)', p1.status === 201 && /^[a-f0-9]{24}$/.test(p1.data.id));
const p2 = await api('/posts', { method: 'POST', token: a.token, body: { caption: 'Segunda publicação' } });

const feed = await api(`/posts?author=${a.id}`, { token: b.token });
check('feed do autor: mais nova primeiro, com mídia e autor', feed.data.length === 2 && feed.data[0].id === p2.data.id && feed.data[1].media[0].path === up && feed.data[0].author_first_name === 'Autor');

check('curtir → true', (await api(`/posts/${p1.data.id}/like`, { method: 'POST', token: b.token })).data.liked === true);
let item = (await api(`/posts/${p1.data.id}`, { token: b.token })).data;
check('like_count 1 e liked_by_me', item.like_count === 1 && item.liked_by_me === true);
check('descurtir → false', (await api(`/posts/${p1.data.id}/like`, { method: 'POST', token: b.token })).data.liked === false && (await api(`/posts/${p1.data.id}`, { token: b.token })).data.like_count === 0);

const c1 = await api(`/posts/${p1.data.id}/comments`, { method: 'POST', token: b.token, body: { body: 'Que lindo!' } });
await api(`/posts/${p1.data.id}/comments`, { method: 'POST', token: a.token, body: { body: 'Obrigado!' } });
const cs = await api(`/posts/${p1.data.id}/comments`, { token: b.token });
check('comentários: mais antigos primeiro, com autor', cs.data.length === 2 && cs.data[0].body === 'Que lindo!' && cs.data[0].author_first_name === 'Leitor');
check('comment_count 2', (await api(`/posts/${p1.data.id}`, { token: a.token })).data.comment_count === 2);
check('comentário vazio → 400', (await api(`/posts/${p1.data.id}/comments`, { method: 'POST', token: b.token, body: { body: ' ' } })).status === 400);
check('autor da publicação apaga comentário de outro (204)', (await api(`/comments/${c1.data.id}`, { method: 'DELETE', token: a.token })).status === 204);

// paginação
const before = feed.data[0].created_at;
check('paginação com before', (await api(`/posts?author=${a.id}&before=${encodeURIComponent(before)}`, { token: b.token })).data.every((p) => p.id !== p2.data.id));

// 3 denúncias ocultam
for (const r of [r1, r2]) await api('/reports', { method: 'POST', token: r.token, body: { target_type: 'post', target_id: p2.data.id, reason: 'spam' } });
check('com 2 denúncias ainda aparece', (await api(`/posts/${p2.data.id}`, { token: b.token })).status === 200);
await api('/reports', { method: 'POST', token: r3.token, body: { target_type: 'post', target_id: p2.data.id, reason: 'spam' } });
check('3ª denúncia oculta para os outros (404)', (await api(`/posts/${p2.data.id}`, { token: b.token })).status === 404 && !(await api(`/posts?author=${a.id}`, { token: b.token })).data.some((p) => p.id === p2.data.id));
check('o autor ainda vê a sua', (await api(`/posts/${p2.data.id}`, { token: a.token })).status === 200);

// bloqueio
await api(`/blocks/${a.id}`, { method: 'PUT', token: b.token });
check('autor bloqueado some do feed', !(await api('/posts', { token: b.token })).data.some((p) => p.author_id === a.id));
await api(`/blocks/${a.id}`, { method: 'DELETE', token: b.token });

check('só o autor apaga (404 para outro)', (await api(`/posts/${p1.data.id}`, { method: 'DELETE', token: b.token })).status === 404);
check('autor apaga (204) com curtidas e comentários', (await api(`/posts/${p1.data.id}`, { method: 'DELETE', token: a.token })).status === 204 && (await api(`/posts/${p1.data.id}/comments`, { token: a.token })).status === 404);
done();
