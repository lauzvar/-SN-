"""Read-only comparison of original columns/rows before and after an upgrade.

Set PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE and optionally PSQL.
The baseline contains hashes and schema metadata, not raw business values.
Keep its output directory private. No SQL mutation is performed.
"""
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('mode', choices=['before', 'after'])
parser.add_argument('directory', type=Path)
args = parser.parse_args()
if os.environ.get('PGDATABASE') not in ('robot_sn_v2', 'robot_sn_v2_test'):
    raise SystemExit('Explicit v2 database required')
args.directory.mkdir(parents=True, exist_ok=True)
os.environ['PGCLIENTENCODING'] = 'UTF8'

def query(sql):
    value = subprocess.check_output(
        [os.environ.get('PSQL', 'psql'), '-X', '-w', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-c', sql],
        encoding='utf-8')
    return json.loads(value)

def ident(value):
    return '"' + value.replace('"', '""') + '"'

def hashes(table, columns):
    rows = query('select coalesce(json_agg(q),\'[]\'::json) from (select '
                 + ','.join(map(ident, columns)) + ' from public.' + ident(table) + ') q')
    return sorted(hashlib.sha256(json.dumps(r, ensure_ascii=False, sort_keys=True).encode()).hexdigest() for r in rows)

baseline_file = args.directory / 'original-data-fingerprints.json'
if args.mode == 'before':
    if baseline_file.exists():
        raise SystemExit('Baseline exists; choose a new private directory')
    metadata = query("select json_agg(q) from (select table_name,column_name from information_schema.columns where table_schema='public' and table_name <> 'flyway_schema_history' order by table_name,ordinal_position) q")
    tables = {}
    for row in metadata:
        tables.setdefault(row['table_name'], []).append(row['column_name'])
    baseline = {t: {'columns': c, 'rows': hashes(t, c)} for t, c in tables.items()}
    baseline_file.write_text(json.dumps(baseline, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Read-only baseline recorded:', len(baseline), 'tables')
else:
    baseline = json.loads(baseline_file.read_text(encoding='utf-8'))
    report = {}
    for table, entry in baseline.items():
        current = hashes(table, entry['columns'])
        before = collections.Counter(entry['rows'])
        after = collections.Counter(current)
        append_only = table in ('audit_log', 'login_log', 'field_def')
        valid = not (before - after) and (append_only or before == after)
        report[table] = {'pass': valid, 'beforeRows': len(entry['rows']), 'afterRows': len(current), 'allowAdditionalRows': append_only}
    (args.directory / 'data-preservation-result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    assert all(r['pass'] for r in report.values()), 'Mismatch; inspect private report before continuing'
    print('PASS: all original columns and rows preserved across', len(report), 'tables')
