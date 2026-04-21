# Configuration file for the Sphinx documentation builder.

project = "MemoizeLib"
copyright = "2026, NYU Abu Dhabi"
author = "Rui Rua"
release = "0.1.0"

extensions = [
    "myst_parser",
    "sphinx_copybutton",
    "sphinx.ext.autosectionlabel",
]

# MyST (Markdown) settings
myst_enable_extensions = [
    "colon_fence",
    "deflist",
    "fieldlist",
    "tasklist",
]
myst_heading_anchors = 3

# Source settings
source_suffix = {
    ".rst": "restructuredtext",
    ".md": "markdown",
}

templates_path = ["_templates"]
exclude_patterns = ["_build", ".venv", "Thumbs.db", ".DS_Store"]

# HTML output
html_baseurl = "https://sanadlab.github.io/MORAl/"
html_theme = "sphinx_rtd_theme"
html_static_path = ["_static"]
html_theme_options = {
    "navigation_depth": 4,
    "collapse_navigation": False,
    "titles_only": False,
    "logo_only": False,
}
html_title = "MemoizeLib Documentation"
html_short_title = "MemoizeLib"

# Copy button settings
copybutton_prompt_text = r">>> |\.\.\. |\$ "
copybutton_prompt_is_regexp = True

# Auto section label settings
autosectionlabel_prefix_document = True
