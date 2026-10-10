---
title: "md2pdf"
author: "Version 2.0"
date: "Oct 2026"
geometry: margin=1in
...

# md2pdf

Convert Markdown into PDF, self-contained HTML, or plain text. PDF output uses Pandoc to generate HTML and Playwright/Chromium to render it.

Supports multiple Markdown inputs, optional YAML metadata, independent font sizes, syntax-highlighted code blocks, and metadata-driven repeating PDF headers and footers.

## Usage

```text
md2pdf [OPTIONS] FILE...
```

Place options before input files. Markdown files are merged in their command-line order; `.yaml` and `.yml` files are treated as metadata.

| Option | Description | Default |
|---|---|---|
| `-B SIZE` | Body font size | `normalsize` |
| `-C SIZE` | Code-block font size | `normalsize` |
| `-T SIZE` | Table font size | `normalsize` |
| `-f MODE` | Output format: `pdf`, `html`, or `txt` | `pdf` |
| `-o FILE` | Output filename | First Markdown input with the output extension |
| `-h` | Show help | |

Long options are also available: `--body-size`, `--code-size`, `--table-size`, `--format`, `--output`, and `--help`.

Font-size options apply to PDF and HTML, not plain-text output. Use `--` to end option parsing if an input filename begins with `-`.

## Examples

```bash
# Generate README.pdf
md2pdf README.md

# Apply metadata from a separate YAML file
md2pdf README.yaml README.md

# Set body, code, and table sizes independently
md2pdf -B Large -C small -T tiny README.yaml README.md

# Merge multiple Markdown files into one PDF
md2pdf -o manual.pdf metadata.yaml intro.md reference.md examples.md

# Generate self-contained HTML
md2pdf -f html -B large README.yaml README.md

# Generate plain text
md2pdf -f txt README.md

# Show help
md2pdf -h
```

Without `-o`, output is written beside the first Markdown input. No page breaks are automatically inserted between input files.

## Font Sizes

Size names are case-sensitive:

```text
tiny
scriptsize
footnotesize
small
normalsize
large
Large
LARGE
huge
Huge
```

The script applies a font scale of `0.85` to the body, code, table, and existing heading sizes.

Changing `-B` affects ordinary paragraphs, lists, blockquotes, and inline code that inherits the body size. Code blocks and tables retain their independent sizes; headings and repeating headers/footers are not resized by `-B`.

## Metadata

Metadata can be supplied in a separate YAML file or in a YAML block at the beginning of a Markdown file.

```yaml
---
title: "GroovySQL"
author: "Version 3.0.1"
date: "Oct 2024"
subject: "SQL Client"
keywords: [Groovy SQL]
lang: "en"
table-use-row-colors: true
geometry: margin=1in
linkcolor: blue
...
```

| Setting | Effect |
|---|---|
| `title` | PDF title metadata and repeating header text |
| `author` | PDF author metadata and repeating footer text |
| `date` | Repeating header text |
| `subject` | PDF subject metadata |
| `keywords` | PDF keywords metadata |
| `lang` | HTML document language |
| `table-use-row-colors` | Alternating table-body row shading |
| `geometry` | Page margins and PDF header/footer alignment |
| `linkcolor` | Link text color |

Legacy `listings-*` settings are not used.

### Conditional PDF Headers and Footers

When any of `title`, `pagetitle`, `date`, or `author` is nonempty, PDF output includes:

- Header: title on the left, date on the right, rule underneath.
- Footer: author on the left, page number on the right, rule above.

The separate metadata title block is removed from the PDF body.

When all four values are absent or blank, PDF output has no repeating headers, footers, rules, or page numbers. Other metadata and styling settings still apply.

HTML output retains its metadata title block rather than using repeating PDF decorations.

### Margins and Link Colors

Supported margin keys are `margin`, `top`, `bottom`, `left`, and `right`:

```yaml
geometry: margin=1in
```

```yaml
geometry: "top=0.75in,bottom=0.75in,left=1in,right=1in"
```

Lengths accept `in`, `cm`, `mm`, `pt`, or `px`, plus unitless `0`. Repeating PDF headers and footers require top and bottom margins of at least 0.5 inches.

Defaults are Letter paper, 0.55-inch margins, and link color `#245caa`. Link colors accept CSS color names or `#RGB` / `#RRGGBB`.

## Code Blocks

Use fenced code blocks with a language identifier for syntax highlighting:

````markdown
```sql
SELECT customer_id, customer_name
FROM customers
WHERE active = true;
```
````

Code blocks preserve indentation and wrap long lines. Their font size is controlled by `-C`.

## Installation

Prerequisites: `uv` and `pandoc` available on `PATH`.

```bash
V="$HOME/.local/share/venvs/md2pdf"

uv venv "$V"
uv pip install --python "$V/bin/python" playwright pypdf
"$V/bin/python" -m playwright install --with-deps chromium
```

Save the script somewhere on `PATH`, such as `~/bin/md2pdf`, and make it executable:

```bash
chmod +x ~/bin/md2pdf
```

To use a different Python environment:

```bash
export MD2PDF_PYTHON="/path/to/venv/bin/python"
```

## Notes

- `-f` selects the output format; the extension supplied to `-o` does not.
- PDF generation requires Playwright, Chromium, and `pypdf`.
- Relative image resources are searched in the Markdown input directories in command-line order. Avoid ambiguous image paths when merging files from different directories.
- The old positional size arguments are no longer supported; use `-B`, `-C`, and `-T`.
