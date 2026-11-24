import { api, B, check, done, user } from './harness.mjs';

const a = await user('Ana');
// PNG 1x1 válido
const png = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='), (c) => c.charCodeAt(0));
const form = (type, bytes = png, name = 'a.png') => { const f = new FormData(); f.append('file', new Blob([bytes], { type }), name); return f; };

const up = await api('/uploads?bucket=avatars', { method: 'POST', token: a.token, raw: form('image/png') });
check('envia foto de perfil (201) e devolve caminho relativo', up.status === 201 && /^avatars\/[a-f0-9]{24}\/[0-9a-f-]{36}\.png$/.test(up.data.path), up.data.path);

const file = await fetch(`${B}/files/${up.data.path}`);
const bytes = new Uint8Array(await file.arrayBuffer());
check('arquivo é público em /files (sem token) e idêntico', file.status === 200 && bytes.length === png.length && file.headers.get('content-type') === 'image/png');
check('com cache longo', (file.headers.get('cache-control') ?? '').includes('max-age=2592000'));

check('sem token não envia (401)', (await api('/uploads?bucket=media', { method: 'POST', raw: form('image/png') })).status === 401);
check('tipo não aceito (400)', (await api('/uploads?bucket=media', { method: 'POST', token: a.token, raw: form('application/pdf', png, 'x.pdf') })).status === 400);
check('vídeo como foto de perfil (400)', (await api('/uploads?bucket=avatars', { method: 'POST', token: a.token, raw: form('video/mp4', png, 'v.mp4') })).status === 400);
check('bucket inválido (400)', (await api('/uploads?bucket=etc', { method: 'POST', token: a.token, raw: form('image/png') })).status === 400);
const vid = await api('/uploads?bucket=media', { method: 'POST', token: a.token, raw: form('video/mp4', png, 'v.mp4') });
check('vídeo no feed é aceito (bucket media)', vid.status === 201 && vid.data.path.endsWith('.mp4'));
check('path traversal não vaza arquivos', (await fetch(`${B}/files/..%2F..%2Fpom.xml`)).status !== 200 && (await fetch(`${B}/files/../application.properties`)).status !== 200);

const me = await api('/me', { method: 'PATCH', token: a.token, body: { avatar_url: up.data.path } });
check('perfil guarda o caminho da foto', me.data.avatar_url === up.data.path);
check('arquivo inexistente → 404', (await fetch(`${B}/files/avatars/x/nao-existe.png`)).status === 404);
done();
