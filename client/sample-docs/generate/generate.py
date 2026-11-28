"""Renders the fictional demo documents in content.py to PDF, DOCX, PPTX, XLSX, MD and TXT.

Usage (from client/sample-docs/generate):
    python -m venv .venv && .venv/Scripts/pip install -r requirements.txt
    .venv/Scripts/python generate.py            # writes into ../library
"""

from pathlib import Path

from docx import Document
from docx.shared import Pt, RGBColor
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter
from pptx import Presentation
from pptx.util import Pt as PPt
from reportlab.lib import colors
from reportlab.lib.pagesizes import LETTER
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import ListFlowable, ListItem, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

from content import DECKS, DISCLAIMER, TEXT_DOCUMENTS, WORKBOOKS

OUT = Path(__file__).resolve().parent.parent / "library"
BRAND = "0F5C6E"


def _blocks(section_body):
    """Yields ('p', text) | ('bullets', items) | ('table', header, rows)."""
    for block in section_body:
        if isinstance(block, str):
            yield ("p", block)
        else:
            yield block


# ---------------------------------------------------------------- PDF
def write_pdf(stem, title, meta, sections):
    styles = getSampleStyleSheet()
    cell = styles["BodyText"].clone("cell", fontSize=8.5, leading=10.5)
    story = [
        Paragraph(DISCLAIMER, styles["Italic"]),
        Spacer(1, 6),
        Paragraph(title, styles["Title"]),
        Paragraph(meta, styles["Italic"]),
        Spacer(1, 10),
    ]
    for heading, body in sections:
        story.append(Paragraph(heading, styles["Heading2"]))
        for block in _blocks(body):
            if block[0] == "p":
                story.append(Paragraph(block[1], styles["BodyText"]))
            elif block[0] == "bullets":
                story.append(ListFlowable(
                    [ListItem(Paragraph(b, styles["BodyText"])) for b in block[1]],
                    bulletType="bullet", leftIndent=14,
                ))
            else:
                _, header, rows = block
                data = [[Paragraph(f"<b>{h}</b>", cell) for h in header]]
                data += [[Paragraph(str(c), cell) for c in r] for r in rows]
                t = Table(data, repeatRows=1, colWidths=[(6.5 * inch) / len(header)] * len(header))
                t.setStyle(TableStyle([
                    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#E3F1F3")),
                    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#B8C4CC")),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ]))
                story.append(t)
            story.append(Spacer(1, 6))
    doc = SimpleDocTemplate(str(OUT / f"{stem}.pdf"), pagesize=LETTER, title=title, author="Harborline Health (fictional)")
    doc.build(story)


# ---------------------------------------------------------------- DOCX
def write_docx(stem, title, meta, sections):
    d = Document()
    d.core_properties.title = title
    d.core_properties.author = "Harborline Health (fictional)"
    note = d.add_paragraph(DISCLAIMER)
    note.runs[0].italic = True
    note.runs[0].font.size = Pt(9)
    d.add_heading(title, level=0)
    m = d.add_paragraph(meta)
    m.runs[0].font.color.rgb = RGBColor(0x5F, 0x6F, 0x7B)
    for heading, body in sections:
        d.add_heading(heading, level=1)
        for block in _blocks(body):
            if block[0] == "p":
                d.add_paragraph(block[1])
            elif block[0] == "bullets":
                for b in block[1]:
                    d.add_paragraph(b, style="List Bullet")
            else:
                _, header, rows = block
                t = d.add_table(rows=1, cols=len(header))
                t.style = "Light Grid Accent 1"
                for i, h in enumerate(header):
                    t.rows[0].cells[i].text = h
                for r in rows:
                    cells = t.add_row().cells
                    for i, c in enumerate(r):
                        cells[i].text = str(c)
    d.save(OUT / f"{stem}.docx")


# ---------------------------------------------------------------- MD / TXT
def _md(title, meta, sections):
    lines = [f"> {DISCLAIMER}", "", f"# {title}", "", f"_{meta}_", ""]
    for heading, body in sections:
        lines += [f"## {heading}", ""]
        for block in _blocks(body):
            if block[0] == "p":
                lines += [block[1], ""]
            elif block[0] == "bullets":
                lines += [f"- {b}" for b in block[1]] + [""]
            else:
                _, header, rows = block
                lines.append("| " + " | ".join(header) + " |")
                lines.append("|" + "---|" * len(header))
                lines += ["| " + " | ".join(str(c) for c in r) + " |" for r in rows]
                lines.append("")
    return "\n".join(lines)


def _txt(title, meta, sections):
    lines = [DISCLAIMER, "", title.upper(), meta, ""]
    for heading, body in sections:
        lines += [heading, "-" * len(heading)]
        for block in _blocks(body):
            if block[0] == "p":
                lines += [block[1], ""]
            elif block[0] == "bullets":
                lines += [f"  * {b}" for b in block[1]] + [""]
            else:
                _, header, rows = block
                lines += ["  " + " | ".join(header)] + ["  " + " | ".join(str(c) for c in r) for r in rows] + [""]
    return "\n".join(lines)


# ---------------------------------------------------------------- PPTX
def write_pptx(stem, title, subtitle, slides):
    p = Presentation()
    p.core_properties.title = title
    p.core_properties.author = "Harborline Health (fictional)"
    first = p.slides.add_slide(p.slide_layouts[0])
    first.shapes.title.text = title
    first.placeholders[1].text = f"{subtitle}\n{DISCLAIMER}"
    for s_title, bullets, notes in slides:
        slide = p.slides.add_slide(p.slide_layouts[1])
        slide.shapes.title.text = s_title
        body = slide.placeholders[1].text_frame
        body.text = bullets[0]
        for b in bullets[1:]:
            body.add_paragraph().text = b
        for para in body.paragraphs:
            for run in para.runs:
                run.font.size = PPt(20)
        if notes:
            slide.notes_slide.notes_text_frame.text = notes
    p.save(OUT / f"{stem}.pptx")


# ---------------------------------------------------------------- XLSX
def write_xlsx(stem, title, sheets):
    wb = Workbook()
    wb.remove(wb.active)
    for name, header, rows in sheets:
        ws = wb.create_sheet(name)
        ws.append([f"{title} - {DISCLAIMER}"])
        ws["A1"].font = Font(italic=True, color="5F6F7B")
        ws.append(header)
        for c in ws[2]:
            c.font = Font(bold=True, color="FFFFFF")
            c.fill = PatternFill("solid", fgColor=BRAND)
            c.alignment = Alignment(wrap_text=True, vertical="top")
        for r in rows:
            ws.append(r)
        for i, h in enumerate(header, start=1):
            width = max([len(str(h))] + [len(str(r[i - 1])) for r in rows if len(r) >= i])
            ws.column_dimensions[get_column_letter(i)].width = min(60, max(10, width + 2))
        ws.freeze_panes = "A3"
    wb.properties.title = title
    wb.properties.creator = "Harborline Health (fictional)"
    wb.save(OUT / f"{stem}.xlsx")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    writers = {"pdf": write_pdf, "docx": write_docx}
    for stem, fmt, title, meta, sections in TEXT_DOCUMENTS:
        if fmt in writers:
            writers[fmt](stem, title, meta, sections)
        elif fmt == "md":
            (OUT / f"{stem}.md").write_text(_md(title, meta, sections), encoding="utf-8")
        else:
            (OUT / f"{stem}.txt").write_text(_txt(title, meta, sections), encoding="utf-8")
    for stem, title, subtitle, slides in DECKS:
        write_pptx(stem, title, subtitle, slides)
    for stem, title, sheets in WORKBOOKS:
        write_xlsx(stem, title, sheets)
    files = sorted(OUT.iterdir())
    for f in files:
        print(f"{f.stat().st_size:>8,}  {f.name}")
    print(f"{len(files)} files in {OUT}")


if __name__ == "__main__":
    main()
