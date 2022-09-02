#!/usr/bin/env bash
set -o errexit -o pipefail -o nounset
command -v brew >/dev/null 2>&1 || { warn "Homebrew must be installed -> https://brew.sh/"; exit 1; }

export HOMEBREW_NO_ANALYTICS=1
export HOMEBREW_NO_AUTO_UPDATE=1

# Install, or upgrade if already installed
brew_up() {
    if brew ls --versions "${1}" >/dev/null; then
        brew upgrade "${1}"
    else
        brew install "${1}"
    fi
}

echo "Caching Password..."
sudo -K
sudo true

echo "Updating Homebrew..."
brew update

# Kubernetes Tools
brew_up helm
brew_up kubectl
brew_up minikube
brew_up derailed/k9s/k9s

# Manage Multiple JDKs
brew_up jenv

# Docker Desktop for Mac
brew install --cask docker

echo
echo "Recommended zsh setup for auto-complete and aliases:"
echo "  1. Install OhMyZSH (https://ohmyz.sh/)"
echo "  2. Enable plugins git, helm, docker, kubectl (https://github.com/ohmyzsh/ohmyzsh/wiki/Plugins)"
