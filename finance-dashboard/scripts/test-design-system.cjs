const assert = require('node:assert/strict');
const fs = require('node:fs');
const postcss = require('postcss');
const source = file => fs.readFileSync('src/' + file, 'utf8');
const canonical = postcss.parse(source('design-system.css'));
const tokens = new Map();
canonical.walkDecls(d => {if (d.prop.startsWith('--fs-')) tokens.set(d.prop, d.value);});
const styleFiles = ['styles.css', 'overview.css', 'design-system.css'];
let passed = 0;
const test = (name, run) => {run();passed++;console.log('OK ' + name);};

test('one UI font is inherited by prose, titles, inputs, buttons and SVG labels', () => {
    for (const file of styleFiles) postcss.parse(source(file)).walkDecls('font-family', d => {
        assert.ok(['inherit','var(--fs-font)','var(--fs-font-code)'].includes(d.value), file+': '+d.value);
    });
    const body = canonical.nodes.find(r => r.selector === 'body');
    assert.ok(body.nodes.some(d => d.prop === 'font-family' && d.value === 'var(--fs-font)'));
    assert.ok(body.nodes.some(d => d.prop === 'font-variant-numeric' && d.value === 'tabular-nums'));
    assert.ok(canonical.nodes.some(r => r.selector === 'button,input,select,textarea' && r.nodes.some(d => d.prop === 'font' && d.value === 'inherit')));
    assert.ok(!/Georgia|Times New Roman|font-family:\s*Inter/.test(styleFiles.map(source).join('\n')));
});

test('all screen colors and visual variables come from the canonical palette', () => {
    for (const file of styleFiles) postcss.parse(source(file)).walkDecls(d => {
        if (file !== 'design-system.css') assert.ok(!/#[0-9a-f]{3,8}\b|rgba?\(/i.test(d.value), file+': '+d.value);
        for (const match of d.value.matchAll(/var\((--[\w-]+)/g)) assert.ok(tokens.has(match[1]),file+': undefined '+match[1]);
    });
    for (const file of fs.readdirSync('src').filter(f => f.endsWith('.tsx'))) {
        const text = source(file);
        assert.ok(!/#[0-9a-f]{3,8}\b/i.test(text), file+': inline palette bypass');
        for (const match of text.matchAll(/var\((--[\w-]+)/g)) assert.ok(tokens.has(match[1]),file+': '+match[1]);
    }
});

test('actual normal-text color pairs meet 4.5:1 on their designated surfaces', () => {
    const lum = hex => {
        assert.match(hex,/^#[0-9a-f]{6}$/i);
        const c=hex.slice(1).match(/../g).map(x=>parseInt(x,16)/255).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);
        return c[0]*.2126+c[1]*.7152+c[2]*.0722;
    };
    const pairs = [];
    for (const bg of ['--fs-surface','--fs-canvas','--fs-surface-soft','--fs-surface-warm','--fs-surface-estimate']) {
        for (const fg of ['--fs-ink','--fs-muted']) pairs.push([fg,bg]);
    }
    pairs.push(['--fs-primary','--fs-surface'],['--fs-positive','--fs-primary-soft'],['--fs-negative','--fs-negative-soft'],
        ['--fs-warning','--fs-warning-soft'],['--fs-info','--fs-info-soft'],['--fs-on-primary','--fs-primary'],
        ['--fs-on-primary','--fs-primary-hover'],['--fs-on-primary','--fs-negative'],['--fs-on-brand','--fs-brand'],
        ['--fs-on-brand-muted','--fs-brand'],['--fs-on-brand-accent','--fs-brand'],['--fs-on-brand','--fs-brand-hover']);
    for (const [fg,bg] of pairs) {
        const a=lum(tokens.get(fg)), b=lum(tokens.get(bg)), ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
        assert.ok(ratio>=4.5,`${fg}/${bg}: ${ratio.toFixed(2)}`);
    }
    console.log(`  ${pairs.length} palette pairs checked; computed browser styles remain a separate check`);
});

test('every page and test fixture loads the same styles; overview and charts have no separate theme', () => {
    const imports = postcss.parse(source('styles.css')).nodes.filter(n=>n.type==='atrule'&&n.name==='import').map(n=>n.params);
    assert.deepEqual(imports,["'./design-system.css'","'./overview.css'"]);
    assert.ok(source('main.tsx').includes("import './styles.css'"));
    assert.ok(!source('FinancialOverview.tsx').includes("import './overview.css'"));
    for(const token of ['--fs-positive','--fs-negative','--fs-border','--fs-muted'])assert.ok(source('FinancialOverview.tsx').includes(token));
    const palette=new Set([...tokens.values()].filter(x=>/^#[0-9a-f]{6}$/i.test(x)));
    for(const hex of fs.readFileSync('public/favicon.svg','utf8').match(/#[0-9a-f]{6}/gi))assert.ok(palette.has(hex),hex);
});

test('keyboard focus, readable controls and reduced-motion rules cover every screen', () => {
    const button=canonical.nodes.find(r=>r.selector==='button');
    assert.ok(button.nodes.some(d=>d.prop==='min-height'&&d.value==='44px'));
    const secondary=canonical.nodes.find(r=>r.selector==='button.secondary');
    assert.ok(secondary.nodes.some(d=>d.prop==='background'&&d.value==='var(--fs-surface)'));
    assert.ok(secondary.nodes.some(d=>d.prop==='color'&&d.value==='var(--fs-primary)'));
    assert.ok(source('styles.css').includes('.credit-limit-editor button{border:1px solid var(--fs-primary)'));
    assert.ok(canonical.nodes.some(r=>r.selector?.includes('summary:focus-visible')&&r.nodes.some(d=>d.prop==='outline')));
    const reduced=canonical.nodes.find(r=>r.type==='atrule'&&r.params==='(prefers-reduced-motion:reduce)');
    assert.ok(reduced.nodes.some(r=>r.selector==='*,*::before,*::after'&&r.nodes.some(d=>d.prop==='animation'&&d.value==='none'&&d.important)));
});
console.log(passed+' design-system checks passed');
