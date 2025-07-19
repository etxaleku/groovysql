To generate the project's README.md to PDF format -

1. install pandoc and related packages

	apt install pandoc texlive-base texlive-latex-extra texlive-extra-utils texlive-fonts-extra

2. copy md2pdf to a directory in the execution path ($PATH)

	cp ./src/templates/md2pdf  ~/bin/md2pdf

3. copy the pandoc templates (2 files) to your local pandoc templates folder, creating the folder if necessary

	cp ./src/templates/eisvogel.{beamer,latex} to ~/.local/share/pandoc/templates/

4. executing "md2pdf README.md" should produce README.pdf

