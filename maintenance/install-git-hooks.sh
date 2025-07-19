#!/bin/bash

repo_root=$(git rev-parse --show-toplevel)
hook_dir=$repo_root/.git/hooks
hook_src=$repo_root/maintenance

function errexit { echo "$*"; exit 255; }

function install_hook {
    srce=$1
    dest=$(basename $srce .hook)
    cp $srce $hook_dir/$dest
    echo "installed $hook_dir/$dest"
}

[[ ! $repo_root ]] && errexit "no repo detected"

for hook in $hook_src/*.hook
do install_hook $hook
done

