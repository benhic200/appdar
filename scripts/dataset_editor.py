#!/usr/bin/env python3
"""
Appdar InitialDataset Editor
Serves a web UI on 0.0.0.0:9003 for browsing and editing InitialDataset.kt

Usage:
  /Host_Machine/usr/bin/python3 .../scripts/dataset_editor.py
  Then open http://localhost:9003
"""

import json
import os
import re
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse, urlencode
from urllib.request import urlopen, Request
from urllib.error import HTTPError, URLError

DATASET_PATH = os.path.join(
    os.path.dirname(__file__),
    "../data/src/main/kotlin/com/benhic/appdar/data/local/InitialDataset.kt"
)
DATASET_PATH = os.path.realpath(DATASET_PATH)

# In-memory cache: packageName -> True/False/None (None = unchecked)
_store_cache: dict = {}
_store_cache_lock = threading.Lock()

# ── Parser ─────────────────────────────────────────────────────────────────────

def parse_entries():
    """Parse all createMapping(...) calls from the Kotlin file."""
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()

    entries = []
    pattern = re.compile(
        r'createMapping\(\s*"([^"]+)"\s*,'   # businessName
        r'\s*"([^"]+)"\s*,'                  # packageName
        r'\s*"([^"]+)"\s*,'                  # appName
        r'\s*"([^"]+)"'                       # category
        r'(.*?)\),?'                          # optional named args, closing paren, optional comma
        r'(?:\s*//\s*(.*))?$',               # optional inline comment
        re.DOTALL
    )

    for lineno, line in enumerate(lines, 1):
        m = pattern.search(line)
        if not m:
            continue
        business_name, package_name, app_name, category, extras, comment = m.groups()
        extras = extras or ""
        comment = (comment or "").strip()

        is_enabled = True
        if re.search(r'isEnabled\s*=\s*false', extras):
            is_enabled = False

        region_hint = None
        rh_match = re.search(r'regionHint\s*=\s*"([^"]+)"', extras)
        if rh_match:
            region_hint = rh_match.group(1)

        needs_verify = "verify" in comment.lower()

        entries.append({
            "lineno": lineno,
            "businessName": business_name,
            "packageName": package_name,
            "appName": app_name,
            "category": category,
            "isEnabled": is_enabled,
            "regionHint": region_hint,
            "comment": comment,
            "needsVerify": needs_verify,
        })

    return entries


def group_by_region(entries):
    """Group entries by regionHint, with a synthetic 'Global' group for null."""
    order = ["UK", "US", "AU", "NZ", "Global"]
    groups = {r: [] for r in order}
    for e in entries:
        # For comma-separated regionHints like "US,NZ", key off the first region
        raw = e["regionHint"] or "Global"
        key = raw.split(",")[0] if raw != "Global" else "Global"
        if key not in groups:
            groups[key] = []
        groups[key].append(e)
    result = []
    seen = set()
    for r in order:
        if groups.get(r):
            result.append((r, groups[r]))
            seen.add(r)
    for r, items in groups.items():
        if r not in seen and items:
            result.append((r, items))
    return result


def check_play_store(pkg: str):
    """Return True (found), False (not found / 404), or None (network error)."""
    with _store_cache_lock:
        if pkg in _store_cache:
            return _store_cache[pkg]

    url = f"https://play.google.com/store/apps/details?id={pkg}&hl=en&gl=US"
    req = Request(url)
    req.add_header("User-Agent",
        "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
    req.add_header("Accept-Language", "en-US,en;q=0.9")
    try:
        resp = urlopen(req, timeout=12)
        resp.read(1)   # read minimal data so the connection closes cleanly
        resp.close()
        result = True
    except HTTPError as e:
        result = False if e.code == 404 else None
    except Exception:
        result = None

    with _store_cache_lock:
        _store_cache[pkg] = result
    return result


# ── Writer ─────────────────────────────────────────────────────────────────────

def build_line(entry, indent="        "):
    """Reconstruct a createMapping(...) line from an entry dict."""
    bn = entry["businessName"]
    pkg = entry["packageName"]
    an = entry["appName"]
    cat = entry["category"]
    enabled = entry.get("isEnabled", True)
    region = entry.get("regionHint")
    comment = entry.get("comment", "")

    extras = []
    if not enabled:
        extras.append("isEnabled = false")
    if region:
        extras.append(f'regionHint = "{region}"')

    args = f'"{bn}", "{pkg}", "{an}", "{cat}"'
    if extras:
        args += ", " + ", ".join(extras)

    line = f'{indent}createMapping({args})'
    if comment:
        line += f'  // {comment}'
    return line


def update_entry(original_pkg, updated_entry):
    """Replace the createMapping line for original_pkg in the Kotlin file."""
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()

    pkg_pattern = re.compile(r'createMapping\([^)]*"' + re.escape(original_pkg) + r'"')
    new_lines = []
    replaced = False
    for line in lines:
        if not replaced and pkg_pattern.search(line):
            indent_match = re.match(r'^(\s*)', line)
            indent = indent_match.group(1) if indent_match else "        "
            # Check for trailing comma after closing paren (before any inline comment)
            has_comma = bool(re.search(r'\),\s*(?://.*)?$', line.rstrip()))
            new_line = build_line(updated_entry, indent=indent)
            if has_comma:
                new_line += ","
            new_line += "\n"
            new_lines.append(new_line)
            replaced = True
        else:
            new_lines.append(line)

    if not replaced:
        return False, f"Package '{original_pkg}' not found in file"

    with open(DATASET_PATH, "w", encoding="utf-8") as f:
        f.writelines(new_lines)

    # Invalidate store cache for new package (may have changed)
    new_pkg = updated_entry.get("packageName", original_pkg)
    with _store_cache_lock:
        _store_cache.pop(original_pkg, None)
        _store_cache.pop(new_pkg, None)

    return True, "OK"


def add_entry(new_entry):
    """Append a new createMapping line to the appropriate region section."""
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()

    region = new_entry.get("regionHint")
    if region:
        header_pattern = re.compile(r'//\s*──\s*' + re.escape(region.split(",")[0]), re.IGNORECASE)
    else:
        header_pattern = re.compile(r'//\s*──\s*Global', re.IGNORECASE)

    insert_after = -1
    in_section = False
    for i, line in enumerate(lines):
        if header_pattern.search(line):
            in_section = True
        if in_section and re.search(r'createMapping\(', line):
            insert_after = i

    if insert_after == -1:
        for i, line in reversed(list(enumerate(lines))):
            if re.search(r'createMapping\(', line):
                insert_after = i
                break

    if insert_after == -1:
        return False, "Could not find insertion point"

    prev = lines[insert_after]
    prev_stripped = prev.rstrip()
    if not re.search(r'\),\s*(?://.*)?$', prev_stripped):
        # No trailing comma — add one before any inline comment
        comment_match = re.search(r'(\)\s*)(//.*)?$', prev_stripped)
        if comment_match and comment_match.group(2):
            lines[insert_after] = prev_stripped[:comment_match.start(1)] + ")," + prev_stripped[comment_match.start(1)+1:] + "\n"
        else:
            lines[insert_after] = prev_stripped + ",\n"

    new_line = build_line(new_entry, indent="        ") + "\n"
    lines.insert(insert_after + 1, new_line)

    with open(DATASET_PATH, "w", encoding="utf-8") as f:
        f.writelines(lines)
    return True, "OK"


def delete_entry(package_name):
    """Remove the createMapping line for the given packageName."""
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()

    pkg_pattern = re.compile(r'createMapping\([^)]*"' + re.escape(package_name) + r'"')
    new_lines = []
    deleted = False
    for line in lines:
        if not deleted and pkg_pattern.search(line):
            deleted = True
        else:
            new_lines.append(line)

    if not deleted:
        return False, "Package not found"

    with open(DATASET_PATH, "w", encoding="utf-8") as f:
        f.writelines(new_lines)

    with _store_cache_lock:
        _store_cache.pop(package_name, None)

    return True, "OK"


# ── HTML ───────────────────────────────────────────────────────────────────────

def render_html(grouped):
    # Collect all packages for the JS store-check initialiser
    all_packages = []
    region_tabs = ""
    region_panels = ""

    for idx, (region, entries) in enumerate(grouped):
        active = "active" if idx == 0 else ""
        region_tabs += (
            f'<button class="tab-btn {active}" onclick="showRegion(\'{region}\')" '
            f'id="tab-{region}">{region} <span class="count">{len(entries)}</span></button>\n'
        )

        rows = ""
        for e in entries:
            all_packages.append(e["packageName"])
            verify_badge = '<span class="badge verify">verify</span>' if e["needsVerify"] else ""
            enabled_badge = '' if e["isEnabled"] else '<span class="badge disabled">disabled</span>'
            region_badge = (
                f'<span class="badge region">{e["regionHint"]}</span>'
                if e["regionHint"] else
                '<span class="badge global">global</span>'
            )
            play_url = f'https://play.google.com/store/apps/details?id={e["packageName"]}'
            entry_json = json.dumps(e).replace("'", "&#39;").replace('"', '&quot;')
            pkg_safe = e["packageName"].replace(".", "_").replace("-", "_")
            rows += f"""
            <tr class="{'disabled-row' if not e['isEnabled'] else ''}{'verify-row' if e['needsVerify'] else ''}">
              <td><strong>{e['businessName']}</strong>{enabled_badge}{verify_badge}</td>
              <td class="pkg">{e['packageName']}</td>
              <td>{e['appName']}</td>
              <td>{e['category']}</td>
              <td>{region_badge}</td>
              <td class="store-cell" id="store-{pkg_safe}"><span class="store-dot dot-checking" title="Checking Play Store..."></span></td>
              <td class="actions">
                <a href="{play_url}" target="_blank" class="btn btn-play" title="Open in Play Store">&#9654; Play</a>
                <button class="btn btn-edit" onclick='openEdit({entry_json})'>Edit</button>
                <button class="btn btn-delete" onclick="confirmDelete('{e['packageName']}')">&#x2715;</button>
              </td>
            </tr>"""

        show = "block" if idx == 0 else "none"
        region_panels += f"""
        <div id="panel-{region}" class="region-panel" style="display:{show}">
          <div class="panel-header">
            <h2>{region}</h2>
            <button class="btn btn-add" onclick="openAdd('{region}')">+ Add Entry</button>
          </div>
          <div class="search-bar">
            <input type="text" placeholder="Filter {region}..." oninput="filterTable(this, 'table-{region}')" class="search-input">
          </div>
          <table id="table-{region}">
            <thead><tr>
              <th>Business</th><th>Package</th><th>App Name</th><th>Category</th><th>Region</th>
              <th title="Play Store availability">Store</th><th>Actions</th>
            </tr></thead>
            <tbody>{rows}</tbody>
          </table>
        </div>"""

    packages_js = json.dumps(all_packages)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Appdar Dataset Editor</title>
<style>
  :root {{
    --bg: #0d1117; --surface: #161b22; --surface2: #21262d;
    --border: #30363d; --text: #e6edf3; --muted: #8b949e;
    --primary: #58a6ff; --green: #3fb950; --red: #f85149;
    --orange: #d29922; --purple: #bc8cff;
  }}
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{ background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 14px; }}
  header {{ background: var(--surface); border-bottom: 1px solid var(--border); padding: 16px 24px; display: flex; align-items: center; gap: 16px; }}
  header h1 {{ font-size: 18px; font-weight: 600; color: var(--primary); }}
  header .subtitle {{ color: var(--muted); font-size: 12px; margin-top: 2px; }}
  .tabs {{ display: flex; gap: 4px; padding: 12px 24px 0; background: var(--surface); border-bottom: 1px solid var(--border); flex-wrap: wrap; }}
  .tab-btn {{ background: none; border: none; color: var(--muted); padding: 8px 16px; cursor: pointer; border-bottom: 2px solid transparent; font-size: 13px; font-weight: 500; transition: all .15s; }}
  .tab-btn:hover {{ color: var(--text); }}
  .tab-btn.active {{ color: var(--primary); border-bottom-color: var(--primary); }}
  .tab-btn .count {{ background: var(--surface2); border-radius: 10px; padding: 1px 7px; font-size: 11px; margin-left: 4px; color: var(--muted); }}
  .main {{ padding: 20px 24px; }}
  .panel-header {{ display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }}
  .panel-header h2 {{ font-size: 16px; font-weight: 600; }}
  .search-bar {{ margin-bottom: 10px; }}
  .search-input {{ background: var(--surface); border: 1px solid var(--border); color: var(--text); padding: 7px 12px; border-radius: 6px; width: 300px; font-size: 13px; }}
  .search-input:focus {{ outline: none; border-color: var(--primary); }}
  table {{ width: 100%; border-collapse: collapse; background: var(--surface); border-radius: 8px; overflow: hidden; border: 1px solid var(--border); }}
  thead tr {{ background: var(--surface2); }}
  th {{ padding: 10px 12px; text-align: left; font-weight: 600; color: var(--muted); font-size: 12px; text-transform: uppercase; letter-spacing: .5px; }}
  td {{ padding: 10px 12px; border-top: 1px solid var(--border); vertical-align: middle; }}
  tr:hover td {{ background: rgba(88,166,255,.04); }}
  .disabled-row td {{ opacity: .6; }}
  .verify-row td:first-child {{ border-left: 2px solid var(--orange); }}
  .pkg {{ font-family: monospace; font-size: 12px; color: var(--muted); max-width: 260px; word-break: break-all; }}
  .store-cell {{ text-align: center; width: 48px; }}
  .store-dot {{
    display: inline-block; width: 12px; height: 12px; border-radius: 50%;
    vertical-align: middle; cursor: default;
  }}
  .dot-checking {{ background: var(--surface2); border: 2px solid var(--border); animation: pulse 1.4s ease-in-out infinite; }}
  .dot-found    {{ background: var(--green); box-shadow: 0 0 4px var(--green); }}
  .dot-missing  {{ background: var(--red);   box-shadow: 0 0 4px var(--red); }}
  .dot-error    {{ background: var(--orange); }}
  @keyframes pulse {{ 0%,100%{{opacity:.4}} 50%{{opacity:1}} }}
  .actions {{ white-space: nowrap; }}
  .btn {{ border: none; border-radius: 5px; cursor: pointer; font-size: 12px; padding: 5px 10px; font-weight: 500; transition: opacity .15s; }}
  .btn:hover {{ opacity: .8; }}
  .btn-play {{ background: var(--green); color: #000; text-decoration: none; display: inline-block; }}
  .btn-edit {{ background: var(--primary); color: #000; margin: 0 4px; }}
  .btn-delete {{ background: var(--red); color: #fff; }}
  .btn-add {{ background: var(--primary); color: #000; padding: 8px 16px; font-size: 13px; }}
  .badge {{ font-size: 10px; font-weight: 600; padding: 2px 6px; border-radius: 4px; margin-left: 5px; vertical-align: middle; }}
  .badge.disabled {{ background: #3a2a1a; color: var(--orange); }}
  .badge.verify {{ background: #2d2800; color: #d29922; border: 1px solid #d29922; }}
  .badge.region {{ background: var(--surface2); color: var(--primary); }}
  .badge.global {{ background: #1a2a3a; color: #79c0ff; }}
  /* Modal */
  .modal-overlay {{ display: none; position: fixed; inset: 0; background: rgba(0,0,0,.7); z-index: 100; align-items: center; justify-content: center; }}
  .modal-overlay.open {{ display: flex; }}
  .modal {{ background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 24px; width: 520px; max-width: 95vw; max-height: 90vh; overflow-y: auto; }}
  .modal h3 {{ font-size: 16px; margin-bottom: 18px; color: var(--primary); }}
  .form-row {{ margin-bottom: 14px; }}
  .form-row label {{ display: block; font-size: 12px; color: var(--muted); margin-bottom: 4px; font-weight: 500; }}
  .form-row input, .form-row select {{ width: 100%; background: var(--surface2); border: 1px solid var(--border); color: var(--text); padding: 8px 10px; border-radius: 6px; font-size: 13px; }}
  .form-row input:focus, .form-row select:focus {{ outline: none; border-color: var(--primary); }}
  .form-row .hint {{ font-size: 11px; color: var(--muted); margin-top: 3px; }}
  .checkbox-row {{ display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }}
  .checkbox-row input {{ width: auto; }}
  .modal-actions {{ display: flex; gap: 8px; justify-content: flex-end; margin-top: 20px; }}
  .btn-cancel {{ background: var(--surface2); color: var(--text); padding: 8px 16px; }}
  .btn-save {{ background: var(--green); color: #000; padding: 8px 20px; font-size: 13px; font-weight: 600; }}
  .toast {{ position: fixed; bottom: 24px; right: 24px; background: var(--surface2); border: 1px solid var(--border); border-radius: 8px; padding: 12px 20px; font-size: 13px; z-index: 200; display: none; max-width: 360px; }}
  .toast.show {{ display: block; animation: fadeInOut 3s forwards; }}
  .toast.error {{ border-color: var(--red); color: var(--red); }}
  @keyframes fadeInOut {{ 0%{{opacity:0;transform:translateY(10px)}} 10%{{opacity:1;transform:none}} 75%{{opacity:1}} 100%{{opacity:0}} }}
</style>
</head>
<body>
<header>
  <div>
    <h1>Appdar Dataset Editor</h1>
    <div class="subtitle">{DATASET_PATH}</div>
  </div>
</header>

<div class="tabs">
{region_tabs}
</div>

<div class="main">
{region_panels}
</div>

<!-- Edit/Add Modal -->
<div class="modal-overlay" id="modal-overlay" onclick="closeModal(event)">
  <div class="modal" onclick="event.stopPropagation()">
    <h3 id="modal-title">Edit Entry</h3>
    <input type="hidden" id="field-original-pkg">
    <input type="hidden" id="modal-mode" value="edit">
    <div class="form-row">
      <label>Business Name</label>
      <input type="text" id="field-businessName">
    </div>
    <div class="form-row">
      <label>Package Name</label>
      <input type="text" id="field-packageName" placeholder="com.example.app">
      <div class="hint">Unique key — Play Store ID</div>
    </div>
    <div class="form-row">
      <label>App Name</label>
      <input type="text" id="field-appName">
    </div>
    <div class="form-row">
      <label>Category</label>
      <input type="text" id="field-category" placeholder="fast_food, supermarket, coffee...">
    </div>
    <div class="form-row">
      <label>Region Hint</label>
      <input type="text" id="field-regionHint" placeholder='UK, US, AU, NZ, US,NZ, or leave blank for global'>
      <div class="hint">Comma-separated for apps shared across regions (e.g. US,NZ)</div>
    </div>
    <div class="checkbox-row">
      <input type="checkbox" id="field-isEnabled">
      <label for="field-isEnabled">Enabled (shown to users in this region)</label>
    </div>
    <div class="form-row">
      <label>Comment (optional)</label>
      <input type="text" id="field-comment" placeholder="verify package, etc.">
    </div>
    <div class="modal-actions">
      <button class="btn btn-cancel" onclick="closeModal()">Cancel</button>
      <button class="btn btn-save" id="btn-save" onclick="saveEntry()">Save</button>
    </div>
  </div>
</div>

<div class="toast" id="toast"></div>

<script>
const ALL_PACKAGES = {packages_js};

function pkgToId(pkg) {{
  return 'store-' + pkg.replace(/\\./g, '_').replace(/-/g, '_');
}}

// ── Region tabs ───────────────────────────────────────────────────────────────
function showRegion(region) {{
  document.querySelectorAll('.region-panel').forEach(p => p.style.display = 'none');
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('panel-' + region).style.display = 'block';
  document.getElementById('tab-' + region).classList.add('active');
}}

function filterTable(input, tableId) {{
  const q = input.value.toLowerCase();
  document.querySelectorAll('#' + tableId + ' tbody tr').forEach(row => {{
    row.style.display = row.textContent.toLowerCase().includes(q) ? '' : 'none';
  }});
}}

// ── Modal ─────────────────────────────────────────────────────────────────────
function openEdit(entry) {{
  document.getElementById('modal-title').textContent = 'Edit Entry';
  document.getElementById('modal-mode').value = 'edit';
  document.getElementById('field-original-pkg').value = entry.packageName;
  document.getElementById('field-businessName').value = entry.businessName;
  document.getElementById('field-packageName').value = entry.packageName;
  document.getElementById('field-appName').value = entry.appName;
  document.getElementById('field-category').value = entry.category;
  document.getElementById('field-regionHint').value = entry.regionHint || '';
  document.getElementById('field-isEnabled').checked = entry.isEnabled;
  document.getElementById('field-comment').value = entry.comment || '';
  document.getElementById('btn-save').disabled = false;
  document.getElementById('btn-save').textContent = 'Save';
  document.getElementById('modal-overlay').classList.add('open');
}}

function openAdd(region) {{
  document.getElementById('modal-title').textContent = 'Add New Entry';
  document.getElementById('modal-mode').value = 'add';
  document.getElementById('field-original-pkg').value = '';
  document.getElementById('field-businessName').value = '';
  document.getElementById('field-packageName').value = '';
  document.getElementById('field-appName').value = '';
  document.getElementById('field-category').value = '';
  document.getElementById('field-regionHint').value = region === 'Global' ? '' : region;
  document.getElementById('field-isEnabled').checked = region === 'UK' || region === 'Global';
  document.getElementById('field-comment').value = '';
  document.getElementById('btn-save').disabled = false;
  document.getElementById('btn-save').textContent = 'Save';
  document.getElementById('modal-overlay').classList.add('open');
}}

function closeModal(e) {{
  if (!e || e.target === document.getElementById('modal-overlay'))
    document.getElementById('modal-overlay').classList.remove('open');
}}

function saveEntry() {{
  const mode = document.getElementById('modal-mode').value;
  const originalPkg = document.getElementById('field-original-pkg').value;
  const entry = {{
    originalPackageName: originalPkg,
    businessName: document.getElementById('field-businessName').value.trim(),
    packageName:  document.getElementById('field-packageName').value.trim(),
    appName:      document.getElementById('field-appName').value.trim(),
    category:     document.getElementById('field-category').value.trim(),
    regionHint:   document.getElementById('field-regionHint').value.trim() || null,
    isEnabled:    document.getElementById('field-isEnabled').checked,
    comment:      document.getElementById('field-comment').value.trim(),
  }};
  if (!entry.businessName || !entry.packageName || !entry.appName || !entry.category) {{
    showToast('Please fill in all required fields', true);
    return;
  }}
  const btn = document.getElementById('btn-save');
  btn.disabled = true;
  btn.textContent = 'Saving…';

  const url = mode === 'add' ? '/api/add' : '/api/edit';
  fetch(url, {{
    method: 'POST',
    headers: {{'Content-Type': 'application/json'}},
    body: JSON.stringify(entry)
  }})
  .then(r => {{
    if (!r.ok) throw new Error('Server error ' + r.status);
    return r.json();
  }})
  .then(data => {{
    if (data.ok) {{
      showToast(mode === 'add' ? 'Entry added' : 'Entry saved');
      document.getElementById('modal-overlay').classList.remove('open');
      setTimeout(() => location.reload(), 900);
    }} else {{
      showToast('Error: ' + (data.error || 'unknown'), true);
      btn.disabled = false;
      btn.textContent = 'Save';
    }}
  }})
  .catch(err => {{
    showToast('Request failed: ' + err.message, true);
    btn.disabled = false;
    btn.textContent = 'Save';
  }});
}}

function confirmDelete(pkg) {{
  if (!confirm('Delete entry for package:\\n' + pkg + '?')) return;
  fetch('/api/delete', {{
    method: 'POST',
    headers: {{'Content-Type': 'application/json'}},
    body: JSON.stringify({{packageName: pkg}})
  }})
  .then(r => r.json())
  .then(data => {{
    if (data.ok) {{ showToast('Deleted'); setTimeout(() => location.reload(), 700); }}
    else showToast('Error: ' + data.error, true);
  }})
  .catch(err => showToast('Request failed: ' + err.message, true));
}}

function showToast(msg, isError) {{
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.className = 'toast show' + (isError ? ' error' : '');
  setTimeout(() => t.className = 'toast', 3100);
}}

// ── Play Store availability checks (server-side) ──────────────────────────────
// The Python server fetches play.google.com directly (no CORS issue).
// Browser calls /api/check_package?id=PKG and reads the JSON result.

function updateStoreDot(pkg, state) {{
  const cell = document.getElementById(pkgToId(pkg));
  if (!cell) return;
  const dot = cell.querySelector('.store-dot');
  if (!dot) return;
  if (state === true) {{
    dot.className = 'store-dot dot-found';
    dot.title = 'Found on Play Store ✓';
  }} else if (state === false) {{
    dot.className = 'store-dot dot-missing';
    dot.title = 'NOT found on Play Store — check package name';
  }} else {{
    dot.className = 'store-dot dot-error';
    dot.title = 'Could not check — click Play to verify manually';
  }}
}}

function checkPackageBrowser(pkg) {{
  return fetch('/api/check_package?id=' + encodeURIComponent(pkg),
               {{ signal: AbortSignal.timeout(20000) }})
    .then(r => r.json())
    .then(d => d.found)
    .catch(() => null);
}}

function checkPackages(packages) {{
  let i = 0;
  const concurrency = 3; // server fetches play.google.com — keep polite
  function next() {{
    if (i >= packages.length) return;
    const pkg = packages[i++];
    checkPackageBrowser(pkg)
      .then(found => {{ updateStoreDot(pkg, found); next(); }})
      .catch(() => {{ updateStoreDot(pkg, null); next(); }});
  }}
  for (let c = 0; c < concurrency; c++) next();
}}

// Start checks on page load
window.addEventListener('DOMContentLoaded', () => {{
  checkPackages(ALL_PACKAGES);
}});
</script>
</body>
</html>"""


# ── HTTP Handler ───────────────────────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        print(f"  {self.address_string()} {fmt % args}")

    def send_json(self, data, status=200):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", len(body))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path in ("/", ""):
            try:
                entries = parse_entries()
                grouped = group_by_region(entries)
                html = render_html(grouped)
                body = html.encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", len(body))
                self.end_headers()
                self.wfile.write(body)
            except Exception:
                import traceback
                err = traceback.format_exc()
                print(err)
                self.send_response(500)
                self.end_headers()
                self.wfile.write(f"<pre>{err}</pre>".encode())

        elif parsed.path == "/api/check_package":
            params = parse_qs(parsed.query)
            pkg = (params.get("id") or [""])[0]
            if not pkg:
                self.send_json({"package": pkg, "found": None})
                return
            found = check_play_store(pkg)
            self.send_json({"package": pkg, "found": found})

        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        # Strip query string from path (defensive)
        path = self.path.split("?")[0]
        print(f"  POST {path!r}  ({length} bytes)")

        try:
            data = json.loads(body)
        except Exception:
            self.send_json({"ok": False, "error": "Invalid JSON"}, 400)
            return

        try:
            if path == "/api/edit":
                original_pkg = data.get("originalPackageName") or data.get("packageName")
                if not original_pkg:
                    self.send_json({"ok": False, "error": "Missing originalPackageName"})
                    return
                ok, msg = update_entry(original_pkg, data)
                self.send_json({"ok": ok, "error": msg if not ok else None})

            elif path == "/api/add":
                ok, msg = add_entry(data)
                self.send_json({"ok": ok, "error": msg if not ok else None})

            elif path == "/api/delete":
                pkg = data.get("packageName")
                if not pkg:
                    self.send_json({"ok": False, "error": "Missing packageName"})
                    return
                ok, msg = delete_entry(pkg)
                self.send_json({"ok": ok, "error": msg if not ok else None})

            else:
                print(f"  Unknown POST path: {path!r}")
                self.send_json({"ok": False, "error": f"Unknown endpoint: {path}"}, 404)

        except Exception as e:
            import traceback
            print(traceback.format_exc())
            self.send_json({"ok": False, "error": f"Server error: {e}"})


# ── Main ───────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    if not os.path.exists(DATASET_PATH):
        print(f"ERROR: Dataset not found at: {DATASET_PATH}")
        sys.exit(1)

    port = 9003
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"Appdar Dataset Editor")
    print(f"  Dataset : {DATASET_PATH}")
    print(f"  URL     : http://localhost:{port}")
    print(f"  Ctrl+C to stop\n")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")
