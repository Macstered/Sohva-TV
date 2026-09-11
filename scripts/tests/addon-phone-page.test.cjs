// Dependency-free client regression tests. Run: node --test scripts/tests/addon-phone-page.test.cjs
// Execute the exact inline script shipped by the TV, with synthetic DOM/FileReader/network ports.
// Browser file-picker appearance and device networking remain separate acceptance checks.
const {readFileSync} = require('node:fs');
const {join} = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');

const source = readFileSync(join(__dirname, '../../addons/src/main/java/com/sohva/tv/addons/AddonPhoneSession.kt'), 'utf8');
const script = source.match(/private val SCRIPT = """([\s\S]*?)"""\.trimIndent\(\)/)[1];
const url = 'https://example.invalid/private-fixture/manifest.json';
function page({hash = '#synthetic-token', response = {ok: true, text: async () => 'Sent. Review and confirm on your TV.'}} = {}) {
    const nodes = Object.fromEntries(['form', 'textarea', '#file', '#send', '#clear', '#message'].map(key =>
        [key, {value: '', textContent: '', disabled: false, files: [], remove() { this.removed = true; }}]));
    const requests = [], readers = [], events = {}, histories = [];
    const context = vm.createContext({
        document: {querySelector: selector => nodes[selector]}, location: {hash},
        history: {replaceState: (...args) => histories.push(args)},
        window: {addEventListener: (name, callback) => { events[name] = callback; }},
        TextDecoder, TextEncoder,
        FileReader: class {
            constructor() { readers.push(this); }
            readAsArrayBuffer(file) { this.file = file; }
            abort() { this.aborted = true; }
        },
        fetch: async (...args) => { requests.push(args); if (response instanceof Error) throw response; return response; }
    });
    vm.runInContext(script, context);
    return {
        nodes, requests, readers, histories, events,
        select(bytes = Buffer.from(url), size = bytes.length) {
            nodes['#file'].files = [{size, bytes}]; nodes['#file'].value = 'synthetic.txt'; nodes['#file'].onchange();
        },
        finish(index = readers.length - 1) {
            const reader = readers[index]; reader.result = Uint8Array.from(reader.file.bytes).buffer; reader.onload();
        },
        paste(value) { nodes.textarea.value = value; nodes.textarea.oninput(); },
        submit() { return nodes.form.onsubmit({preventDefault() {}}); }
    };
}

test('file loading is local, strips UTF-8 BOM, preserves CRLF and waits for explicit send', async () => {
    const p = page();
    const text = url + '\r\n\r\nhttps://example.invalid/second/manifest.json\r\n';
    p.select(Buffer.from('\uFEFF' + text));
    assert.equal(p.nodes['#send'].disabled, true);
    await p.submit(); assert.equal(p.requests.length, 0);
    p.finish();
    assert.equal(p.nodes.textarea.value, text);
    assert.equal(p.nodes['#file'].value, '');
    assert.match(p.nodes['#message'].textContent, /2 addon URL/);
    assert.equal(p.requests.length, 0);
    await p.submit();
    assert.equal(p.requests.length, 1);
    const [path, options] = p.requests[0];
    assert.equal(path, '/submit'); assert.equal(options.body, text);
    assert.equal(options.headers['Content-Type'], 'text/plain');
    assert.equal(options.headers.Authorization, 'Bearer synthetic-token');
    assert.equal(p.nodes.textarea.value, '');
    assert.equal(p.nodes.form.removed, true);
    await p.submit(); assert.equal(p.requests.length, 1);
});

test('oversized and empty files are rejected before reading and clear stale input', () => {
    for (const size of [0, 262145]) {
        const p = page(); p.paste(url); p.select(Buffer.from(url), size);
        assert.equal(p.readers.length, 0); assert.equal(p.requests.length, 0);
        assert.equal(p.nodes.textarea.value, ''); assert.equal(p.nodes['#send'].disabled, true);
        assert.match(p.nodes['#message'].textContent, /non-empty UTF-8/);
    }
});

test('invalid UTF-8, binary, blank and over-32-line files fail locally; a valid replacement works', () => {
    for (const bytes of [Buffer.from([0xc3, 0x28]), Buffer.from('a\0b'), Buffer.from(' \r\n'), Buffer.from(Array(33).fill(url).join('\n'))]) {
        const p = page(); p.select(bytes); p.finish();
        assert.equal(p.nodes.textarea.value, ''); assert.equal(p.nodes['#send'].disabled, true);
        assert.equal(p.requests.length, 0);
        p.select(); p.finish(); assert.equal(p.nodes.textarea.value, url);
        assert.equal(p.nodes['#send'].disabled, false);
    }
});

test('32-entry and exact 256-KiB files pass; paste limits count UTF-8 bytes rather than characters', async () => {
    for (const text of [Array(32).fill(url).join('\n'), 'x'.repeat(262144)]) {
        const p = page(); p.select(Buffer.from(text)); p.finish();
        assert.equal(p.nodes.textarea.value, text); assert.equal(p.nodes['#send'].disabled, false);
    }
    const p = page(); p.paste('ä'.repeat(131073)); await p.submit();
    assert.equal(p.requests.length, 0); assert.match(p.nodes['#message'].textContent, /256 KiB/);
});

test('reselecting or clearing during a read cannot restore an old credential list', () => {
    const p = page(); p.select(); p.select(Buffer.from('https://example.invalid/new/manifest.json'));
    assert.equal(p.readers[0].aborted, true);
    p.finish(0); assert.equal(p.nodes.textarea.value, ''); assert.equal(p.nodes['#send'].disabled, true);
    p.finish(1); assert.match(p.nodes.textarea.value, /\/new\//);
    p.select(); p.nodes['#clear'].onclick(); p.finish(2);
    assert.equal(p.nodes.textarea.value, ''); assert.equal(p.nodes['#send'].disabled, true);
    assert.equal(p.requests.length, 0);
});

test('read errors and cancelling the picker do not send data', () => {
    const p = page(); p.select(); p.readers[0].onerror();
    assert.match(p.nodes['#message'].textContent, /Could not read/);
    assert.equal(p.nodes['#send'].disabled, true);
    p.paste(url); p.nodes['#file'].files = []; p.nodes['#file'].onchange();
    assert.equal(p.nodes.textarea.value, url); assert.equal(p.requests.length, 0);
});

test('paste still works, repeated send is blocked, and server rejection allows another selection', async () => {
    let release;
    const response = {ok: false, text: () => new Promise(resolve => { release = resolve; })};
    const p = page({response}); p.paste(url);
    const sending = p.submit(); await Promise.resolve();
    assert.equal(p.nodes['#file'].disabled, true); assert.equal(p.nodes['#clear'].disabled, true);
    await p.submit(); assert.equal(p.requests.length, 1);
    release('Invalid list'); await sending;
    assert.equal(p.nodes.form.removed, undefined); assert.equal(p.nodes['#file'].disabled, false);
    assert.equal(p.nodes.textarea.value, '');
    p.select(); p.finish(); assert.equal(p.nodes['#send'].disabled, false);
});

test('network errors are generic and do not expose file content', async () => {
    const p = page({response: new Error(url)}); p.paste(url); await p.submit();
    assert.match(p.nodes['#message'].textContent, /Connection closed/);
    assert.equal(p.nodes['#message'].textContent.includes(url), false);
    assert.equal(p.nodes.textarea.value, '');
});

test('token leaves address history, and navigation clears credentials and stops delayed reads', async () => {
    const p = page(); assert.deepEqual(p.histories, [[null, '', '/']]);
    p.select(); p.events.pagehide(); p.finish();
    assert.equal(p.nodes.textarea.value, ''); assert.equal(p.nodes['#file'].value, '');
    assert.equal(p.nodes['#file'].disabled, true); await p.submit(); assert.equal(p.requests.length, 0);
    const missing = page({hash: ''}); missing.paste(url); await missing.submit();
    assert.equal(missing.nodes['#send'].disabled, true); assert.equal(missing.requests.length, 0);
});
