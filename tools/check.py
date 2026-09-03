import sys, zipfile, re
import xml.etree.ElementTree as ET

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
path = sys.argv[1]

z = zipfile.ZipFile(path)
names = z.namelist()
print("=== PACKAGE ===")
for required in ['[Content_Types].xml', 'word/document.xml', 'word/styles.xml',
                 'word/_rels/document.xml.rels', 'word/numbering.xml',
                 'word/header1.xml', 'word/footer1.xml']:
    print(("  OK   " if required in names else "  MISS ") + required)

bad = z.testzip()
print("  zip integrity:", "OK" if bad is None else "CORRUPT at " + str(bad))

# well-formedness of every xml part
print("=== XML WELL-FORMEDNESS ===")
errs = 0
for n in names:
    if n.endswith('.xml') or n.endswith('.rels'):
        try:
            ET.fromstring(z.read(n))
        except Exception as e:
            errs += 1
            print("  FAIL", n, e)
print("  parts checked:", sum(1 for n in names if n.endswith(('.xml', '.rels'))), "| errors:", errs)

root = ET.fromstring(z.read('word/document.xml'))

# headings in order
print("=== HEADINGS ===")
heads = []
for p in root.iter(W + 'p'):
    ppr = p.find(W + 'pPr')
    if ppr is None:
        continue
    st = ppr.find(W + 'pStyle')
    if st is None:
        continue
    val = st.get(W + 'val') or ''
    if val.lower().startswith('heading'):
        txt = ''.join(t.text or '' for t in p.iter(W + 't'))
        heads.append((val, txt))
for val, txt in heads:
    lvl = val[-1] if val[-1].isdigit() else '?'
    print("  " + "  " * (int(lvl) - 1 if lvl.isdigit() else 0) + "H" + lvl + " | " + txt)

# structural counts
tables = list(root.iter(W + 'tbl'))
paras = list(root.iter(W + 'p'))
print("=== COUNTS ===")
print("  paragraphs:", len(paras))
print("  tables:", len(tables))
print("  headings:", len(heads))

# table integrity: every row same cell count, every cell has a width
print("=== TABLE INTEGRITY ===")
problems = 0
for i, tbl in enumerate(tables):
    rows = tbl.findall(W + 'tr')
    counts = {len(r.findall(W + 'tc')) for r in rows}
    grid = tbl.find(W + 'tblGrid')
    ncols = len(grid.findall(W + 'gridCol')) if grid is not None else 0
    nowidth = 0
    for r in rows:
        for c in r.findall(W + 'tc'):
            tcpr = c.find(W + 'tcPr')
            if tcpr is None or tcpr.find(W + 'tcW') is None:
                nowidth += 1
    flag = ''
    if len(counts) != 1:
        flag += ' RAGGED' + str(sorted(counts)); problems += 1
    if ncols and counts and ncols != list(counts)[0]:
        flag += ' GRID_MISMATCH(grid=%d cells=%d)' % (ncols, list(counts)[0]); problems += 1
    if nowidth:
        flag += ' %d_CELLS_NO_WIDTH' % nowidth; problems += 1
    if flag:
        print("  table %d: rows=%d%s" % (i, len(rows), flag))
print("  tables with problems:", problems)

# full text checks
text = ''.join(t.text or '' for t in root.iter(W + 't'))
print("=== CONTENT CHECKS ===")
print("  total characters:", len(text))
required_sections = ['1. Background', '2. Product Vision', '3. Scope', '4. Target Users',
                     '5. Design Language', '6. Core Features', '7. Accessibility',
                     '8. Privacy', '9. Data Sources', '10. Architecture', '11. Data Flow',
                     '12. Non-Functional', '13. Error Handling', '14. Testing Approach',
                     '15. Success Metrics', '16. Assumptions', '17. Implementation Phases']
for s in required_sections:
    print(("  OK   " if s in text else "  MISS ") + s)

print("=== REQUIREMENT IDS ===")
for pre in ['FR-1.', 'FR-2.', 'FR-3.', 'FR-4.', 'FR-5.', 'FR-6.', 'FR-7.', 'FR-8.', 'FR-9.', 'FR-10.',
            'AC-1.', 'AC-2.', 'AC-3.', 'AC-4.', 'AC-5.', 'AC-6.', 'AC-7.', 'AC-8.', 'AC-9.', 'AC-10.', 'NFR-']:
    print("  %-6s %d" % (pre, len(re.findall(re.escape(pre), text))))

print("=== PROHIBITED CONTENT (timelines) ===")
hits = []
for pat in [r'\b\d+\s*(?:day|days|week|weeks|month|months)\b', r'\bSep(?:tember)?\s+\d', r'\bV0\b', r'\bMVP\b',
            r'\bday\s+\d', r'\bsprint\b', r'\bdeadline\b', r'\btimeline\b']:
    for m in re.finditer(pat, text, re.I):
        hits.append((pat, text[max(0, m.start() - 45):m.end() + 45]))
if hits:
    for p, ctx in hits:
        print("  HIT [%s]: ...%s..." % (p, ctx.replace('\n', ' ')))
else:
    print("  none found - clean")

print("=== LITERAL BULLET CHARS (should be 0) ===")
print("  bullet glyphs in text:", text.count('•'))
