# Minimal makefile for Sphinx documentation

SPHINXOPTS    ?=
SPHINXBUILD   ?= sphinx-build
SOURCEDIR     = .
BUILDDIR      = _build
PAGESDIR      = ../docs

help:
	@$(SPHINXBUILD) -M help "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

# Build into ../docs/ for GitHub Pages "Deploy from branch" serving
publish:
	rm -rf "$(PAGESDIR)"
	$(SPHINXBUILD) -b html -d "$(BUILDDIR)/doctrees" "$(SOURCEDIR)" "$(PAGESDIR)" $(SPHINXOPTS) $(O)
	rm -f "$(PAGESDIR)/.buildinfo"
	touch "$(PAGESDIR)/.nojekyll"

.PHONY: help publish Makefile

%: Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)
