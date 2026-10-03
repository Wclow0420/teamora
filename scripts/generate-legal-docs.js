/**
 * Regenerates docs/legal/privacy.md + terms.md from src/content/legal.ts (the
 * single source of the in-app legal text), so the hosted copies never drift.
 *
 *   node scripts/generate-legal-docs.js
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const root = path.resolve(__dirname, '..');
const outDir = path.join(root, 'docs', 'legal');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'teamora-legal-'));

// legal.ts is plain data — transpile just that file and load it.
execFileSync(
  'npx',
  ['tsc', path.join('src', 'content', 'legal.ts'), '--outDir', tmp, '--module', 'commonjs', '--target', 'es2020'],
  { cwd: root, stdio: 'inherit' },
);
const L = require(path.join(tmp, 'legal.js'));

const bullet = (b) => (typeof b === 'string' ? `- ${b}` : `- **${b.term}** — ${b.text}`);

function toMarkdown(doc, related) {
  const lines = [`# ${doc.title}`, ''];
  if (L.LEGAL_DRAFT) {
    lines.push(`> **${L.LEGAL_DRAFT_NOTE}.** This text has not yet been reviewed by a lawyer. See the drafting notes at the end.`, '');
  }
  lines.push(`_Last updated: ${L.LEGAL_LAST_UPDATED}_`, '');
  doc.intro.forEach((p) => lines.push(p, ''));
  doc.sections.forEach((s) => {
    lines.push(`## ${s.heading}`, '');
    (s.body || []).forEach((p) => lines.push(p, ''));
    if (s.bullets) {
      s.bullets.forEach((b) => lines.push(bullet(b)));
      lines.push('');
    }
    (s.after || []).forEach((p) => lines.push(p, ''));
  });
  lines.push(`See also: [${related.label}](${related.file}).`, '');
  if (L.LEGAL_DRAFT) {
    lines.push('---', '', '## Drafting notes (remove before publishing)', '');
    L.LEGAL_DRAFTING_NOTES.forEach((n) => lines.push(`- ${n}`));
    lines.push('');
  }
  lines.push('<!-- Generated from src/content/legal.ts by scripts/generate-legal-docs.js — edit the text there, then rerun. -->', '');
  return lines.join('\n');
}

fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'privacy.md'), toMarkdown(L.PRIVACY_NOTICE, { label: 'Terms of use', file: 'terms.md' }));
fs.writeFileSync(path.join(outDir, 'terms.md'), toMarkdown(L.TERMS_OF_USE, { label: 'Privacy notice', file: 'privacy.md' }));
fs.rmSync(tmp, { recursive: true, force: true });
console.log('Wrote docs/legal/privacy.md and docs/legal/terms.md');
