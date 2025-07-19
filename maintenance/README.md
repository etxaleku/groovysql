# Various development maintenance components

1. install-git-hooks.sh

Installs a commit-msg hook in your local work tree. It currently enforces Conventional Commit message formatting.

After checking out a branch initially, run install-git-hooks.sh, it puts the git hooks in the maintenance folder in your hooks folder.

Ideally the hook would be installed as a server-side hook but unfortunately GitHub does not support server-side hooks.