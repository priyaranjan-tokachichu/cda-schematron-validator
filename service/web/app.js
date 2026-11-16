'use strict';
const $ = (id) => document.getElementById(id);
let profiles = [], latestReport = null, selectedFilter = 'all', busy = false, inputRevision = 0, loadRevision = 0, maxXmlBytes = 20 * 1024 * 1024;
const token = () => $('api-token').value.trim();
const headers = () => token() ? { Authorization: `Bearer ${token()}` } : {};
function invalidateReport() {
  inputRevision++; loadRevision++;
  latestReport = null; $('report').hidden = true; $('empty-results').hidden = false;
}
const size = (value) => new TextEncoder().encode(value).length;
function notice(message = '') { $('notice').textContent = message; $('notice').hidden = !message; }
function updateEditor() {
  const count = $('xml').value ? $('xml').value.split('\n').length : 0;
  $('document-size').textContent = `${count.toLocaleString()} lines · ${size($('xml').value).toLocaleString()} bytes`;
  $('line-numbers').textContent = Array.from({ length: Math.max(1, Math.min(count, 10000)) }, (_, i) => i + 1).join('\n');
  $('validate').disabled = busy || !profiles.length || !$('xml').value.trim();
}
function profileChanged() {
  const p = profiles.find((p) => p.id === $('profile').value);
  if (!p) return;
  $('profile-release').textContent = `Pinned release ${p.release} · CDA schema 20260205_SDTC`;
  $('limitations').replaceChildren(...p.limitations.map((text) => { const li = document.createElement('li'); li.textContent = text; return li; }));
}
async function connect() {
  try {
    const response = await fetch('/v1/profiles', { headers: headers() });
    if (!response.ok) throw new Error(response.status === 401 ? 'This service requires an API token. Enter it under Service authentication.' : 'Unable to load validation profiles.');
    const configuration = await response.json();
    profiles = configuration.profiles;
    maxXmlBytes = configuration.limits?.maxXmlBytes || 20 * 1024 * 1024;
    $('upload-limit').textContent = `UTF-8 XML · up to ${maxXmlBytes / 1024 / 1024} MiB`;
    invalidateReport();
    $('profile').replaceChildren(...profiles.map((p) => { const option = document.createElement('option'); option.value = p.id; option.textContent = p.name; option.selected = p.id === 'ccda-5.0'; return option; }));
    $('profile').disabled = false;
    $('connection').replaceChildren();
    $('connection').append(document.createElement('i'), document.createTextNode('Service ready'));
    $('connection').className = 'connection ready';
    notice(); profileChanged(); updateEditor();
  } catch (error) {
    profiles = []; $('validate').disabled = true; $('profile').disabled = true;
    $('connection').textContent = 'Service unavailable'; $('connection').className = 'connection failed'; notice(error.message);
  }
}
function element(tag, className, text) { const node = document.createElement(tag); if (className) node.className = className; if (text !== undefined) node.textContent = text; return node; }
function jumpToLine(line) {
  const input = $('xml'), lines = input.value.split('\n');
  const start = lines.slice(0, Math.max(0, line - 1)).reduce((sum, text) => sum + text.length + 1, 0);
  input.focus(); input.setSelectionRange(start, start + (lines[line - 1] || '').length);
  input.scrollTop = Math.max(0, (line - 5) * 19.8); $('line-numbers').scrollTop = input.scrollTop;
}
function renderFindings(container) {
  const items = latestReport.findings.filter((f) => selectedFilter === 'all' || f.severity === selectedFilter);
  container.replaceChildren();
  if (!items.length) { container.append(element('div', 'empty-results', 'No findings in this view.')); return; }
  for (const finding of items) {
    const card = element('article', 'finding');
    const top = element('div', 'finding-top'); top.append(element('span', `badge ${finding.severity}`, finding.severity), element('h4', '', finding.message));
    const meta = element('div', 'finding-meta'); meta.append(element('span', '', finding.stage.toUpperCase()));
    if (finding.line) { const button = element('button', 'location-button', `Line ${finding.line}${finding.column ? `:${finding.column}` : ''} ↗`); button.onclick = () => jumpToLine(finding.line); meta.append(button); }
    if (finding.ruleId) meta.append(element('span', '', finding.ruleId));
    card.append(top, meta);
    if (finding.xpath || finding.test) { const detail = element('details'); detail.append(element('summary', '', 'Rule and XML location')); if (finding.xpath) detail.append(element('code', '', finding.xpath)); if (finding.test) detail.append(element('code', '', finding.test)); card.append(detail); }
    container.append(card);
  }
}
function renderReport(report) {
  latestReport = report; selectedFilter = 'all'; $('empty-results').hidden = true; $('report').hidden = false; $('report').replaceChildren();
  const banner = element('div', `report-banner status-${report.status}`), intro = element('div');
  const title = report.status === 'invalid' ? 'Your document needs attention.' : report.status === 'valid' ? 'The configured checks passed.' : 'No errors found. Coverage is incomplete.';
  intro.append(element('h3', '', title), element('p', '', `${report.profile.name} · ${report.profile.release} · ${report.durationMs} ms`));
  const stats = element('div', 'stats');
  for (const [label, value, cls] of [['Errors', report.summary.errors, 'error'], ['Warnings', report.summary.warnings, 'warning'], ['Rules fired', report.summary.firedRules, '']]) { const stat = element('div', `stat ${cls}`); stat.append(element('strong', '', value.toLocaleString()), element('span', '', label)); stats.append(stat); }
  banner.append(intro, stats); $('report').append(banner);
  const stages = element('div', 'stage-list'); for (const stage of report.stages) stages.append(element('span', '', `${stage.status === 'completed' ? '✓' : '○'} ${stage.name.toUpperCase()} · ${stage.status}`)); $('report').append(stages);
  if (!report.validationComplete) { const alert = element('div', 'coverage-alert', report.truncated ? 'The finding limit was reached. This report is partial.' : 'Validation is incomplete. Review stage results and the selected profile’s coverage limitations; this report does not establish full conformance.'); $('report').append(alert); }
  const actions = element('div', 'report-actions'), list = element('div', 'findings');
  for (const [id, title] of [['all', 'All findings'], ['error', 'Errors'], ['warning', 'Warnings']]) { const button = element('button', `filter ${id === 'all' ? 'selected' : ''}`, title); button.setAttribute('aria-pressed', id === 'all'); button.onclick = () => { selectedFilter = id; for (const b of actions.querySelectorAll('.filter')) { b.classList.toggle('selected', b === button); b.setAttribute('aria-pressed', b === button); } renderFindings(list); }; actions.append(button); }
  const download = element('button', 'text-button', 'Download JSON ↓'); download.onclick = () => { const url = URL.createObjectURL(new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' })); const a = element('a'); a.href = url; a.download = 'validation-report.json'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }; actions.append(download); $('report').append(actions, list); renderFindings(list);
}
async function loadFile(file) {
  if (busy) return;
  if (!file) return;
  const revision = ++loadRevision;
  if (file.size > maxXmlBytes) { notice(`Choose an XML document at most ${maxXmlBytes.toLocaleString()} bytes (${maxXmlBytes / 1024 / 1024} MiB).`); return; }
  try { const xml = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer()); if (busy || revision !== loadRevision) return; $('xml').value = xml; $('file-name').textContent = file.name; invalidateReport(); notice(); updateEditor(); }
  catch { if (!busy && revision === loadRevision) notice('The file must be UTF-8 encoded XML.'); }
}
$('xml').addEventListener('input', () => { $('file-name').textContent = 'Pasted document'; invalidateReport(); updateEditor(); });
$('xml').addEventListener('scroll', () => { $('line-numbers').scrollTop = $('xml').scrollTop; });
$('file').addEventListener('change', (event) => { loadFile(event.target.files[0]); event.target.value = ''; });
$('profile').addEventListener('change', () => { profileChanged(); invalidateReport(); });
$('warnings').addEventListener('change', invalidateReport);
$('connect').addEventListener('click', connect);
$('dropzone').addEventListener('dragover', (event) => { event.preventDefault(); $('dropzone').classList.add('dragover'); });
$('dropzone').addEventListener('dragleave', () => $('dropzone').classList.remove('dragover'));
$('dropzone').addEventListener('drop', (event) => { event.preventDefault(); $('dropzone').classList.remove('dragover'); loadFile(event.dataTransfer.files[0]); });
$('clear').onclick = () => { invalidateReport(); $('xml').value = ''; $('file-name').textContent = 'Untitled document'; latestReport = null; $('report').hidden = true; $('empty-results').hidden = false; notice(); updateEditor(); };
$('load-example').onclick = async () => {
  if (busy) return;
  const revision = ++loadRevision;
  try {
    const response = await fetch('/sample.xml'); if (!response.ok) throw new Error();
    const xml = await response.text();
    if (busy || revision !== loadRevision) return;
    $('xml').value = xml; $('file-name').textContent = 'example-with-errors.xml'; invalidateReport();
    notice('This synthetic example intentionally contains errors to demonstrate the report.'); updateEditor();
  } catch { if (!busy && revision === loadRevision) notice('Could not load the example document.'); }
};
$('validate').onclick = async () => {
  if (busy) return;
  invalidateReport();
  const revision = inputRevision;
  const input = { xml: $('xml').value, profile: $('profile').value, includeWarnings: $('warnings').checked };
  const isCurrent = () => revision === inputRevision && input.xml === $('xml').value && input.profile === $('profile').value && input.includeWarnings === $('warnings').checked;
  busy = true; document.body.classList.add('busy'); $('results').setAttribute('aria-busy', 'true'); $('validate').firstElementChild.textContent = 'Checking your document…'; updateEditor(); notice();
  for (const id of ['xml', 'file', 'profile', 'warnings', 'clear', 'load-example']) $(id).disabled = true;
  try {
    const response = await fetch('/v1/validate', { method: 'POST', headers: { ...headers(), 'Content-Type': 'application/json' }, body: JSON.stringify(input) });
    const report = await response.json();
    if (!isCurrent()) return;
    if (report.findings && report.profile) { renderReport(report); if (!response.ok) notice(report.error || 'The engine could not finish every validation stage.'); }
    else throw new Error(report.error || 'Validation could not complete.');
  } catch (error) { if (isCurrent()) notice(error.message || 'The service could not be reached.'); }
  finally { busy = false; for (const id of ['xml', 'file', 'profile', 'warnings', 'clear', 'load-example']) $(id).disabled = false; document.body.classList.remove('busy'); $('results').setAttribute('aria-busy', 'false'); $('validate').firstElementChild.textContent = 'Validate document'; updateEditor(); }
};
$('api-link').onclick = () => $('api-dialog').showModal(); $('close-api').onclick = () => $('api-dialog').close();
connect(); updateEditor();
