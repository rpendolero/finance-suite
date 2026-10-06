// Deterministic DOM-state checks; not a substitute for a live-bank/browser test.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(process.argv[2], 'utf8');
const arm = source.match(/String ARM =\s*"""([\s\S]*?)""";/)[1].replaceAll('\\\\', '\\');
function fixture() {
  const prefix = 'panelCargando:connection-';
  const nodes = Object.fromEntries(['working','idle','trouble','lost'].map(name =>
    [prefix + name, {id: prefix + name, visibility: name === 'idle' ? 'visible' : 'hidden'}]));
  let callback;
  const window = {};
  const context = vm.createContext({window,
    document: {documentElement: {}, getElementById: id => nodes[id]},
    getComputedStyle: node => ({visibility: node.visibility}),
    MutationObserver: class {constructor(cb) {callback=cb;} observe(){} disconnect(){}}
  });
  vm.runInContext('(' + arm + ')()', context);
  return {nodes, state: window.__financeKutxaWait, update: records => callback(records), prefix};
}
let f = fixture();
f.update([]);
assert.equal(f.state.completed, false, 'Initial idle must not imply action completed');
f.nodes[f.prefix+'working'].visibility = 'visible';
f.nodes[f.prefix+'idle'].visibility = 'hidden';
f.update([]);
assert.equal(f.state.started, true);
assert.equal(f.state.completed, false);
f.nodes[f.prefix+'working'].visibility = 'hidden';
f.nodes[f.prefix+'idle'].visibility = 'visible';
f.update([]);
assert.equal(f.state.completed, true);
f = fixture();
f.update([{target:f.nodes[f.prefix+'working'],attributeName:'style',oldValue:'visibility: visible;'}]);
assert.equal(f.state.completed, true, 'Batched rapid busy/idle mutations must be detected');
f = fixture();
f.nodes[f.prefix+'working'].visibility = 'visible'; f.update([]);
f.nodes[f.prefix+'working'] = {id:f.prefix+'working',visibility:'hidden'};
f.update([]);
assert.equal(f.state.completed, true, 'Replaced status nodes must be read from the current DOM');
f.nodes[f.prefix+'lost'].visibility = 'visible';f.update([]);
assert.equal(f.state.failed, true);
assert.equal(f.state.completed, false);
console.log('ICEFaces DOM-state checks passed (5 scenarios)');
